package edu.utdallas.davisbase.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static edu.utdallas.davisbase.RowIdUtils.ROWID_MAX_VALUE;
import static edu.utdallas.davisbase.storage.DataUtils.convertToBytes;
import static edu.utdallas.davisbase.storage.Page.FILE_OFFSET_OF_METADATA_CURRENT_ROWID;
import static edu.utdallas.davisbase.storage.Page.FILE_OFFSET_OF_METADATA_ROOT_PAGENO;  // spell-checker:ignore pageno
import static edu.utdallas.davisbase.storage.Page.PAGE_SIZE;
import static edu.utdallas.davisbase.storage.Page.convertPageNoToFileOffset;
import static edu.utdallas.davisbase.storage.Page.getNumberOfCells;
import static edu.utdallas.davisbase.storage.Page.getPageOffsetOfCell;
import static edu.utdallas.davisbase.storage.Page.getParent;
import static edu.utdallas.davisbase.storage.Page.getRightSiblingOfLeafPage;
import static edu.utdallas.davisbase.storage.Page.splitLeafPage;
import static edu.utdallas.davisbase.storage.Page.updateParentwithLeafPageMaxRowID;
import static edu.utdallas.davisbase.storage.TablePageType.INTERIOR;
import static edu.utdallas.davisbase.storage.TablePageType.LEAF;
import static java.lang.String.format;
import static java.util.Arrays.stream;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import edu.utdallas.davisbase.DataType;
import edu.utdallas.davisbase.YearUtils;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A DavisBase "Table" file.
 *
 * A {@link TableFile} object is the lowest-level <i>structured</i> interface to a DavisBase "Table"
 * file. It is intended to wrap a live (albeit closeable) file connection, and thereby abstract
 * access and manipulation of the underlying binary file as if it were <b>a collection of
 * records</b>.
 *
 * A {@link TableFile} object functions similarly to a mutable forward-only cursor.
 *
 * @apiNote Unlike a conceptual "table", a {@link TableFile} object does not have a schema. This is
 *          because the {@link TableFile} class is intended to abstract only the binary structures
 *          and algorithms (e.g. paging, record de-/serialization, b-tree balancing, etc.), and be
 *          used by other higher-level classes to effect a schematic table and complex SQL
 *          operations. As such, <b>schematically correct reading and writing of records is the
 *          responsibility of the code using a {@link TableFile} object</b>.
 */
public class TableFile implements Closeable {

  private static final int   NULL_PAGE_NO    = -1;
  private static final short NULL_CELL_INDEX = -1;

  protected final RandomAccessFile file;

  private int   currentLeafPageNo    = NULL_PAGE_NO;
  private short currentLeafCellIndex = NULL_CELL_INDEX;
  private boolean isCurrentRowDeleted = false;

  public TableFile(RandomAccessFile file) {
    checkNotNull(file);
    checkArgument(file.getChannel().isOpen());
    this.file = file;

    try {

      if (file.length() < 512) {
        Page.addTableMetaDataPage(file);
      }
    } catch (Exception e) {

    }
  }

  @Override
  public void close() throws IOException {
    file.close();
  }

  //region Append

  public void appendRow(TableRowBuilder tableRowBuilder) throws IOException {

    final int newRowId = getNextRowId();
    incrementMetaDataCurrentRowId();

    tableRowBuilder.prependRowId(newRowId);
    final TableLeafCellBuffer newLeafCellBuffer = tableRowBuilder.toLeafCellBuffer();

    this.appendRow(newRowId, newLeafCellBuffer);
  }

