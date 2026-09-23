package nusynapxe.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PatientDeletionBlockers;
import nusynapxe.domain.Sex;

/** Reads patient records and deletion-reference projections from SQLite. */
final class PatientQueryRepository {
  private static final String PATIENTS_TABLE = "patients";
  private static final String PATIENT_COLUMNS =
      "id, identity_type, identity_number, issuing_country, first_name, last_name, "
          + "date_of_birth, sex, phone_country_code, phone_number, email, address, "
          + "height_cm, weight_kg, active";
  private static final String PATIENT_ORDER = " ORDER BY last_name, first_name, id";
  private static final String SELECT_PATIENTS = "SELECT " + PATIENT_COLUMNS + " FROM patients";
  private static final String SELECT_PATIENT_BY_ID = SELECT_PATIENTS + " WHERE id = ?";
  private static final String SELECT_PATIENT_BY_IDENTITY =
      SELECT_PATIENTS + " WHERE identity_type = ? AND issuing_country = ? AND identity_number = ?";
  private static final String COUNT_APPOINTMENTS =
      "SELECT COUNT(*) FROM appointments WHERE patient_id = ?";
  private static final String COUNT_CLINICAL_RECORDS =
      "SELECT COUNT(*) FROM clinical_records WHERE patient_id = ?";
  private static final String COUNT_PRESCRIPTIONS =
      "SELECT COUNT(*) FROM prescriptions p "
          + "JOIN clinical_records c ON c.id = p.clinical_record_id "
          + "WHERE c.patient_id = ?";
  private static final String COUNT_PAYMENTS = "SELECT COUNT(*) FROM payments WHERE patient_id = ?";
  private static final String COUNT_RECEIPTS = "SELECT COUNT(*) FROM receipts WHERE patient_id = ?";
  private static final String TABLE_NAMES =
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'";
  private static final Set<String> EXPLICIT_PATIENT_REFERENCE_TABLES =
      Set.of("appointments", "clinical_records", "payments", "receipts");
  private static final String SEARCH_PATIENTS =
      SELECT_PATIENTS
          + " WHERE (identity_type LIKE ? ESCAPE '\\' "
          + "OR identity_number LIKE ? ESCAPE '\\' OR issuing_country LIKE ? ESCAPE '\\' "
          + "OR first_name LIKE ? ESCAPE '\\' OR last_name LIKE ? ESCAPE '\\' "
          + "OR (first_name || ' ' || last_name) LIKE ? ESCAPE '\\' "
          + "OR (last_name || ' ' || first_name) LIKE ? ESCAPE '\\' "
          + "OR phone_country_code LIKE ? ESCAPE '\\' OR phone_number LIKE ? ESCAPE '\\' "
          + "OR ('+' || phone_country_code || phone_number) LIKE ? ESCAPE '\\' "
          + "OR email LIKE ? ESCAPE '\\' "
          + "OR (? IS NOT NULL AND id = ?))"
          + PATIENT_ORDER;
  private final SqliteDatabase database;

  PatientQueryRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  Optional<Patient> findById(long id) throws SQLException {
    return findById(database.connection(), id);
  }

