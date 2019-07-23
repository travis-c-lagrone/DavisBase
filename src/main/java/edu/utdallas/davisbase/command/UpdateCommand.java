package edu.utdallas.davisbase.command;

import java.util.List;

public class UpdateCommand implements Command {

  private String tableName;
  private List<String> columnIds;  // COMBAK Split columnIds field into a list column name strings and a list of column id bytes.
  private List<String> values;  // COMBAK Refactor values field to use structured objects vs. raw strings.
  // QUESTION How should the where expression be represented?

  // COMBAK Implement UpdateCommand

  public UpdateCommand(String tableName, List<String> columnIds, List<String> values) {
    this.tableName = tableName;
    this.columnIds = columnIds;
    this.values = values;
  }
}
