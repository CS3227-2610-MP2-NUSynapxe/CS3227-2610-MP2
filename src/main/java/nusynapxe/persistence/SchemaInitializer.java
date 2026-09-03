package nusynapxe.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Creates and versions the SQLite schema used by the clinic application. */
final class SchemaInitializer {
  private static final int FIRST_VERSION = 1;
  private static final int SECOND_VERSION = 2;
  private static final int THIRD_VERSION = 3;
  private static final int FOURTH_VERSION = 4;
  static final int CURRENT_VERSION = 5;

  private static final String SCHEMA_VERSION_KEY = "schema_version";
  private static final String CREATE_METADATA =
      """
      CREATE TABLE IF NOT EXISTS app_metadata (
          key TEXT PRIMARY KEY NOT NULL,
          value TEXT NOT NULL
      )
      """;
  private static final String CREATE_DOCTOR_CALENDAR_SETTINGS =
      """
      CREATE TABLE IF NOT EXISTS doctor_calendar_settings (
          doctor_id INTEGER PRIMARY KEY NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          first_day_of_week TEXT NOT NULL CHECK (
              first_day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                                    'FRIDAY', 'SATURDAY', 'SUNDAY')
          )
      )
      """;
  private static final String CREATE_DOCTOR_WORKING_INTERVALS =
      """
      CREATE TABLE IF NOT EXISTS doctor_working_intervals (
          doctor_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          day_of_week TEXT NOT NULL CHECK (
              day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                              'FRIDAY', 'SATURDAY', 'SUNDAY')
          ),
          start_minute INTEGER NOT NULL CHECK (start_minute >= 0 AND start_minute < 1440),
          end_minute INTEGER NOT NULL CHECK (end_minute > start_minute AND end_minute <= 1440),
          PRIMARY KEY (doctor_id, day_of_week, start_minute)
      )
      """;
  private static final List<String> SCHEMA_STATEMENTS =
      List.of(
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
              identity_type TEXT COLLATE NOCASE
                  CHECK (identity_type IS NULL
                         OR identity_type IN ('NRIC', 'FIN', 'PASSPORT', 'OTHER')),
              identity_number TEXT COLLATE NOCASE,
              issuing_country TEXT COLLATE NOCASE,
              first_name TEXT NOT NULL,
              last_name TEXT NOT NULL,
              date_of_birth TEXT NOT NULL,
              sex TEXT CHECK (
                  sex IS NULL OR sex IN ('FEMALE', 'MALE')
              ),
              phone_country_code TEXT,
              phone_number TEXT NOT NULL,
              email TEXT NOT NULL,
              address TEXT NOT NULL,
              height_cm REAL CHECK (height_cm IS NULL OR height_cm > 0),
              weight_kg REAL CHECK (weight_kg IS NULL OR weight_kg > 0),
              active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
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
          """
          CREATE TABLE IF NOT EXISTS receipts (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              payment_id INTEGER NOT NULL UNIQUE REFERENCES payments(id),
              appointment_id INTEGER NOT NULL REFERENCES appointments(id),
              patient_id INTEGER NOT NULL REFERENCES patients(id),
              amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
              method TEXT NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'OTHER')),
              receipt_date TEXT NOT NULL,
              sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
              recorded_at TEXT NOT NULL,
              UNIQUE(receipt_date, sequence_number)
          )
          """,
          """
          CREATE UNIQUE INDEX IF NOT EXISTS idx_patients_document_identity
              ON patients(identity_type, issuing_country, identity_number)
              WHERE identity_type IS NOT NULL
                AND issuing_country IS NOT NULL
                AND identity_number IS NOT NULL
          """,
          "CREATE INDEX IF NOT EXISTS idx_appointments_doctor_time ON appointments(doctor_id, starts_at)",
          "CREATE INDEX IF NOT EXISTS idx_appointments_patient_time ON appointments(patient_id, starts_at)",
          "CREATE INDEX IF NOT EXISTS idx_time_off_doctor_time ON doctor_time_off(doctor_id, starts_at)",
          "CREATE INDEX IF NOT EXISTS idx_payments_recorded_status ON payments(recorded_at, status)",
          "CREATE INDEX IF NOT EXISTS idx_receipts_date_sequence ON receipts(receipt_date, sequence_number)",
          CREATE_DOCTOR_CALENDAR_SETTINGS,
          CREATE_DOCTOR_WORKING_INTERVALS,
          "CREATE INDEX IF NOT EXISTS idx_calendar_intervals_doctor_day "
              + "ON doctor_working_intervals(doctor_id, day_of_week, start_minute)");
  private static final List<String> VERSION_TWO_MIGRATION =
      List.of(
          "ALTER TABLE patients ADD COLUMN identity_type TEXT COLLATE NOCASE "
              + "CHECK (identity_type IS NULL OR identity_type IN "
              + "('NRIC', 'FIN', 'PASSPORT', 'OTHER'))",
          "ALTER TABLE patients ADD COLUMN identity_number TEXT COLLATE NOCASE",
          "ALTER TABLE patients ADD COLUMN issuing_country TEXT COLLATE NOCASE",
          "ALTER TABLE patients ADD COLUMN sex TEXT CHECK "
              + "(sex IS NULL OR sex IN ('FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED'))",
          "ALTER TABLE patients ADD COLUMN height_cm REAL "
              + "CHECK (height_cm IS NULL OR height_cm > 0)",
          "ALTER TABLE patients ADD COLUMN weight_kg REAL "
              + "CHECK (weight_kg IS NULL OR weight_kg > 0)",
          "ALTER TABLE patients ADD COLUMN active INTEGER NOT NULL DEFAULT 1 "
              + "CHECK (active IN (0, 1))",
          """
          CREATE UNIQUE INDEX idx_patients_document_identity
              ON patients(identity_type, issuing_country, identity_number)
              WHERE identity_type IS NOT NULL
                AND issuing_country IS NOT NULL
                AND identity_number IS NOT NULL
          """);
  private static final List<String> VERSION_THREE_MIGRATION =
      List.of(
          "UPDATE patients SET sex = NULL WHERE sex NOT IN ('FEMALE', 'MALE')",
          "ALTER TABLE patients DROP COLUMN billing_information");
  private static final List<String> VERSION_FOUR_MIGRATION =
      List.of(
          "ALTER TABLE patients RENAME COLUMN phone TO phone_number",
          "ALTER TABLE patients ADD COLUMN phone_country_code TEXT");
  private static final List<String> VERSION_FIVE_MIGRATION =
      List.of(
          CREATE_DOCTOR_CALENDAR_SETTINGS,
          CREATE_DOCTOR_WORKING_INTERVALS,
          "CREATE INDEX IF NOT EXISTS idx_calendar_intervals_doctor_day "
              + "ON doctor_working_intervals(doctor_id, day_of_week, start_minute)");
  private static final List<String> VERSION_FIVE_DEFAULTS =
      List.of(
          "INSERT OR IGNORE INTO doctor_calendar_settings(doctor_id, first_day_of_week) "
              + "SELECT id, 'SUNDAY' FROM users WHERE role = 'DOCTOR'",
          "INSERT OR IGNORE INTO doctor_working_intervals(doctor_id, day_of_week, "
              + "start_minute, end_minute) "
              + "SELECT id, 'MONDAY', 480, 1080 FROM users WHERE role = 'DOCTOR' "
              + "UNION ALL SELECT id, 'TUESDAY', 480, 1080 FROM users WHERE role = 'DOCTOR' "
              + "UNION ALL SELECT id, 'WEDNESDAY', 480, 1080 FROM users WHERE role = 'DOCTOR' "
              + "UNION ALL SELECT id, 'THURSDAY', 480, 1080 FROM users WHERE role = 'DOCTOR' "
              + "UNION ALL SELECT id, 'FRIDAY', 480, 1080 FROM users WHERE role = 'DOCTOR'");