  Optional<Patient> findById(Connection connection, long id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SELECT_PATIENT_BY_ID)) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readPatient(resultSet)) : Optional.empty();
      }
    }
  }

  Optional<Patient> findByIdentity(IdentityType type, String issuingCountry, String identityNumber)
      throws SQLException {
    if (type == null || issuingCountry == null || identityNumber == null) {
      return Optional.empty();
    }
    try (PreparedStatement statement =
        database.connection().prepareStatement(SELECT_PATIENT_BY_IDENTITY)) {
      statement.setString(1, type.name());
      statement.setString(2, normalize(issuingCountry));
      statement.setString(3, normalize(identityNumber));
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readPatient(resultSet)) : Optional.empty();
      }
    }
  }

  Optional<PatientDeletionBlockers> findDeletionBlockers(long patientId) throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement("SELECT 1 FROM patients WHERE id = ?")) {
      statement.setLong(1, patientId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
      }
    }
    return Optional.of(readDeletionBlockers(database.connection(), patientId));
  }

  List<Patient> findAll() throws SQLException {
    return search("");
  }

  List<Patient> search(String requestedQuery) throws SQLException {
    String query = requestedQuery == null ? "" : requestedQuery.trim();
    if (query.isEmpty()) {
      try (PreparedStatement statement =
          database.connection().prepareStatement(SELECT_PATIENTS + PATIENT_ORDER)) {
        return SqliteQueries.readAll(statement, PatientQueryRepository::readPatient);
      }
    }

    Long patientId = parsePatientId(query);
    try (PreparedStatement statement = database.connection().prepareStatement(SEARCH_PATIENTS)) {
      String pattern = "%" + escapeLike(query) + "%";
      for (int index = 1; index <= 11; index++) {
        statement.setString(index, pattern);
      }
      if (patientId == null) {
        statement.setNull(12, java.sql.Types.BIGINT);
        statement.setNull(13, java.sql.Types.BIGINT);
      } else {
        statement.setLong(12, patientId);
        statement.setLong(13, patientId);
      }
      return SqliteQueries.readAll(statement, PatientQueryRepository::readPatient);
    }
  }

  private static Patient readPatient(ResultSet resultSet) throws SQLException {
    return new Patient(
        resultSet.getLong("id"),
        enumValue(IdentityType.class, resultSet.getString("identity_type")),
        resultSet.getString("identity_number"),
        resultSet.getString("issuing_country"),
        resultSet.getString("first_name"),
        resultSet.getString("last_name"),
        resultSet.getString("date_of_birth"),
        enumValue(Sex.class, resultSet.getString("sex")),
        resultSet.getString("phone_country_code"),
        resultSet.getString("phone_number"),
        resultSet.getString("email"),
        resultSet.getString("address"),
        nullableDouble(resultSet, "height_cm"),
        nullableDouble(resultSet, "weight_kg"),
        resultSet.getInt("active") == 1);
  }

  PatientDeletionBlockers readDeletionBlockers(Connection connection, long patientId)
      throws SQLException {
    return new PatientDeletionBlockers(
        patientId,
        count(connection, COUNT_APPOINTMENTS, patientId),
        count(connection, COUNT_CLINICAL_RECORDS, patientId),
        count(connection, COUNT_PRESCRIPTIONS, patientId),
        count(connection, COUNT_PAYMENTS, patientId),
        count(connection, COUNT_RECEIPTS, patientId),
        countOtherPatientReferences(connection, patientId));
  }

  boolean patientExists(Connection connection, long patientId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM patients WHERE id = ?")) {
      statement.setLong(1, patientId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static Long parsePatientId(String query) {
    String candidate = query;
    if (candidate.length() > 1 && (candidate.charAt(0) == 'P' || candidate.charAt(0) == 'p')) {
      candidate = candidate.substring(1);
    }
    if (candidate.isEmpty() || !candidate.chars().allMatch(Character::isDigit)) {
      return null;
    }
    try {
      return Long.valueOf(candidate);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
    return value == null ? null : Enum.valueOf(type, value);
  }

  private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
    double value = resultSet.getDouble(column);
    return resultSet.wasNull() ? null : value;
  }

  private static String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static long count(Connection connection, String sql, long patientId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, patientId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("SQLite did not return a relationship count");
        }
        return resultSet.getLong(1);
      }
    }
  }

  private static long countOtherPatientReferences(Connection connection, long patientId)
      throws SQLException {
    long count = 0;
    try (PreparedStatement tables = connection.prepareStatement(TABLE_NAMES);
        ResultSet tableResults = tables.executeQuery()) {
      while (tableResults.next()) {
        String table = tableResults.getString(1);
        if (!EXPLICIT_PATIENT_REFERENCE_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
          count += countPatientForeignKeys(connection, table, patientId);
        }
      }
    }
    return count;
  }

  private static long countPatientForeignKeys(Connection connection, String table, long patientId)
      throws SQLException {
    long count = 0;
    String pragma = "PRAGMA foreign_key_list(" + quoteIdentifier(table) + ")";
    try (PreparedStatement foreignKeys = connection.prepareStatement(pragma);
        ResultSet foreignKeyResults = foreignKeys.executeQuery()) {
      while (foreignKeyResults.next()) {
        if (PATIENTS_TABLE.equalsIgnoreCase(foreignKeyResults.getString("table"))) {
          String column = foreignKeyResults.getString("from");
          if (column != null) {
            count += countForeignKeyRows(connection, table, column, patientId);
          }
        }
      }
    }
    return count;
  }

  private static long countForeignKeyRows(
      Connection connection, String table, String column, long patientId) throws SQLException {
    String sql =
        "SELECT COUNT(*) FROM "
            + quoteIdentifier(table)
            + " WHERE "
            + quoteIdentifier(column)
            + " = ?";
    return count(connection, sql, patientId);
  }

  private static String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
