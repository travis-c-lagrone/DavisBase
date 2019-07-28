package edu.utdallas.davisbase.executor;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import static org.checkerframework.checker.nullness.NullnessUtil.castNonNull;

import static com.google.common.base.Preconditions.checkNotNull;

import static edu.utdallas.davisbase.DataType.INT;
import static edu.utdallas.davisbase.DataType.TEXT;
import static edu.utdallas.davisbase.DataType.TINYINT;
import static edu.utdallas.davisbase.catalog.CatalogTable.DAVISBASE_COLUMNS;
import static edu.utdallas.davisbase.catalog.CatalogTable.DAVISBASE_TABLES;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import edu.utdallas.davisbase.BooleanUtils;
import edu.utdallas.davisbase.DataType;
import edu.utdallas.davisbase.NotImplementedException;
import edu.utdallas.davisbase.catalog.DavisBaseColumnsTableColumn;
import edu.utdallas.davisbase.catalog.DavisBaseTablesTableColumn;
import edu.utdallas.davisbase.command.Command;
import edu.utdallas.davisbase.command.CreateIndexCommand;
import edu.utdallas.davisbase.command.CreateTableCommand;
import edu.utdallas.davisbase.command.CreateTableCommandColumn;
import edu.utdallas.davisbase.command.DeleteCommand;
import edu.utdallas.davisbase.command.DropTableCommand;
import edu.utdallas.davisbase.command.ExitCommand;
import edu.utdallas.davisbase.command.InsertCommand;
import edu.utdallas.davisbase.command.SelectCommand;
import edu.utdallas.davisbase.command.SelectCommandColumn;
import edu.utdallas.davisbase.command.ShowTablesCommand;
import edu.utdallas.davisbase.command.UpdateCommand;
import edu.utdallas.davisbase.result.CreateIndexResult;
import edu.utdallas.davisbase.result.CreateTableResult;
import edu.utdallas.davisbase.result.DeleteResult;
import edu.utdallas.davisbase.result.DropTableResult;
import edu.utdallas.davisbase.result.ExitResult;
import edu.utdallas.davisbase.result.InsertResult;
import edu.utdallas.davisbase.result.Result;
import edu.utdallas.davisbase.result.SelectResult;
import edu.utdallas.davisbase.result.SelectResultData;
import edu.utdallas.davisbase.result.SelectResultDataRow;
import edu.utdallas.davisbase.result.SelectResultSchema;
import edu.utdallas.davisbase.result.SelectResultSchemaColumn;
import edu.utdallas.davisbase.result.ShowTablesResult;
import edu.utdallas.davisbase.result.UpdateResult;
import edu.utdallas.davisbase.storage.Storage;
import edu.utdallas.davisbase.storage.StorageException;
import edu.utdallas.davisbase.storage.TableFile;
import edu.utdallas.davisbase.storage.TableRowBuilder;

/**
 * An executor of {@link Command}s against a {@link Storage} context, and thereby a producer of
 * {@link Result}s.
 */
public class Executor {

  protected final ExecutorConfiguration configuration;
  protected final Storage context;

  public Executor(ExecutorConfiguration configuration, Storage context) {
    checkNotNull(configuration, "configuration");
    checkNotNull(context, "context");

    this.configuration = configuration;
    this.context = context;
  }

  public Result execute(Command command) throws ExecuteException, StorageException, IOException {
    checkNotNull(command, "command");

    Result result;
    if (command instanceof CreateIndexCommand) {
      result = executeCreateIndex((CreateIndexCommand) command);
    }
    else if (command instanceof CreateTableCommand) {
      result = executeCreateTable((CreateTableCommand) command);
    }
    else if (command instanceof DeleteCommand) {
      result = executeDelete((DeleteCommand) command);
    }
    else if (command instanceof DropTableCommand) {
      result = executeDropTable((DropTableCommand) command);
    }
    else if (command instanceof ExitCommand) {
      result = executeExit((ExitCommand) command);
    }
    else if (command instanceof InsertCommand) {
      result = executeInsert((InsertCommand) command);
    }
    else if (command instanceof SelectCommand) {
      result = executeSelectCommand((SelectCommand) command);
    }
    else if (command instanceof ShowTablesCommand) {
      result = executeShowTables((ShowTablesCommand) command);
    }
    else if (command instanceof UpdateCommand) {
      result = executeUpdate((UpdateCommand) command);
    }
    else {
      throw new ExecuteException(format("Unimplemented command type: %s", command.getClass().getName()));
    }
    return result;
  }

