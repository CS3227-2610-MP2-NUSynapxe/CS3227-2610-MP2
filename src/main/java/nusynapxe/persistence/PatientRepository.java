package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.ClinicClock;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PatientDeletionBlockers;

/** Persists patient mutations while delegating reads to a focused query repository. */
public final class PatientRepository {
  private static final int EXPECTED_UPDATE_COUNT = 1;
  private static final String PATIENT_MISSING_MESSAGE = "Patient does not exist: ";
  private final SqliteDatabase database;
  private final Clock clock;
  private final PatientQueryRepository queries;

  /** Creates a patient repository using the Singapore clinic system clock. */
  public PatientRepository(SqliteDatabase database) {
    this(database, ClinicClock.system());
  }

  /** Creates a patient repository using an injectable clinic clock. */
  public PatientRepository(SqliteDatabase database, Clock clock) {
    this.database = Objects.requireNonNull(database, "database");
    this.clock = ClinicClock.withClinicZone(clock);
    this.queries = new PatientQueryRepository(database);
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
                  date_of_birth, sex, phone_country_code, phone_number, email, address,
                  height_cm, weight_kg, active,
                  created_at, updated_at
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              """;
          try (PreparedStatement statement =
              connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            bindPatient(statement, patient);
            String timestamp = SqliteQueries.formatTimestamp(ClinicClock.now(clock));
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
                  first_name = ?, last_name = ?, date_of_birth = ?, sex = ?,
                  phone_country_code = ?, phone_number = ?, email = ?, address = ?,
                  height_cm = ?, weight_kg = ?, active = ?,
                  updated_at = ?
              WHERE id = ?
              """;
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPatient(statement, patient);
            statement.setString(15, SqliteQueries.formatTimestamp(ClinicClock.now(clock)));
            statement.setLong(16, patient.id());
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw new SQLException(PATIENT_MISSING_MESSAGE + patient.id());
            }
            return patient;
          }
        });
  }

  /** Deactivates a patient while preserving its history. */
  public Patient deactivate(long patientId) throws SQLException {
    return setActive(patientId, false);
  }

  /** Reactivates a patient while preserving its history. */
  public Patient activate(long patientId) throws SQLException {
    return setActive(patientId, true);
  }

  private Patient setActive(long patientId, boolean active) throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          Patient patient =
              queries.findById(connection, patientId).orElseThrow(() -> missing(patientId));
          try (PreparedStatement update =
              connection.prepareStatement(
                  "UPDATE patients SET active = ?, updated_at = ? WHERE id = ?")) {
            update.setInt(1, active ? 1 : 0);
            update.setString(2, SqliteQueries.formatTimestamp(ClinicClock.now(clock)));
            update.setLong(3, patientId);
            if (update.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw missing(patientId);
            }
          }
          return withActive(patient, active);
        });
  }

  /** Finds one patient's non-clinical basic information. */
  public Optional<Patient> findById(long id) throws SQLException {
    return queries.findById(id);
  }

  /** Finds a patient by normalized composite document identity. */
  public Optional<Patient> findByIdentity(
      IdentityType type, String issuingCountry, String identityNumber) throws SQLException {
    return queries.findByIdentity(type, issuingCountry, identityNumber);
  }

  /** Returns non-sensitive relationship counts for a deletion check. */
  public Optional<PatientDeletionBlockers> findDeletionBlockers(long patientId)
      throws SQLException {
    return queries.findDeletionBlockers(patientId);
  }

  /** Deletes an unused patient or returns relationship counts that block deletion. */
  public Optional<PatientDeletionBlockers> deleteIfUnrelated(long patientId) throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          if (!queries.patientExists(connection, patientId)) {
            throw missing(patientId);
          }
          PatientDeletionBlockers blockers = queries.readDeletionBlockers(connection, patientId);
          if (!blockers.canDelete()) {
            return Optional.of(blockers);
          }
          try (PreparedStatement statement =
              connection.prepareStatement("DELETE FROM patients WHERE id = ?")) {
            statement.setLong(1, patientId);
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw missing(patientId);
            }
          } catch (SQLException exception) {
            if (!isForeignKeyViolation(exception)) {
              throw exception;
            }
            PatientDeletionBlockers refreshed = queries.readDeletionBlockers(connection, patientId);
            if (refreshed.canDelete()) {
              refreshed = refreshed.withAdditionalOtherReferences(1);
            }
            return Optional.of(refreshed);
          }
          return Optional.empty();
        });
  }

  /** Returns all patients in deterministic name and Patient ID order. */
  public List<Patient> findAll() throws SQLException {
    return queries.findAll();
  }

  /** Searches non-clinical patient data using one trimmed query. */
  public List<Patient> search(String requestedQuery) throws SQLException {
    return queries.search(requestedQuery);
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
    setNullableString(statement, 8, patient.phoneCountryCode());
    statement.setString(9, patient.phoneNumber());
    statement.setString(10, patient.email());
    statement.setString(11, patient.address());
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
        patient.phoneCountryCode(),
        patient.phoneNumber(),
        patient.email(),
        patient.address(),
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
        patient.phoneCountryCode(),
        patient.phoneNumber(),
        patient.email(),
        patient.address(),
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
        patient.phoneCountryCode(),
        patient.phoneNumber(),
        patient.email(),
        patient.address(),
        patient.heightCm(),
        patient.weightKg(),
        active);
  }

  private static SQLException missing(long patientId) {
    return new SQLException(PATIENT_MISSING_MESSAGE + patientId);
  }

  private static boolean isForeignKeyViolation(SQLException exception) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains("foreign key")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