  private void appendRow(int newRowId, TableLeafCellBuffer newLeafCellBuffer) throws IOException {
    assert newRowId == Ints.fromByteArray(newLeafCellBuffer.get((byte) 0));

    //region Find rightmost leaf page.

    int  pageNo = this.getMetaDataRootPageNo();
    long pageFileOffset = convertPageNoToFileOffset(pageNo);
    byte pageTypeCode;

    // TODO Refactor the following two lines to a new method Page+getRightPageNo(file, pageNo)
    file.seek(pageFileOffset + 6);
    int rightPageNo = file.readInt();

    while (rightPageNo != -1) {  // FIXME magic number
      pageNo = rightPageNo;
      pageFileOffset = convertPageNoToFileOffset(pageNo);

      file.seek(pageFileOffset + 6);
      rightPageNo = file.readInt();
    }

    file.seek(pageFileOffset);
    pageTypeCode = file.readByte();

    //endregion

    //region Check ahead for overflow, and preemptively "split" page if so.

    final byte[] newLeafCellData = newLeafCellBuffer.toBytes();
    final boolean overflowFlag = wouldPageOverflow(newLeafCellData.length, pageNo);

    if (overflowFlag && pageTypeCode == 0x0D) {
      pageNo = splitLeafPage(file, pageNo, newRowId);  // VERIFY Page+splitLeafPage(file, pageNo)

      pageFileOffset = convertPageNoToFileOffset(pageNo);

      file.seek(pageFileOffset);
      pageTypeCode = file.readByte();
    }

    //endregion

    //region Calculate new "cell content area" page offset.

    file.seek(pageFileOffset + 3);  // FIXME magic number
    short oldContentPageOffset = file.readShort();
    assert oldContentPageOffset >= 0;

    if (oldContentPageOffset <= 0) {
      oldContentPageOffset = Shorts.checkedCast(PAGE_SIZE);
    }

    final short newContentPageOffset = Shorts.checkedCast(oldContentPageOffset - newLeafCellData.length);

    //endregion

    //region Insert cell in content area of page.

    file.seek(pageFileOffset + newContentPageOffset);
    file.write(newLeafCellData);

    //endregion

    //region Increment cell count in page.

    file.seek(pageFileOffset + 1);  // FIXME magic number
    final short oldPageCellCount = file.readShort();

    final short newPageCellCount = Shorts.checkedCast(oldPageCellCount + 1);

    file.seek(pageFileOffset + 1);  // FIXME magic number
    file.writeShort(newPageCellCount);

    //endregion

    //region Update "cell content area" page offset in page.

    file.seek(pageFileOffset + 3);  // FIXME magic number
    file.writeShort(newContentPageOffset);

    //endregion

    //region Append the new cell's page offset to the array of such in page.

    file.seek(pageFileOffset + 16 + 2 * (newPageCellCount - 1));  // FIXME magic numbers
    file.writeShort(newContentPageOffset);

    /* NOTE newContentPageOffset
     *
     * The cell was inserted beginning at newContentPageOffset, and so newContentPageOffset is
     * exactly the newCellPageOffset for this cell.
     */

    //endregion

    // Update maximum row ID of the respective page in parent.
    int parentPageNo = getParent(file, pageNo);
    if (parentPageNo != -1) {
      updateParentwithLeafPageMaxRowID(file, pageNo, parentPageNo, newRowId);
    }
  }

  // VERIFY -wouldPageOverflow(newCellDataSize, pageNo): boolean
  private boolean wouldPageOverflow(int newCellDataSize, int pageNo) {
    try {
      file.seek((pageNo - 1) * PAGE_SIZE);
      byte pageType = file.readByte();
      if (pageType == 0x0D) {

        this.file.seek((pageNo - 1) * PAGE_SIZE + 1);
        short noOfRecords = this.file.readShort();

        this.file.seek((pageNo - 1) * PAGE_SIZE + 3);
        short startofCellConcent = file.readShort();
        if (startofCellConcent == 0) {
          startofCellConcent = (short) (PAGE_SIZE);
        }
        short arryLastEntry = (short) (16 + (noOfRecords * 2));
        if ((startofCellConcent - arryLastEntry - 1) < newCellDataSize) {
          return true;
        }
      } else {// TODO Update in Part 2 for the remainig page types
        return false;
      }
    } catch (Exception e) {

    }
    return false;
  }

  //endregion

  //region Go To Next

