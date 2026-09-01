package nusynapxe.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Creates and versions the SQLite schema used by the clinic application. */
final class SchemaInitializer {
  static final int CURRENT_VERSION = 1;

  private static final String SCHEMA_VERSION_KEY = "schema_version";
  private static final List<String> SCHEMA_STATEMENTS =
      List.of(
          """
          CREATE TABLE IF NOT EXISTS app_metadata (
              key TEXT PRIMARY KEY NOT NULL,
              value TEXT NOT NULL
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS users (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              username TEXT NOT NULL COLLATE NOCASE UNIQUE,
              display_name TEXT NOT NULL,
              role TEXT NOT NULL CHECK (role IN ('DOCTOR', 'RECEPTIONIST', 'SYSTEM_ADMIN')),
              enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
              password_salt BLOB NOT NULL,
              password_verifier BLOB NOT NULL,
              created_at TEXT NOT NULL
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS patients (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              first_name TEXT NOT NULL,
              last_name TEXT NOT NULL,
              date_of_birth TEXT NOT NULL,
              phone TEXT NOT NULL,
              email TEXT NOT NULL,
              address TEXT NOT NULL,
              billing_information TEXT NOT NULL,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS appointments (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              patient_id INTEGER NOT NULL REFERENCES patients(id),
              doctor_id INTEGER NOT NULL REFERENCES users(id),
              starts_at TEXT NOT NULL,
              ends_at TEXT NOT NULL,
              status TEXT NOT NULL CHECK (
                  status IN ('PENDING', 'ACCEPTED', 'CHECKED_IN', 'COMPLETED',
                             'CHECKED_OUT', 'CANCELLED')
              ),
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL,
              CHECK (ends_at > starts_at)
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS doctor_time_off (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              doctor_id INTEGER NOT NULL REFERENCES users(id),
              starts_at TEXT NOT NULL,
              ends_at TEXT NOT NULL,
              CHECK (ends_at > starts_at)
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS clinical_records (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              patient_id INTEGER NOT NULL REFERENCES patients(id),
              appointment_id INTEGER NOT NULL UNIQUE REFERENCES appointments(id),
              doctor_id INTEGER NOT NULL REFERENCES users(id),
              diagnosis TEXT NOT NULL,
              consultation_notes TEXT NOT NULL,
              follow_up_notes TEXT NOT NULL,
              updated_at TEXT NOT NULL
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS prescriptions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              clinical_record_id INTEGER NOT NULL REFERENCES clinical_records(id),
              medication TEXT NOT NULL,
              dosage TEXT NOT NULL,
              frequency TEXT NOT NULL,
              duration TEXT NOT NULL,
              instructions TEXT NOT NULL,
              created_at TEXT NOT NULL
          )
          """,
          """
          CREATE TABLE IF NOT EXISTS payments (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              appointment_id INTEGER NOT NULL UNIQUE REFERENCES appointments(id),
              patient_id INTEGER NOT NULL REFERENCES patients(id),
              receptionist_id INTEGER NOT NULL REFERENCES users(id),
              amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
              method TEXT NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'OTHER')),
              status TEXT NOT NULL CHECK (status IN ('SUCCESSFUL', 'UNSUCCESSFUL')),
              recorded_at TEXT NOT NULL
          )
          """,
          "CREATE INDEX IF NOT EXISTS idx_appointments_doctor_time ON appointments(doctor_id, starts_at)",
          "CREATE INDEX IF NOT EXISTS idx_appointments_patient_time ON appointments(patient_id, starts_at)",
          "CREATE INDEX IF NOT EXISTS idx_time_off_doctor_time ON doctor_time_off(doctor_id, starts_at)",
          "CREATE INDEX IF NOT EXISTS idx_payments_recorded_status ON payments(recorded_at, status)");

  private SchemaInitializer() {
    throw new AssertionError("Utility class");
  }

  /** Initializes the schema in one transaction. */
  static void initialize(Connection connection) throws SQLException {
    boolean originalAutoCommit = connection.getAutoCommit();
    try {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        for (String sql : SCHEMA_STATEMENTS) {
          statement.executeUpdate(sql);
        }
        try (var versionStatement =
            connection.prepareStatement(
                "INSERT OR IGNORE INTO app_metadata(key, value) VALUES (?, ?)")) {
          versionStatement.setString(1, SCHEMA_VERSION_KEY);
          versionStatement.setString(2, Integer.toString(CURRENT_VERSION));
          versionStatement.executeUpdate();
        }
      }
      connection.commit();
    } catch (SQLException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(originalAutoCommit);
    }
  }
}
