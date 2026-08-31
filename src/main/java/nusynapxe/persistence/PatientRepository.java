package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Patient;

/** Persists the administrative portion of patient records. */
public final class PatientRepository {
  private static final int EXPECTED_UPDATE_COUNT = 1;
  private static final String PATIENT_COLUMNS =
      "id, first_name, last_name, date_of_birth, phone, email, address, billing_information";
  private final SqliteDatabase database;

  /** Creates a patient repository backed by an opened database. */
  public PatientRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /** Creates a patient and returns its assigned identifier. */
  public Patient create(Patient patient) throws SQLException {
    Objects.requireNonNull(patient, "patient");
    return SqliteTransactions.execute(
        database,
        connection -> {
          String sql =
              "INSERT INTO patients(first_name, last_name, date_of_birth, phone, email, address, "
                  + "billing_information, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
          try (PreparedStatement statement =
              connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            bindPatient(statement, patient);
            String timestamp = SqliteQueries.formatTimestamp(LocalDateTime.now());
            statement.setString(8, timestamp);
            statement.setString(9, timestamp);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
              if (!generatedKeys.next()) {
                throw new SQLException("SQLite did not return the new patient identifier");
              }
              return withId(patient, generatedKeys.getLong(1));
            }
          }
        });
  }

  /** Updates a patient's administrative information. */
  public Patient update(Patient patient) throws SQLException {
    Objects.requireNonNull(patient, "patient");
    return SqliteTransactions.execute(
        database,
        connection -> {
          String sql =
              "UPDATE patients SET first_name = ?, last_name = ?, date_of_birth = ?, phone = ?, "
                  + "email = ?, address = ?, billing_information = ?, updated_at = ? WHERE id = ?";
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPatient(statement, patient);
            statement.setString(8, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            statement.setLong(9, patient.id());
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw new SQLException("Patient does not exist: " + patient.id());
            }
            return patient;
          }
        });
  }

  /** Finds one patient's administrative information. */
  public Optional<Patient> findById(long id) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement("SELECT " + PATIENT_COLUMNS + " FROM patients WHERE id = ?")) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readPatient(resultSet)) : Optional.empty();
      }
    }
  }

  /** Returns all patients in surname and given-name order. */
  public List<Patient> findAll() throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT " + PATIENT_COLUMNS + " FROM patients ORDER BY last_name, first_name")) {
      return SqliteQueries.readAll(statement, PatientRepository::readPatient);
    }
  }

  private static void bindPatient(PreparedStatement statement, Patient patient)
      throws SQLException {
    statement.setString(1, patient.firstName());
    statement.setString(2, patient.lastName());
    statement.setString(3, patient.dateOfBirth());
    statement.setString(4, patient.phone());
    statement.setString(5, patient.email());
    statement.setString(6, patient.address());
    statement.setString(7, patient.billingInformation());
  }

  private static Patient readPatient(ResultSet resultSet) throws SQLException {
    return new Patient(
        resultSet.getLong("id"),
        resultSet.getString("first_name"),
        resultSet.getString("last_name"),
        resultSet.getString("date_of_birth"),
        resultSet.getString("phone"),
        resultSet.getString("email"),
        resultSet.getString("address"),
        resultSet.getString("billing_information"));
  }

  private static Patient withId(Patient patient, long id) {
    return new Patient(
        id,
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.phone(),
        patient.email(),
        patient.address(),
        patient.billingInformation());
  }
}
