package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
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
      assertEquals(1, foreignKeysEnabled(database));
      assertEquals("6", metadataValue(database, "schema_version"));
      assertTrue(tableNames(database).containsAll(expectedFeatureTables()));
      assertTrue(indexNames(database).contains("idx_appointments_doctor_time"));
      assertTrue(indexNames(database).contains("idx_calendar_intervals_doctor_day"));
      assertTrue(indexNames(database).contains("idx_patients_document_identity"));
      assertTrue(columnNames(database, "patients").containsAll(expectedPatientColumns()));
    }

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

  @Test
  void patientForeignKeysRemainRestrictive() throws SQLException {
    try (SqliteDatabase database =
        new SqliteDatabase(temporaryDirectory.resolve("foreign-keys.db"))) {
      database.open();

      for (String table :
          Set.of("appointments", "clinical_records", "prescriptions", "payments", "receipts")) {
        assertTrue(foreignKeyDeleteActions(database, table).stream().allMatch("NO ACTION"::equals));
      }
    }
  }

  private static int foreignKeysEnabled(SqliteDatabase database) throws SQLException {
    try (PreparedStatement statement =
            database.connection().prepareStatement("PRAGMA foreign_keys");
        ResultSet resultSet = statement.executeQuery()) {
      assertTrue(resultSet.next());
      return resultSet.getInt(1);
    }
  }

  private static String metadataValue(SqliteDatabase database, String key) throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement("SELECT value FROM app_metadata WHERE key = ?")) {
      statement.setString(1, key);
      try (ResultSet resultSet = statement.executeQuery()) {
        assertTrue(resultSet.next());
        return resultSet.getString(1);
      }
    }
  }

  private static Set<String> tableNames(SqliteDatabase database) throws SQLException {
    return objectNames(database, "table");
  }

  private static Set<String> indexNames(SqliteDatabase database) throws SQLException {
    return objectNames(database, "index");
  }

  private static Set<String> columnNames(SqliteDatabase database, String table)
      throws SQLException {
    try (PreparedStatement statement =
            database.connection().prepareStatement("PRAGMA table_info(" + table + ")");
        ResultSet resultSet = statement.executeQuery()) {
      Set<String> names = new HashSet<>();
      while (resultSet.next()) {
        names.add(resultSet.getString("name"));
      }
      return Set.copyOf(names);
    }
  }

  private static Set<String> foreignKeyDeleteActions(SqliteDatabase database, String table)
      throws SQLException {
    try (PreparedStatement statement =
            database.connection().prepareStatement("PRAGMA foreign_key_list(" + table + ")");
        ResultSet resultSet = statement.executeQuery()) {
      Set<String> actions = new HashSet<>();
      while (resultSet.next()) {
        actions.add(resultSet.getString("on_delete"));
      }
      return Set.copyOf(actions);
    }
  }

  private static Set<String> objectNames(SqliteDatabase database, String type) throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement("SELECT name FROM sqlite_master WHERE type = ?")) {
      statement.setString(1, type);
      try (ResultSet resultSet = statement.executeQuery()) {
        return readNames(resultSet);
      }
    }
  }

  private static Set<String> readNames(ResultSet resultSet) throws SQLException {
    Set<String> names = new HashSet<>();
    while (resultSet.next()) {
      names.add(resultSet.getString(1));
    }
    return Set.copyOf(names);
  }

  private static Set<String> expectedFeatureTables() {
    return Set.of(
        "app_metadata",
        "users",
        "patients",
        "appointments",
        "doctor_time_off",
        "doctor_calendar_settings",
        "doctor_working_intervals",
        "clinical_records",
        "prescriptions",
        "payments");
  }

  private static Set<String> expectedPatientColumns() {
    return Set.of(
        "id",
        "identity_type",
        "identity_number",
        "issuing_country",
        "first_name",
        "last_name",
        "date_of_birth",
        "sex",
        "phone_country_code",
        "phone_number",
        "email",
        "address",
        "height_cm",
        "weight_kg",
        "active",
        "created_at",
        "updated_at");
  }
}
