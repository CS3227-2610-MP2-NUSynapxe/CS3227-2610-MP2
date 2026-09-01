package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteTransactionsTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void commitsWorkAndRestoresAutoCommit() throws SQLException {
    try (SqliteDatabase database = openDatabase("commit.db")) {
      SqliteTransactions.execute(
          database,
          connection -> {
            try (var statement =
                connection.prepareStatement("INSERT INTO app_metadata(key, value) VALUES (?, ?)")) {
              statement.setString(1, "transaction-test");
              statement.setString(2, "committed");
              statement.executeUpdate();
            }
            return null;
          });

      assertTrue(database.connection().getAutoCommit());
      try (var statement =
          database.connection().prepareStatement("SELECT value FROM app_metadata WHERE key = ?")) {
        statement.setString(1, "transaction-test");
        try (var resultSet = statement.executeQuery()) {
          assertTrue(resultSet.next());
          assertEquals("committed", resultSet.getString(1));
        }
      }
    }
  }

  @Test
  void rollsBackSqlFailure() throws SQLException {
    try (SqliteDatabase database = openDatabase("rollback.db")) {
      assertThrows(
          SQLException.class,
          () ->
              SqliteTransactions.execute(
                  database,
                  connection -> {
                    try (var statement =
                        connection.prepareStatement(
                            "INSERT INTO app_metadata(key, value) VALUES (?, ?)")) {
                      statement.setString(1, "transaction-test");
                      statement.setString(2, "rolled-back");
                      statement.executeUpdate();
                      throw new SQLException("force rollback");
                    }
                  }));

      assertFalse(metadataExists(database, "transaction-test"));
      assertTrue(database.connection().getAutoCommit());
    }
  }

  @Test
  void rollsBackRuntimeFailure() throws SQLException {
    try (SqliteDatabase database = openDatabase("runtime-rollback.db")) {
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteTransactions.execute(
                  database,
                  connection -> {
                    try (var statement =
                        connection.prepareStatement(
                            "INSERT INTO app_metadata(key, value) VALUES (?, ?)")) {
                      statement.setString(1, "runtime-test");
                      statement.setString(2, "rolled-back");
                      statement.executeUpdate();
                    }
                    throw new IllegalStateException("force rollback");
                  }));

      assertFalse(metadataExists(database, "runtime-test"));
    }
  }

  private SqliteDatabase openDatabase(String fileName) throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve(fileName));
    database.open();
    return database;
  }

  private static boolean metadataExists(SqliteDatabase database, String key) throws SQLException {
    try (var statement =
        database.connection().prepareStatement("SELECT 1 FROM app_metadata WHERE key = ?")) {
      statement.setString(1, key);
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
