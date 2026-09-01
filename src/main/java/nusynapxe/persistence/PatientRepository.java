package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Sex;

/** Persists the non-clinical portion of patient records. */
public final class PatientRepository {
  private static final int EXPECTED_UPDATE_COUNT = 1;
  private static final String PATIENT_COLUMNS =
      "id, identity_type, identity_number, issuing_country, first_name, last_name, "
          + "date_of_birth, sex, phone, email, address, billing_information, height_cm, "
          + "weight_kg, active";
  private static final String PATIENT_ORDER = " ORDER BY last_name, first_name, id";
  private static final String SELECT_PATIENTS = "SELECT " + PATIENT_COLUMNS + " FROM patients";
  private static final String SELECT_PATIENT_BY_ID = SELECT_PATIENTS + " WHERE id = ?";
  private static final String SELECT_PATIENT_BY_IDENTITY =
      SELECT_PATIENTS + " WHERE identity_type = ? AND issuing_country = ? AND identity_number = ?";
  private static final String SEARCH_PATIENTS =
      SELECT_PATIENTS
          + " WHERE (identity_type LIKE ? ESCAPE '\\' "
          + "OR identity_number LIKE ? ESCAPE '\\' OR issuing_country LIKE ? ESCAPE '\\' "
          + "OR first_name LIKE ? ESCAPE '\\' OR last_name LIKE ? ESCAPE '\\' "
          + "OR phone LIKE ? ESCAPE '\\' OR email LIKE ? ESCAPE '\\' "
          + "OR (? IS NOT NULL AND id = ?))"
          + PATIENT_ORDER;
  private final SqliteDatabase database;