  public boolean goToNextRow() throws IOException {
    assert this.hasCurrentLeafPageNo() == this.hasCurrentLeafCellIndex();

    if (this.hasCurrentLeafPageNo()) {
      if (this.isCurrentRowDeleted) {
        this.isCurrentRowDeleted = false;
      }
      else {
        this.currentLeafCellIndex += 1;
      }
    }
    else { // Very first time goToNextRow() has been called for this TableFile instance.
      this.currentLeafPageNo = getLeftmostLeafPageNo();
      this.currentLeafCellIndex = 0;
    }

    short countCells = getNumberOfCells(file, this.currentLeafPageNo);
    if (!(this.currentLeafCellIndex < countCells)) {

      final int rightSiblingPageNo = getRightSiblingOfLeafPage(file, this.currentLeafPageNo);
      if (!Page.exists(file, rightSiblingPageNo)) {
        return false;
      }

      this.currentLeafPageNo = rightSiblingPageNo;
      this.currentLeafCellIndex = 0;

      countCells = getNumberOfCells(file, this.currentLeafPageNo);
      if (!(this.currentLeafCellIndex < countCells)) {
        return false;
      }
    }

    return true;
  }

  private boolean valueOfCurrentRowColumnIsNull(int columnIndex) throws IOException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        format("columnIndex (%d) is not in range [0, %d)",
            columnIndex,
            Byte.MAX_VALUE));
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    final long fileOffsetOfPage = Page.convertPageNoToFileOffset(this.currentLeafPageNo);
    final short pageOffsetOfCell = Page.getPageOffsetOfCell(file, this.currentLeafPageNo, this.currentLeafCellIndex);
    final long fileOffsetOfPageCell = fileOffsetOfPage + pageOffsetOfCell;

    final int valueSizeInBytes = Page.getSizeOfTableLeafCellColumn(file, fileOffsetOfPageCell, columnIndex);
    return valueSizeInBytes <= 0;
  }

  private void goToCurrentLeafPageCellColumnValue(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        format("columnIndex (%d) is not in range [0, %d)",
            columnIndex,
            Byte.MAX_VALUE));
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    final long fileOffsetOfPage = Page.convertPageNoToFileOffset(this.currentLeafPageNo);
    final short pageOffsetOfCell = Page.getPageOffsetOfCell(file, this.currentLeafPageNo, this.currentLeafCellIndex);
    final long fileOffsetOfPageCell = fileOffsetOfPage + pageOffsetOfCell;

    final byte columnCount = Page.getNumberOfColumnsOfTableLeafCell(file, fileOffsetOfPageCell);
    if (!(columnIndex < columnCount)) {
      throw new StorageException(
          format("columnIndex (%d) is not less than columnCount (%d)",
              columnIndex,
              columnCount));
    }

    int cellOffset = 1 + columnCount; // 1 to account for the initial byte of column count.
    for (int i = 0; i < columnIndex; i++) {
      cellOffset += Page.getSizeOfTableLeafCellColumn(file, fileOffsetOfPageCell, i);
    }

    final long fileOffsetOfPageCellColumnValue = fileOffsetOfPageCell + cellOffset;
    file.seek(fileOffsetOfPageCellColumnValue);
  }

  //endregion

  //region Read

  public @Nullable Byte readTinyInt(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return file.readByte();
  }

  public @Nullable Short readSmallInt(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return file.readShort();
  }

  public @Nullable Integer readInt(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return file.readInt();
  }

  public @Nullable Long readBigInt(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return file.readLong();
  }

  public @Nullable Float readFloat(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return file.readFloat();
  }

  public @Nullable Double readDouble(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return file.readDouble();
  }

  public @Nullable Year readYear(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return Year.of(file.readByte() + YearUtils.YEAR_OFFSET);
  }

  public @Nullable LocalTime readTime(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return LocalTime.ofSecondOfDay(file.readInt());
  }

  public @Nullable LocalDateTime readDateTime(int columnIndex)
      throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return LocalDateTime.ofEpochSecond(file.readLong(), 0, ZoneOffset.UTC);
  }

  public @Nullable LocalDate readDate(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    if (valueOfCurrentRowColumnIsNull(columnIndex)) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    return LocalDate.ofEpochDay(file.readLong());
  }

  public @Nullable String readText(int columnIndex) throws IOException, StorageException {
    checkArgument(0 <= columnIndex && columnIndex < Byte.MAX_VALUE,
        "columnIndex (%d) is not in range [0, %d)", columnIndex, Byte.MAX_VALUE);
    checkState(this.hasCurrentRow(),
        "tableFile is not pointing to a current row from which to read");

    final long fileOffsetOfPage = Page.convertPageNoToFileOffset(this.currentLeafPageNo);
    final short pageOffsetOfCell =
        Page.getPageOffsetOfCell(file, this.currentLeafPageNo, this.currentLeafCellIndex);
    final long fileOffsetOfPageCell = fileOffsetOfPage + pageOffsetOfCell;

    final int valueSizeInBytes =
        Page.getSizeOfTableLeafCellColumn(file, fileOffsetOfPageCell, columnIndex);
    if (valueSizeInBytes <= 0) {
      return null;
    }

    goToCurrentLeafPageCellColumnValue(columnIndex);
    final byte[] bytes = new byte[valueSizeInBytes];
    file.read(bytes);
    return new String(bytes);
  }

  //endregion

  //region Remove

  public void removeRow() throws IOException {
    checkState(this.hasCurrentRow(), "tableFile is not pointing to a current row from which to read");

    long fileOffsetOfPage = Page.convertPageNoToFileOffset(this.currentLeafPageNo);
    long cellOffsetOffset = 0x0010;
    long currentCellOffset = fileOffsetOfPage + cellOffsetOffset;
    long cellCountOffset = fileOffsetOfPage + 1;
    int maxRowId = getMaxRowId();
    int currentRowId = getcurrentRowId();

    file.seek(cellCountOffset);
    short cellCount = file.readShort();

    if (cellCount == 1 && currentRowId == maxRowId) {  // leaf = rootpage, only 1 record in the table
      removeRow(currentCellOffset, cellCount);
      file.seek(cellCountOffset);
      file.writeShort(cellCount - 1);
      this.isCurrentRowDeleted = true;
    }
    else if (cellCount == 1) {
      removeRow(currentCellOffset, cellCount);
      file.seek(cellCountOffset);
      file.writeShort(cellCount - 1);
      removeLeafFromParent();
      this.isCurrentRowDeleted = true;
    }
    else if (currentRowId + 1 == cellCount) {
      removeRow(currentCellOffset, cellCount);
      file.seek(cellCountOffset);
      file.writeShort(cellCount - 1);
      updateMaxRowIdInParent();
      this.isCurrentRowDeleted = true;
    }
    else {
      removeRow(currentCellOffset, cellCount);
      file.seek(cellCountOffset);
      file.writeShort(cellCount - 1);
      this.isCurrentRowDeleted = true;
    }

    file.seek(cellCountOffset);
    file.writeShort(cellCount - 1);
  }

  private void removeRow(long currentCellOffset, long cellCount) throws IOException {
    file.seek(currentCellOffset);
    ArrayList<Short> offsetLocationList = new ArrayList<>();
    for (int i = 0; i < cellCount; i++) {
      short offsetLocation = file.readShort();
      offsetLocationList.add(offsetLocation);
    }
    offsetLocationList.remove(this.currentLeafCellIndex);
    offsetLocationList.add((short) 0x00);
    file.seek(currentCellOffset);

    for (short val : offsetLocationList) {
      // System.out.println("this.file.getFilePointer() ::: " + this.file.getFilePointer());
      file.writeShort(val);
    }
  }

  private void removeRow(long currentCellOffset, long cellCount, int cellIndexId) throws IOException {
    file.seek(currentCellOffset);
    ArrayList<Short> offsetLocationList = new ArrayList<>();
    for (int i = 0; i < cellCount; i++) {
      short offsetLocation = file.readShort();
      offsetLocationList.add(offsetLocation);
    }
    offsetLocationList.remove(cellIndexId);
    offsetLocationList.add((short) 0x00);
    file.seek(currentCellOffset);

    for (short val : offsetLocationList) {
      // System.out.println("this.file.getFilePointer() ::: " + this.file.getFilePointer());
      file.writeShort(val);
    }
  }

  public int getParentPageNo() throws IOException {
    long fileOffsetOfPage = Page.convertPageNoToFileOffset(this.currentLeafPageNo);
    long cellOffsetOffset = 0x0A;
    long currentParentOffset = fileOffsetOfPage + cellOffsetOffset;
    // System.out.println("file.getFilePointer() ::: " + file.getFilePointer());

    file.seek(currentParentOffset);
    // System.out.println("file.getFilePointer() ::: " + file.getFilePointer());
    return file.readInt();
  }

  public void updateMaxRowIdInParent() throws IOException {
    int parentPageNo = getParentPageNo();
    if (parentPageNo == NULL_PAGE_NO) {
      return;
    }

    long cellOffsetOffset = 0x0010;
    long fileOffsetOfPage = (parentPageNo - 1) * 512;
    long cellCountOffset = fileOffsetOfPage + 1;

    long currentCellLocationOffset = fileOffsetOfPage + cellOffsetOffset;

    file.seek(cellCountOffset);
    short cellCount = file.readShort();

    ArrayList<Short> offsetLocationList = new ArrayList<>();
    for (int i = 0; i < cellCount; i++) {
      currentCellLocationOffset += 0x02 * i;
      file.seek(currentCellLocationOffset);
      short offsetLocation = file.readShort();
      offsetLocationList.add(offsetLocation);
      // System.out.println("offsetLocation ::: " + offsetLocation);
    }


    for (short val : offsetLocationList) {
      long recordOffset = fileOffsetOfPage + val;
      file.seek(recordOffset);
      int pageId = file.readInt();

      // System.out.println("pageId ::: " + pageId);

      if (pageId == this.currentLeafPageNo) {
        long maxRowIdOffset = recordOffset + 0x04;
        file.seek(maxRowIdOffset);
        int maxRowId = file.readInt();

        file.seek(maxRowIdOffset);
        file.writeInt(maxRowId - 1);

        // System.out.println("maxRowId ::: " + maxRowId);
      }
    }

  }


  public void removeLeafFromParent() throws IOException {
    int parentPageNo = getParentPageNo();
    long cellOffsetOffset = 0x0010;
    long fileOffsetOfPage = (parentPageNo - 1) * 512;
    long cellCountOffset = fileOffsetOfPage + 1;

    long currentCellLocationOffset = fileOffsetOfPage + cellOffsetOffset;

    file.seek(cellCountOffset);
    short cellCount = file.readShort();

    ArrayList<Short> offsetLocationList = new ArrayList<>();
    for (int i = 0; i < cellCount; i++) {
      currentCellLocationOffset += 0x02 * i;
      file.seek(currentCellLocationOffset);
      short offsetLocation = file.readShort();
      offsetLocationList.add(offsetLocation);
      // System.out.println("offsetLocation ::: " + offsetLocation);
    }

    currentCellLocationOffset = fileOffsetOfPage + cellOffsetOffset;

    int cellIndexId = 0;
    for (short val : offsetLocationList) {
      long recordOffset = fileOffsetOfPage + val;
      file.seek(recordOffset);
      int pageId = file.readInt();

      // System.out.println("pageId ::: " + pageId);

      if (pageId == this.currentLeafPageNo) {

        // removeRow(long currentCellOffset, long cellCount, int cellIndexId)
        removeRow(currentCellLocationOffset, cellCount, cellIndexId);

        /*
         * long maxRowIdOffset = recordOffset + 0x04; file.seek(maxRowIdOffset); int maxRowId =
         * file.readInt();
         *
         * file.seek(maxRowIdOffset); file.writeInt(maxRowId - 1);
         */
        // System.out.println("maxRowId ::: " + maxRowId);
      }
      cellIndexId++;
    }

  }

  private int getMaxRowId() throws IOException {
    file.seek(0x01);
    return file.readInt();
  }

  private int getcurrentRowId() throws IOException {
    long fileOffsetOfPage = Page.convertPageNoToFileOffset(this.currentLeafPageNo);
    long cellOffsetOffset = 0x0010;
    long currentCellOffset = fileOffsetOfPage + cellOffsetOffset + 0x02 * this.currentLeafCellIndex;
    file.seek(currentCellOffset);
    short currentCellLocation = file.readShort();
    long currentCellLocationOffset = fileOffsetOfPage + currentCellLocation;
    file.seek(currentCellLocationOffset);
    byte columnCount = file.readByte();
    long currentRowLocationOffset =
        fileOffsetOfPage + currentCellLocation + 0x01 * (1 + columnCount);

    file.seek(currentRowLocationOffset);
    int rowId = file.readInt();
    return rowId;
  }

  //endregion

  //region Write

  /**
   * Overwrites zero-or-more pre-existing (but nullable) columns of the current row.
   * <p>
   * May delete and re-append the row with a new {@code rowid}, but does not modify this
   * {@code TableFile}'s current row pointer. However, if the current row is deleted and
   * re-appended, then this method not be called again until the current row pointer is updated
   * (e.g. by invoking {@link #goToNextRow()}). When the current row is deleted and re-appended is
   * implementation-defined.
   *
   * @param row the set of zero-or-more {@code columnIndex}-keyed nullable values with which to
   *        update the current row
   * @throws IOException
   * @see TableRowWrite
   */
  public void writeRow(TableRowWrite rowWrite) throws IOException {
    checkNotNull(rowWrite, "rowWrite");
    checkState(this.hasCurrentRow(),
        format("This %s{currentLeafPageNo=%d, currentLeafCellIndex=%d, fileLength=%d} is not currently pointing to any row to which to write.",
            this.getClass().getName(),
            this.currentLeafPageNo,
            this.currentLeafCellIndex,
            this.file.length()));

    //region Locate and read "old" cell.

    final short pageOffsetOfCell = getPageOffsetOfCell(this.file, this.currentLeafPageNo, this.currentLeafCellIndex);
    final long fileOffsetOfCell = convertPageNoToFileOffset(this.currentLeafPageNo) + pageOffsetOfCell;
    file.seek(fileOffsetOfCell);
    final TableLeafCellBuffer cellBuffer = TableLeafCellBuffer.fromBytes(file);

    final int oldCellLength = cellBuffer.length();

    //endregion

    //region Create "new" updated cell in-memory.

    // Apply the column-wise updates to the "old" cell data in the cell buffer.
    for (final Map.Entry<Byte, @Nullable Object> column : rowWrite) {
      assert 1 <= column.getKey() && column.getKey() < cellBuffer.size();  // Cannot be zero because that is built-in reserved for rowId, which is not user-writable.
      assert column.getValue() == null || stream(DataType.values()).allMatch(dt -> dt.getJavaClass().isInstance(column.getValue()));

      final byte columnIndex = column.getKey();
      final byte[] data = convertToBytes(column.getValue());

      cellBuffer.set(columnIndex, data);
    }
    // The cell buffer now contains the "new" cell data.

    final int newCellLength = cellBuffer.length();

    //endregion

    // CASE 1/2 : The new cell is *not* larger, so we can just overwrite the old cell in-place.
    if (newCellLength <= oldCellLength) {
      final byte[] newCellData = cellBuffer.toBytes();
      file.seek(fileOffsetOfCell);
      file.write(newCellData);
    }

    // CASE 2/2 : The new cell *is* larger, so we have to do some reorganizing to make it fit.
    else {
      /* IMPORTANT
       *
       * The professor has verbally stated during lecture that if an UPDATE command causes a cell to
       * increase in size (of bytes), we are **not** required to either of the following:
       *
       * - Defragment the cell content area _within_ the affected page in an attempt to re-fit the
       *   now-larger cell
       * - Balance cells _across_ sibling pages (using rotations and/or splits) in order to make
       *   room for the now-large cell on one of those pages
       *
       * Rather, he both permitted as well as actively suggested simply deleting the "old" row and
       * inserting the "new" (updated) row. The professor did verbally acknowledge that this would
       * necessarily (albeit not preferably) alter the rowId of the updated row. However, he stated
       * that he would not take off grade points for that considering the extensive project scope
       * and compressed course timeline of the summer.
       *
       * In light of the professor's aforementioned statements, this second case (i.e. the updated
       * row _is_ larger), is simply implemented using the one catch-all strategy of naively
       * deleting and re-inserting the updated row.
       *
       * AUTHOR Travis C. LaGrone
       * SINCE 2019-08-01
       */

      this.removeRow();

      final int newRowId = this.getNextRowId();
      this.incrementMetaDataCurrentRowId();

      final byte[] newRowIdData = Ints.toByteArray(newRowId);
      cellBuffer.set((byte) 0, newRowIdData);

      this.appendRow(newRowId, cellBuffer);
    }
  }

  //endregion

  public int getCurrentMaxRowId() throws IOException {
    file.seek(FILE_OFFSET_OF_METADATA_CURRENT_ROWID);
    final int currentMaxRowId = file.readInt();
    return currentMaxRowId;
  }

  public int getMaxtRowIdInTable(RandomAccessFile file) throws IOException {
    try {
      file.seek(0x01);

      int rowId = file.readInt();
      return (rowId);

    } catch (Exception e) {
    }
    return -1;
  }

  //region Private Helper Methods

  private int getNextRowId() throws IOException {
    final int currentMaxRowId = this.getCurrentMaxRowId();
    checkState(currentMaxRowId < ROWID_MAX_VALUE,
        format("Cannot get the next allocatable ROWID value because the maximum ROWID of %d has already been allocated for this table.",
            ROWID_MAX_VALUE));
    final int nextRowId = currentMaxRowId + 1;
    return nextRowId;
  }

  private void incrementMetaDataCurrentRowId() throws IOException {
    this.file.seek(FILE_OFFSET_OF_METADATA_CURRENT_ROWID);
    final int oldRowId = this.file.readInt();

    checkState(oldRowId < ROWID_MAX_VALUE,
        format("All ROWIDs up through the maximum of %d have been exhausted. Cannot allocate new ROWID.",  // spell-checker:ignore rowids
            ROWID_MAX_VALUE));
    final int newRowId = oldRowId + 1;

    this.file.seek(FILE_OFFSET_OF_METADATA_CURRENT_ROWID);
    this.file.writeInt(newRowId);
  }

  private void setMetaDataRootPageNo(int newRootPageNo) throws IOException {
    this.file.seek(FILE_OFFSET_OF_METADATA_ROOT_PAGENO);
    this.file.writeInt(newRootPageNo);
  }

  private int getMetaDataRootPageNo() throws IOException {
    this.file.seek(FILE_OFFSET_OF_METADATA_ROOT_PAGENO);
    return this.file.readInt();
  }

  private boolean hasCurrentLeafPageNo() {
    return currentLeafPageNo != NULL_PAGE_NO;
  }

  private boolean hasCurrentLeafCellIndex() {
    return currentLeafCellIndex != NULL_CELL_INDEX;
  }

  private boolean hasCurrentRow() throws IOException {
    assert this.hasCurrentLeafPageNo() == this.hasCurrentLeafCellIndex();

    return
        this.hasCurrentLeafPageNo() &&
        Page.exists(file, this.currentLeafPageNo) &&
        this.currentLeafCellIndex < Page.getNumberOfCells(file, currentLeafPageNo);
  }

  private int getLeftmostLeafPageNo() throws IOException {
    int pageNo = this.getMetaDataRootPageNo();
    while (Page.getTablePageType(file, pageNo) == INTERIOR) {
      pageNo = Page.getLeftmostChildPageNoOfInteriorPage(file, pageNo);
    }
    assert Page.getTablePageType(file, pageNo) == LEAF;
    return pageNo;
  }

  //endregion

}
