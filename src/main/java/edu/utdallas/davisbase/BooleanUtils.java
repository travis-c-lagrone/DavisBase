package edu.utdallas.davisbase;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utilities for simulating a `BOOLEAN` data type in DavisBase.
 */
public class BooleanUtils {

  public static String TRUE_VALUE = "YES";
  public static String FALSE_VALUE = "NO";

  private BooleanUtils() {}

  public static String toText(boolean value) {
    return value ? TRUE_VALUE : FALSE_VALUE;
  }

  public static boolean fromText(String value) {
    checkNotNull(value, "value");

    if (value.equalsIgnoreCase(TRUE_VALUE)) {
      return true;
    }
    else if (value.equalsIgnoreCase(FALSE_VALUE)) {
      return false;
    }
    else {
      throw new IllegalArgumentException(value);
    }
  }

}
