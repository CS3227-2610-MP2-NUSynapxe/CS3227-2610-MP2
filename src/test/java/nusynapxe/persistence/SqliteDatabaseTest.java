package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteDatabaseTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void opensCreatesSchemaAndCloses() throws SQLException {
    Path databasePath = temporaryDirectory.resolve("application.db");

    try (SqliteDatabase database = new SqliteDatabase(databasePath)) {
      database.open();

      assertTrue(database.isOpen());
      try (PreparedStatement statement =
              database
                  .connection()
                  .prepareStatement(
                      "SELECT name FROM sqlite_master "
                          + "WHERE type = 'table' AND name = 'app_metadata'");
          ResultSet resultSet = statement.executeQuery()) {
        assertTrue(resultSet.next());
        assertEquals("app_metadata", resultSet.getString("name"));
      }
    }

    assertTrue(Files.isRegularFile(databasePath));
  }

  @Test
  void remainsClosedUntilOpened() throws SQLException {
    try (SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("closed.db"))) {
      assertFalse(database.isOpen());
    }
  }
}