  private SchemaInitializer() {
    throw new AssertionError("Utility class");
  }

  /** Initializes or migrates the schema in one transaction. */
  static void initialize(Connection connection) throws SQLException {
    boolean originalAutoCommit = connection.getAutoCommit();
    try {
      connection.setAutoCommit(false);
      execute(connection, CREATE_METADATA);
      Integer existingVersion = readVersion(connection);
      if (existingVersion != null
          && (existingVersion < FIRST_VERSION || existingVersion > CURRENT_VERSION)) {
        throw new SQLException("Unsupported schema version: " + existingVersion);
      }
      if (existingVersion != null) {
        int version = existingVersion;
        if (version < SECOND_VERSION) {
          executeAll(connection, VERSION_TWO_MIGRATION);
          version = SECOND_VERSION;
        }
        if (version < THIRD_VERSION) {
          executeAll(connection, VERSION_THREE_MIGRATION);
          version = THIRD_VERSION;
        }
        if (version < FOURTH_VERSION) {
          executeAll(connection, VERSION_FOUR_MIGRATION);
          version = FOURTH_VERSION;
        }
        if (version < CURRENT_VERSION) {
          executeAll(connection, VERSION_FIVE_MIGRATION);
        }
      }
      executeAll(connection, SCHEMA_STATEMENTS);
      if (existingVersion != null && existingVersion < CURRENT_VERSION) {
        executeAll(connection, VERSION_FIVE_DEFAULTS);
      }
      writeVersion(connection, CURRENT_VERSION);
      connection.commit();
    } catch (SQLException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(originalAutoCommit);
    }
  }

  private static Integer readVersion(Connection connection) throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT value FROM app_metadata WHERE key = ?")) {
      statement.setString(1, SCHEMA_VERSION_KEY);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        try {
          return Integer.valueOf(resultSet.getString(1));
        } catch (NumberFormatException exception) {
          throw new SQLException("Invalid stored schema version", exception);
        }
      }
    }
  }

  private static void writeVersion(Connection connection, int version) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            INSERT INTO app_metadata(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
      statement.setString(1, SCHEMA_VERSION_KEY);
      statement.setString(2, Integer.toString(version));
      statement.executeUpdate();
    }
  }

  private static void executeAll(Connection connection, List<String> statements)
      throws SQLException {
    for (String sql : statements) {
      execute(connection, sql);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }
}
