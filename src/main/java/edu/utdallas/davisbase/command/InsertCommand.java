package edu.utdallas.davisbase.command;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.hash;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import edu.utdallas.davisbase.DataType;

public class InsertCommand implements Command {

  private final String tableName;
  private final int rowId;
  private final List<@Nullable Object> values;  // Assumes only one row is being inserted, which is true in DavisBase.

  /**
   * @param tableName    the name of the table into which to insert data (not null)
   * @param rowId        the row id to assign to the row to be inserted in the table; SHOULD be
   *                     greater than any other row id in the table (not null, not negative, less
   *                     than {@link java.lang.Integer#MAX_VALUE Integer.MAX_VALUE})
   * @param columnValues the ordered list of (nullable) values to insert, where the index of the
   *                     value in the list is the index of the column of the row into which it will
   *                     be inserted; SHOULD NOT include the index-zero default {@code rowId} (not
   *                     null, not empty, every value must be either null or an instance of
   *                     {@link DataType#getJavaClass()} for one of the {@link DataType}s)
   */
  public InsertCommand(String tableName, int rowId, List<@Nullable Object> values) {
    checkNotNull(tableName, "tableName");
    checkElementIndex(rowId, Integer.MAX_VALUE, format("rowId %d is not in the range [0, %d)", rowId, Integer.MAX_VALUE));
    checkNotNull(values, "values");
    checkArgument(!values.isEmpty(), "values is empty");
    for (int i = 0; i < values.size(); i++) {
      @Nullable Object value = values.get(i);
      if (value != null) {
        boolean isAcceptedDataType = false;
        for (DataType dataType : DataType.values()) {
          isAcceptedDataType |= dataType.getJavaClass().equals(value.getClass());
        }
        checkArgument(isAcceptedDataType, "values.get(%d) is neither null nor an accepted type: %s", i, value.getClass().getName());
      }
    }

    this.tableName = tableName;
    this.rowId = rowId;
    // Copy to a new list for encapsulation, and wrap in an unmodifiable view for immutability.
    this.values = unmodifiableList(new ArrayList<>(values));
  }

  /**
   * @return the name of the table into which to insert data (not null)
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * @return the row id to assign to the row to be inserted in the table; SHOULD be greater than any
   *         other row id in the table (not null, not negative, less than
   *         {@link java.lang.Integer#MAX_VALUE Integer.MAX_VALUE})
   */
  public int getRowId() {
    return rowId;
  }

  /**
   * @return an unmodifiable view of the ordered list of (nullable) values to insert, where the
   *         index of the value in the list is the index of the column of the row into which it will
   *         be inserted; SHOULD NOT include the index-zero default {@code rowId} (not null, not
   *         empty, every value is either null or an instance of {@link DataType#getJavaClass()} for
   *         one of the {@link DataType}s)
   */
  public List<Object> getValues() {
    return values;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj != null && obj instanceof InsertCommand)) {
      return false;
    }

    InsertCommand other = (InsertCommand) obj;
    return
        getTableName().equals(other.getTableName()) &&
        getValues().equals(other.getValues());
  }

  @Override
  public int hashCode() {
    return hash(getTableName(), getValues());
  }

  @Override
  public String toString() {
    return toStringHelper(InsertCommand.class)
        .add("tableName", getTableName())
        .add("values", getValues())
        .toString();
  }

}
