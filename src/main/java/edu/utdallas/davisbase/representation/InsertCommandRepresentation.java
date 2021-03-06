package edu.utdallas.davisbase.representation;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InsertCommandRepresentation implements CommandRepresentation {
  private final String command;
  private final String table;
  private final List<Column> columns;
  private final List<Expression> values;

  public InsertCommandRepresentation(String command, String table, List<Column> columns, List<Expression> values) {
    this.command= command;
    this.table = table;
    this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    this.values = Collections.unmodifiableList(new ArrayList<>(values));
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

  @Override
  public String getFullCommand() {
    return command;
  }

  @Override
  public String getOperation() {
    return "INSERT INTO";
  }

  @Override
  public String toString() {
    return "InsertCommandRepresentation{" +
      "command='" + command + '\'' +
      ", table='" + table + '\'' +
      ", columns=" + columns +
      ", values=" + values +
      '}';
  }
}