  /** Creates a patient repository backed by an opened database. */
  public PatientRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /** Creates a patient and returns its generated Patient ID. */
  public Patient create(Patient requestedPatient) throws SQLException {
    Objects.requireNonNull(requestedPatient, "patient");
    Patient patient = normalizeIdentity(requestedPatient);
    return SqliteTransactions.execute(
        database,
        connection -> {
          String sql =
              """
              INSERT INTO patients(
                  identity_type, identity_number, issuing_country, first_name, last_name,
                  date_of_birth, sex, phone, email, address, billing_information, height_cm,
                  weight_kg, active, created_at, updated_at
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              """;
          try (PreparedStatement statement =
              connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            bindPatient(statement, patient);
            String timestamp = SqliteQueries.formatTimestamp(LocalDateTime.now());
            statement.setString(15, timestamp);
            statement.setString(16, timestamp);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
              if (!generatedKeys.next()) {
                throw new SQLException("SQLite did not return the new Patient ID");
              }
              return withId(patient, generatedKeys.getLong(1));
            }
          }
        });
  }

  /** Atomically updates a patient's permitted basic information. */
  public Patient update(Patient requestedPatient) throws SQLException {
    Objects.requireNonNull(requestedPatient, "patient");
    Patient patient = normalizeIdentity(requestedPatient);
    return SqliteTransactions.execute(
        database,
        connection -> {
          String sql =
              """
              UPDATE patients SET
                  identity_type = ?, identity_number = ?, issuing_country = ?,
                  first_name = ?, last_name = ?, date_of_birth = ?, sex = ?, phone = ?,
                  email = ?, address = ?, billing_information = ?, height_cm = ?,
                  weight_kg = ?, active = ?, updated_at = ?
              WHERE id = ?
              """;
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPatient(statement, patient);
            statement.setString(15, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            statement.setLong(16, patient.id());
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw new SQLException("Patient does not exist: " + patient.id());
            }
            return patient;
          }
        });
  }

  /** Deactivates a patient while preserving the Patient ID and all related history. */
  public Patient deactivate(long patientId) throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          Patient patient;
          try (PreparedStatement select = connection.prepareStatement(SELECT_PATIENT_BY_ID)) {
            select.setLong(1, patientId);
            try (ResultSet resultSet = select.executeQuery()) {
              if (!resultSet.next()) {
                throw new SQLException("Patient does not exist: " + patientId);
              }
              patient = readPatient(resultSet);
            }
          }
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE patients SET active = 0, updated_at = ? WHERE id = ?")) {
            update.setString(1, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            update.setLong(2, patientId);
            if (update.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw new SQLException("Patient does not exist: " + patientId);
            }
          }
          return withActive(patient, false);
        });
  }

  /** Finds one patient's non-clinical basic information. */
  public Optional<Patient> findById(long id) throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement(SELECT_PATIENT_BY_ID)) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readPatient(resultSet)) : Optional.empty();
      }
    }
  }

  /** Finds a patient by the normalized composite document identity. */
  public Optional<Patient> findByIdentity(
      IdentityType type, String issuingCountry, String identityNumber) throws SQLException {
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

  /** Returns all patients in deterministic name and Patient ID order. */
  public List<Patient> findAll() throws SQLException {
    return search("");
  }

  /**
   * Searches non-clinical patient data using one trimmed query.
   *
   * <p>Text fields use escaped literal substring matching. Numeric and {@code P}-prefixed values
   * also match an exact generated Patient ID.
   */
  public List<Patient> search(String requestedQuery) throws SQLException {
    String query = requestedQuery == null ? "" : requestedQuery.trim();
    if (query.isEmpty()) {
      try (PreparedStatement statement =
          database.connection().prepareStatement(SELECT_PATIENTS + PATIENT_ORDER)) {
        return SqliteQueries.readAll(statement, PatientRepository::readPatient);
      }
    }

    Long patientId = parsePatientId(query);
    try (PreparedStatement statement = database.connection().prepareStatement(SEARCH_PATIENTS)) {
      String pattern = "%" + escapeLike(query) + "%";
      for (int index = 1; index <= 7; index++) {
        statement.setString(index, pattern);
      }
      if (patientId == null) {
        statement.setNull(8, Types.BIGINT);
        statement.setNull(9, Types.BIGINT);
      } else {
        statement.setLong(8, patientId);
        statement.setLong(9, patientId);
      }
      return SqliteQueries.readAll(statement, PatientRepository::readPatient);
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

  private static void bindPatient(PreparedStatement statement, Patient patient)
      throws SQLException {
    setEnum(statement, 1, patient.identityType());
    setNullableString(statement, 2, patient.identityNumber());
    setNullableString(statement, 3, patient.issuingCountry());
    statement.setString(4, patient.firstName());
    statement.setString(5, patient.lastName());
    statement.setString(6, patient.dateOfBirth());
    setEnum(statement, 7, patient.sex());
    statement.setString(8, patient.phone());
    statement.setString(9, patient.email());
    statement.setString(10, patient.address());
    statement.setString(11, patient.billingInformation());
    setNullableDouble(statement, 12, patient.heightCm());
    setNullableDouble(statement, 13, patient.weightKg());
    statement.setInt(14, patient.active() ? 1 : 0);
  }

  private static void setEnum(PreparedStatement statement, int index, Enum<?> value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.VARCHAR);
    } else {
      statement.setString(index, value.name());
    }
  }

  private static void setNullableString(PreparedStatement statement, int index, String value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.VARCHAR);
    } else {
      statement.setString(index, value);
    }
  }

  private static void setNullableDouble(PreparedStatement statement, int index, Double value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.REAL);
    } else {
      statement.setDouble(index, value);
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
        resultSet.getString("phone"),
        resultSet.getString("email"),
        resultSet.getString("address"),
        resultSet.getString("billing_information"),
        nullableDouble(resultSet, "height_cm"),
        nullableDouble(resultSet, "weight_kg"),
        resultSet.getInt("active") == 1);
  }

  private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
    return value == null ? null : Enum.valueOf(type, value);
  }

  private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
    double value = resultSet.getDouble(column);
    return resultSet.wasNull() ? null : value;
  }

  private static Patient normalizeIdentity(Patient patient) {
    return new Patient(
        patient.id(),
        patient.identityType(),
        normalizeNullable(patient.identityNumber()),
        normalizeNullable(patient.issuingCountry()),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        patient.phone(),
        patient.email(),
        patient.address(),
        patient.billingInformation(),
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  private static String normalizeNullable(String value) {
    return value == null ? null : normalize(value);
  }

  private static String normalize(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static Patient withId(Patient patient, long id) {
    return new Patient(
        id,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        patient.phone(),
        patient.email(),
        patient.address(),
        patient.billingInformation(),
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  private static Patient withActive(Patient patient, boolean active) {
    return new Patient(
        patient.id(),
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        patient.phone(),
        patient.email(),
        patient.address(),
        patient.billingInformation(),
        patient.heightCm(),
        patient.weightKg(),
        active);
  }
}
