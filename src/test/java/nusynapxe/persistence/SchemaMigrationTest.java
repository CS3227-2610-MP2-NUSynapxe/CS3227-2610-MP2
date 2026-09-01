package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SchemaMigrationTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void migratesVersionOneDataWithoutInventingBasicValues() throws SQLException {
    Path path = temporaryDirectory.resolve("version-one.db");
    createVersionOneFixture(path);

    try (SqliteDatabase database = new SqliteDatabase(path)) {
      database.open();

      assertEquals("2", scalar(database.connection(), "SELECT value FROM app_metadata"));
      assertEquals("1", scalar(database.connection(), "SELECT id FROM patients"));
      assertEquals("1", scalar(database.connection(), "SELECT patient_id FROM appointments"));
      assertEquals("1", scalar(database.connection(), "SELECT patient_id FROM clinical_records"));
      assertEquals("1", scalar(database.connection(), "SELECT patient_id FROM payments"));
      assertEquals(
          "1", scalar(database.connection(), "SELECT clinical_record_id FROM prescriptions"));
      try (Statement statement = database.connection().createStatement();
          ResultSet patient =
              statement.executeQuery(
                  "SELECT identity_type, identity_number, issuing_country, sex, height_cm, "
                      + "weight_kg, active FROM patients WHERE id = 1")) {
        assertTrue(patient.next());
        assertNull(patient.getString("identity_type"));
        assertNull(patient.getString("identity_number"));
        assertNull(patient.getString("issuing_country"));
        assertNull(patient.getString("sex"));
        assertNull(patient.getObject("height_cm"));
        assertNull(patient.getObject("weight_kg"));
        assertEquals(1, patient.getInt("active"));
      }
    }
  }

  @Test
  void rollsBackPartiallyAppliedMigrationAndVersionAdvance() throws SQLException {
    Path path = temporaryDirectory.resolve("broken-version-one.db");
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE app_metadata(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
      statement.executeUpdate("INSERT INTO app_metadata VALUES ('schema_version', '1')");
      statement.executeUpdate(
          """
          CREATE TABLE patients (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              first_name TEXT NOT NULL,
              last_name TEXT NOT NULL,
              date_of_birth TEXT NOT NULL,
              phone TEXT NOT NULL,
              email TEXT NOT NULL,
              address TEXT NOT NULL,
              billing_information TEXT NOT NULL,
              identity_number TEXT,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
          )
          """);

      assertThrows(SQLException.class, () -> SchemaInitializer.initialize(connection));
      assertEquals("1", scalar(connection, "SELECT value FROM app_metadata"));
      assertFalse(columnNames(connection, "patients").contains("identity_type"));
    }
  }

  private static void createVersionOneFixture(Path path) throws SQLException {
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE TABLE app_metadata(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
      statement.executeUpdate("INSERT INTO app_metadata VALUES ('schema_version', '1')");
      statement.executeUpdate(
          """
          CREATE TABLE users (
              id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE,
              display_name TEXT NOT NULL, role TEXT NOT NULL, enabled INTEGER NOT NULL,
              password_salt BLOB NOT NULL, password_verifier BLOB NOT NULL,
              created_at TEXT NOT NULL
          )
          """);
      statement.executeUpdate(
          "INSERT INTO users VALUES (1, 'doctor', 'Doctor', 'DOCTOR', 1, X'01', X'02', 'now')");
      statement.executeUpdate(
          "INSERT INTO users VALUES (2, 'reception', 'Reception', 'RECEPTIONIST', 1, "
              + "X'03', X'04', 'now')");
      statement.executeUpdate(
          """
          CREATE TABLE patients (
              id INTEGER PRIMARY KEY AUTOINCREMENT, first_name TEXT NOT NULL,
              last_name TEXT NOT NULL, date_of_birth TEXT NOT NULL, phone TEXT NOT NULL,
              email TEXT NOT NULL, address TEXT NOT NULL, billing_information TEXT NOT NULL,
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL
          )
          """);
      statement.executeUpdate(
          "INSERT INTO patients VALUES "
              + "(1, 'Legacy', 'Patient', '1990-01-01', '123', '', '', '', 'now', 'now')");
      statement.executeUpdate(
          """
          CREATE TABLE appointments (
              id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL,
              doctor_id INTEGER NOT NULL, starts_at TEXT NOT NULL, ends_at TEXT NOT NULL,
              status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
          )
          """);
      statement.executeUpdate(
          "INSERT INTO appointments VALUES "
              + "(1, 1, 1, '2026-09-01T09:00:00', '2026-09-01T09:30:00', "
              + "'COMPLETED', 'now', 'now')");
      statement.executeUpdate(
          """
          CREATE TABLE clinical_records (
              id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL,
              appointment_id INTEGER NOT NULL UNIQUE, doctor_id INTEGER NOT NULL,
              diagnosis TEXT NOT NULL, consultation_notes TEXT NOT NULL,
              follow_up_notes TEXT NOT NULL, updated_at TEXT NOT NULL
          )
          """);
      statement.executeUpdate(
          "INSERT INTO clinical_records VALUES (1, 1, 1, 1, 'd', 'n', 'f', 'now')");
      statement.executeUpdate(
          """
          CREATE TABLE prescriptions (
              id INTEGER PRIMARY KEY AUTOINCREMENT, clinical_record_id INTEGER NOT NULL,
              medication TEXT NOT NULL, dosage TEXT NOT NULL, frequency TEXT NOT NULL,
              duration TEXT NOT NULL, instructions TEXT NOT NULL, created_at TEXT NOT NULL
          )
          """);
      statement.executeUpdate(
          "INSERT INTO prescriptions VALUES (1, 1, 'm', 'd', 'f', 't', 'i', 'now')");
      statement.executeUpdate(
          """
          CREATE TABLE payments (
              id INTEGER PRIMARY KEY AUTOINCREMENT, appointment_id INTEGER NOT NULL UNIQUE,
              patient_id INTEGER NOT NULL, receptionist_id INTEGER NOT NULL,
              amount_minor INTEGER NOT NULL, method TEXT NOT NULL, status TEXT NOT NULL,
              recorded_at TEXT NOT NULL
          )
          """);
      statement.executeUpdate(
          "INSERT INTO payments VALUES (1, 1, 1, 2, 1000, 'CASH', 'SUCCESSFUL', 'now')");
    }
  }

  private static String scalar(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      assertTrue(resultSet.next());
      return resultSet.getString(1);
    }
  }

  private static Set<String> columnNames(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
      Set<String> columns = new HashSet<>();
      while (resultSet.next()) {
        columns.add(resultSet.getString("name"));
      }
      return Set.copyOf(columns);
    }
  }
}
