package edu.utdallas.davisbase.representation;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UpdateCommandRepresentation implements CommandRepresentation {

  private final String command;
  private final String table;
  private final List<Column> columns;
  private final List<Expression> values;
  private final @Nullable WhereExpression whereClause;

  public UpdateCommandRepresentation(String command, String table, List<Column> columns, List<Expression> values, @Nullable WhereExpression whereClause) {
    this.command = command;
    this.table = table;
    this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    this.values = Collections.unmodifiableList(new ArrayList<>(values));
    this.whereClause = whereClause;
  }

  public String getTable() {
    return table;
  }

  public List<Column> getColumns() {
    return columns;
  }

  public List<Expression> getValues() {
    return values;
  }

  public @Nullable WhereExpression getWhereClause() {
    return whereClause;
  }

  @Override
  public String getFullCommand() {
    return command;
  }

  @Override
  public String getOperation() {
    return "UPDATE";
  }

  @Override
  public String toString() {
    return "UpdateCommandRepresentation{" +
      "command='" + command + '\'' +
      ", table='" + table + '\'' +
      ", columns=" + columns +
      ", values=" + values +
      ", whereClause=" + whereClause +
      '}';
  }
}