  protected CreateIndexResult executeCreateIndex(CreateIndexCommand command) throws ExecuteException, StorageException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    // COMBAK Implement Executor.execute(CreateIndexCommand, Storage)
    throw new NotImplementedException();
  }

  protected CreateTableResult executeCreateTable(CreateTableCommand command) throws ExecuteException, StorageException, IOException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    assert DavisBaseTablesTableColumn.ROWID.getOrdinalPosition() == 0;
    assert DavisBaseTablesTableColumn.TABLE_NAME.getOrdinalPosition() == 1;
    assert DavisBaseTablesTableColumn.TABLE_NAME.getDataType() == TEXT;
    assert DavisBaseTablesTableColumn.values().length == 2;

    assert DavisBaseColumnsTableColumn.ROWID.getOrdinalPosition() == 0;
    assert DavisBaseColumnsTableColumn.TABLE_NAME.getOrdinalPosition() == 1;
    assert DavisBaseColumnsTableColumn.TABLE_NAME.getDataType() == TEXT;
    assert DavisBaseColumnsTableColumn.COLUMN_NAME.getOrdinalPosition() == 2;
    assert DavisBaseColumnsTableColumn.COLUMN_NAME.getDataType() == TEXT;
    assert DavisBaseColumnsTableColumn.DATA_TYPE.getOrdinalPosition() == 3;
    assert DavisBaseColumnsTableColumn.DATA_TYPE.getDataType() == TEXT;
    assert DavisBaseColumnsTableColumn.ORDINAL_POSITION.getOrdinalPosition() == 4;
    assert DavisBaseColumnsTableColumn.ORDINAL_POSITION.getDataType() == TINYINT;
    assert DavisBaseColumnsTableColumn.IS_NULLABLE.getOrdinalPosition() == 5;
    assert DavisBaseColumnsTableColumn.IS_NULLABLE.getDataType() == TEXT;
    assert DavisBaseColumnsTableColumn.values().length == 6;

    final String tableName = command.getTableName();
    context.createTableFile(tableName);

    try (final TableFile davisbaseTables = context.openTableFile(DAVISBASE_TABLES.getName())) {
      final TableRowBuilder rowBuilder = new TableRowBuilder();
      rowBuilder.appendText(tableName);
      davisbaseTables.appendRow(rowBuilder);
    }

    try (final TableFile davisbaseColumns = context.openTableFile(DAVISBASE_COLUMNS.getName())) {
      byte ordinalPosition = 0;

      // COMBAK Make a static constant class to modularly structure the special "rowid" column that all tables have, then refactor these magic literals to constant references.
      final TableRowBuilder rowidRowBuilder = new TableRowBuilder();
      rowidRowBuilder.appendText(tableName);
      rowidRowBuilder.appendText("rowid");
      rowidRowBuilder.appendText(INT.name());
      rowidRowBuilder.appendTinyInt(ordinalPosition);
      rowidRowBuilder.appendText(BooleanUtils.toText(false));
      davisbaseColumns.appendRow(rowidRowBuilder);

      for (final CreateTableCommandColumn column : command.getColumnSchemas()) {
        ordinalPosition += 1;
        assert 1 <= ordinalPosition && ordinalPosition < Byte.MAX_VALUE;

        final TableRowBuilder rowBuilder = new TableRowBuilder();
        rowBuilder.appendText(tableName);
        rowBuilder.appendText(column.getName());
        rowBuilder.appendText(column.getDataType().name());
        rowBuilder.appendTinyInt(ordinalPosition);
        rowBuilder.appendText(BooleanUtils.toText(!column.isNotNull()));  // COMBAK Refactor (name + logic) CreateTableCommandColumn#isNotNull() to #isNullable().
        davisbaseColumns.appendRow(rowBuilder);
      }
    }

    final CreateTableResult result = new CreateTableResult(tableName);
    return result;
  }

  // TODO Implement support for WHERE clause in Executor#executeDelete(DeleteCommand)
  protected DeleteResult executeDelete(DeleteCommand command) throws ExecuteException, StorageException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    // COMBAK Implement Executor.execute(DeleteCommand, Storage)
    throw new NotImplementedException();
  }

  protected DropTableResult executeDropTable(DropTableCommand command) throws ExecuteException, StorageException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    // COMBAK Implement Executor.execute(DropTableCommand, Storage)
    throw new NotImplementedException();
  }

  protected ExitResult executeExit(ExitCommand command) throws ExecuteException, StorageException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    return new ExitResult();
  }

  protected InsertResult executeInsert(InsertCommand command) throws ExecuteException, StorageException, IOException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    final TableRowBuilder rowBuilder = new TableRowBuilder();
    for (final @Nullable Object value : command.getValues()) {
      if (value == null) {
        rowBuilder.appendNull();
      }
      else if (DataType.TINYINT.getJavaClass().isInstance(value)) {
        rowBuilder.appendTinyInt((byte) value);
      }
      else if (DataType.SMALLINT.getJavaClass().isInstance(value)) {
        rowBuilder.appendSmallInt((short) value);
      }
      else if (DataType.INT.getJavaClass().isInstance(value)) {
        rowBuilder.appendInt((int) value);
      }
      else if (DataType.BIGINT.getJavaClass().isInstance(value)) {
        rowBuilder.appendBigInt((long) value);
      }
      else if (DataType.FLOAT.getJavaClass().isInstance(value)) {
        rowBuilder.appendFloat((float) value);
      }
      else if (DataType.DOUBLE.getJavaClass().isInstance(value)) {
        rowBuilder.appendDouble((double) value);
      }
      else if (DataType.YEAR.getJavaClass().isInstance(value)) {
        rowBuilder.appendYear((Year) value);
      }
      else if (DataType.TIME.getJavaClass().isInstance(value)) {
        rowBuilder.appendTime((LocalTime) value);
      }
      else if (DataType.DATETIME.getJavaClass().isInstance(value)) {
        rowBuilder.appendDateTime((LocalDateTime) value);
      }
      else if (DataType.DATE.getJavaClass().isInstance(value)) {
        rowBuilder.appendDate((LocalDate) value);
      }
      else if (DataType.TEXT.getJavaClass().isInstance(value)) {
        rowBuilder.appendText((String) value);
      }
      else {
        throw new NotImplementedException(format("Insert value of Java class %s", value.getClass().getName()));
      }
    }

    final String tableName = command.getTableName();
    try (TableFile tableFile = context.openTableFile(tableName)) {
      tableFile.appendRow(rowBuilder);
    }

    final InsertResult result = new InsertResult(tableName, 1);
    return result;
  }

  // TODO Implement support for WHERE clause in Executor#executeSelect(SelectCommand)
  protected SelectResult executeSelectCommand(SelectCommand command) throws ExecuteException, StorageException, IOException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    final String tableName = command.getTableName();
    final List<SelectCommandColumn> columns = command.getSelectClauseColumns();
    final int columnCount = columns.size();

    final SelectResultData.Builder dataBuilder = new SelectResultData.Builder();
    try (TableFile tableFile = context.openTableFile(tableName)) {
      while (tableFile.goToNextRow()) {

        final SelectResultDataRow.Builder dataRowBuilder = new SelectResultDataRow.Builder(columnCount);
        for (SelectCommandColumn column : columns) {
          final byte columnIndex = column.getIndex();

          switch (column.getDataType()) {
            case TINYINT:
              final @Nullable Byte maybeTinyInt = tableFile.readTinyInt(columnIndex);
              dataRowBuilder.addTinyInt(maybeTinyInt);
              break;

            case SMALLINT:
              final @Nullable Short maybeSmallInt = tableFile.readSmallInt(columnIndex);
              dataRowBuilder.addSmallInt(maybeSmallInt);
              break;

            case INT:
              final @Nullable Integer maybeInt = tableFile.readInt(columnIndex);
              dataRowBuilder.addInt(maybeInt);
              break;

            case BIGINT:
              final @Nullable Long maybeBigInt = tableFile.readBigInt(columnIndex);
              dataRowBuilder.addBigInt(maybeBigInt);
              break;

            case FLOAT:
              final @Nullable Float maybeFloat = tableFile.readFloat(columnIndex);
              dataRowBuilder.addFloat(maybeFloat);
              break;

            case DOUBLE:
              final @Nullable Double maybeDouble = tableFile.readDouble(columnIndex);
              dataRowBuilder.addDouble(maybeDouble);
              break;

            case YEAR:
              final @Nullable Year maybeYear = tableFile.readYear(columnIndex);
              dataRowBuilder.addYear(maybeYear);
              break;

            case TIME:
              final @Nullable LocalTime maybeTime = tableFile.readTime(columnIndex);
              dataRowBuilder.addTime(maybeTime);
              break;

            case DATETIME:
              final @Nullable LocalDateTime maybeDateTime = tableFile.readDateTime(columnIndex);
              dataRowBuilder.addDateTime(maybeDateTime);
              break;

            case DATE:
              final @Nullable LocalDate maybeDate = tableFile.readDate(columnIndex);
              dataRowBuilder.addDate(maybeDate);
              break;

            case TEXT:
              final @Nullable String maybeText = tableFile.readText(columnIndex);
              dataRowBuilder.addText(maybeText);
              break;

            default:
              throw new NotImplementedException(format(
                  "Execution of a SelectCommand over a SelectCommandColumn of DataType %s",
                      column.getDataType()));
          }
        }

        SelectResultDataRow dataRow = dataRowBuilder.build();
        dataBuilder.writeRow(dataRow);
      }
    }

    final SelectResultData data = dataBuilder.build();
    final SelectResultSchema schema = new SelectResultSchema(
        columns.stream()
               .map(col -> new SelectResultSchemaColumn(col.getName(), col.getDataType()))
               .collect(toList()));
    final SelectResult result = new SelectResult(schema, data);
    return result;
  }

  protected ShowTablesResult executeShowTables(ShowTablesCommand command) throws ExecuteException, StorageException, IOException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    final String catalogTablesTableName = DAVISBASE_TABLES.getName();
    final byte catalogTablesTableTableNameColumnIndex = DavisBaseTablesTableColumn.TABLE_NAME.getOrdinalPosition();

    final List<String> tableNames = new ArrayList<>();
    try (TableFile tableFile = context.openTableFile(catalogTablesTableName)) {
      while (tableFile.goToNextRow()) {
        final @NonNull String tableName = castNonNull(
            tableFile.readText(catalogTablesTableTableNameColumnIndex)
        );
        tableNames.add(tableName);
      }
    }

    final ShowTablesResult result = new ShowTablesResult(tableNames);
    return result;
  }

  // TODO Implement support for WHERE clause in Executor#executeUpdate(UpdateCommand)
  protected UpdateResult executeUpdate(UpdateCommand command) throws ExecuteException, StorageException {
    assert command != null : "command should not be null";
    assert context != null : "context should not be null";

    // COMBAK Implement Executor.execute(UpdateCommand, Storage)
    throw new NotImplementedException();
  }

}
