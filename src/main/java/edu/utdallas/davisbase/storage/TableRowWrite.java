package edu.utdallas.davisbase.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;
import static edu.utdallas.davisbase.RowIdUtils.ROWID_COLUMN_INDEX;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableSortedMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import edu.utdallas.davisbase.DataType;

/**
 * An immutable, context-free specification for a collection of zero-or-more column-wise writes that
 * altogether constitute an atomic {@link TableFile#write(TableRowWrite)} operation on a single row.
 */
public class TableRowWrite implements Iterable<Map.Entry<@NonNull Byte, @Nullable Object>> {

  private final SortedMap<@NonNull Byte, @Nullable Object> valuesByColumnIndex;

  private TableRowWrite(Map<@NonNull Byte, @Nullable Object> valuesByColumnIndex) {
    assert valuesByColumnIndex != null;
    assert valuesByColumnIndex.keySet().stream().allMatch(colIdx -> 0 <= colIdx && colIdx < Byte.MAX_VALUE);
    assert valuesByColumnIndex.keySet().stream().allMatch(colIdx -> colIdx != ROWID_COLUMN_INDEX);
    assert valuesByColumnIndex.values().stream().allMatch(val -> val == null || stream(DataType.values()).anyMatch(dt -> dt.getJavaClass().isInstance(val)));

    // Copy map for compile-time encapsulation, and wrap in an unmodifiable view for run-time immutability.
    this.valuesByColumnIndex = unmodifiableSortedMap(new TreeMap<@NonNull Byte, @Nullable Object>(valuesByColumnIndex));
  }

  /**
   * @param columnIndex
   * @return
   * @see java.util.Map#containsKey(java.lang.Object)
   */
  public boolean containsColumnIndex(byte columnIndex) {
    return valuesByColumnIndex.containsKey(columnIndex);
  }

  /**
   * @param value
   * @return
   * @see java.util.Map#containsValue(java.lang.Object)
   */
  @SuppressWarnings("nullness")  // For whatever reason, the parameter of java.util.SortedMap#containsValue(Object) is incorrectly annotated @NonNull.
  public boolean containsValue(@Nullable Object value) {
    return valuesByColumnIndex.containsValue(value);
  }

  /**
   * @param columnIndex
   * @return
   * @see java.util.Map#get(java.lang.Object)
   */
  public @Nullable Object get(byte columnIndex) {
    return valuesByColumnIndex.get(columnIndex);
  }

  /**
   * @param columnIndex
   * @param defaultValue
   * @return
   * @see java.util.Map#getOrDefault(java.lang.Object, java.lang.Object)
   */
  public @Nullable Object getOrDefault(byte columnIndex, @Nullable Object defaultValue) {
    return valuesByColumnIndex.getOrDefault(columnIndex, defaultValue);
  }

  /**
   * @return
   * @see java.util.Map#isEmpty()
   */
  public boolean isEmpty() {
    return valuesByColumnIndex.isEmpty();
  }

  /**
   * @return an iterator over the {@code columnIndex : value} entries in this {@code TableRowWrite}
   *         operation, ordered by column index
   * @see java.util.SortedMap#entrySet()
   * @see java.util.Map.Entry
   */
  @Override
  public Iterator<Map.Entry<@NonNull Byte, @Nullable Object>> iterator() {
    return valuesByColumnIndex.entrySet().iterator();
  }

  /**
   * @return
   * @see java.util.Map#size()
   */
  public int size() {
    return valuesByColumnIndex.size();
  }

  /**
   * @param o
   * @return
   * @see java.util.Map#equals(java.lang.Object)
   */
  @Override
  @SuppressWarnings("nullness")  // For whatever reason, the parameter of java.util.SortedMap#equals(Object) is incorrectly annotated @NonNull.
  public boolean equals(@Nullable Object o) {
    return valuesByColumnIndex.equals(o);
  }

  /**
   * @return
   * @see java.util.Map#hashCode()
   */
  @Override
  public int hashCode() {
    return valuesByColumnIndex.hashCode();
  }

  /**
   * @return
   * @see java.util.Map#toString()
   */
  @Override
  public String toString() {
    return valuesByColumnIndex.toString();
  }

  public static class Builder {

    private final Map<@NonNull Byte, @Nullable Object> valuesByColumnIndex = new HashMap<>();

    private void checkColumnIndex(byte columnIndex) {
      checkElementIndex(columnIndex, Byte.MAX_VALUE,
          format("columnIndex must be in range [0, %d)",
              Byte.MAX_VALUE));
      checkArgument(columnIndex != ROWID_COLUMN_INDEX,
          format("May not write to rowid columnIndex %d",
              ROWID_COLUMN_INDEX));
    }

    public Builder writeNull(byte columnIndex) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, null);
      return this;
    }

    public Builder writeTinyInt(byte columnIndex, byte value) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeSmallInt(byte columnIndex, short value) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeInt(byte columnIndex, int value) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeBigInt(byte columnIndex, long value) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeFloat(byte columnIndex, float value) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeDouble(byte columnIndex, double value) {
      checkColumnIndex(columnIndex);

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeYear(byte columnIndex, Year value) {
      checkColumnIndex(columnIndex);
      checkNotNull(value, "value");

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeTime(byte columnIndex, LocalTime value) {
      checkColumnIndex(columnIndex);
      checkNotNull(value, "value");

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeDateTime(byte columnIndex, LocalDateTime value) {
      checkColumnIndex(columnIndex);
      checkNotNull(value, "value");

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeDate(byte columnIndex, LocalDate value) {
      checkColumnIndex(columnIndex);
      checkNotNull(value, "value");

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public Builder writeText(byte columnIndex, String value) {
      checkColumnIndex(columnIndex);
      checkNotNull(value, "value");

      valuesByColumnIndex.put(columnIndex, value);
      return this;
    }

    public TableRowWrite build() {
      return new TableRowWrite(this.valuesByColumnIndex);
    }

  }

}
