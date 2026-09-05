package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Prescription;

/** Persists clinical records and prescriptions without exposing them to administrative queries. */
public final class ClinicalRecordRepository {
  private static final String RECORD_COLUMNS =
      "id, patient_id, appointment_id, doctor_id, diagnosis, consultation_notes, follow_up_notes";
  private static final String PRESCRIPTION_COLUMNS =
      "id, clinical_record_id, medication, dosage, frequency, duration, instructions";
  private final SqliteDatabase database;

  /**
   * Creates a clinical repository backed by an opened database.
   *
   * @param database database used for clinical persistence
   * @throws NullPointerException if {@code database} is {@code null}
   */
  public ClinicalRecordRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /**
   * Finds the clinical record for an appointment.
   *
   * @param appointmentId appointment identifier
   * @return clinical record, or empty when consultation notes have not been saved
   * @throws SQLException if the query fails
   */
  public Optional<ClinicalRecord> findByAppointment(long appointmentId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT " + RECORD_COLUMNS + " FROM clinical_records WHERE appointment_id = ?")) {
      statement.setLong(1, appointmentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readRecord(resultSet)) : Optional.empty();
      }
    }
  }

  /**
   * Saves or updates the one clinical record associated with an appointment.
   *
   * @param record clinical record to insert or replace
   * @return record with its persisted identifier
   * @throws NullPointerException if {@code record} is {@code null}
   * @throws SQLException if the save transaction fails
   */
  public ClinicalRecord save(ClinicalRecord record) throws SQLException {
    Objects.requireNonNull(record, "record");
    return SqliteTransactions.execute(
        database,
        connection -> {
          Optional<Long> existingId = findIdByAppointment(connection, record.appointmentId());
          if (existingId.isPresent()) {
            updateRecord(connection, existingId.orElseThrow(), record);
            return withId(record, existingId.orElseThrow());
          }
          return insertRecord(connection, record);
        });
  }

  /**
   * Adds a prescription to a clinical record.
   *
   * @param prescription prescription to persist
   * @return prescription with its generated identifier
   * @throws NullPointerException if {@code prescription} is {@code null}
   * @throws SQLException if the insert fails
   */
  public Prescription addPrescription(Prescription prescription) throws SQLException {
    Objects.requireNonNull(prescription, "prescription");
    return SqliteTransactions.execute(
        database,
        connection -> {
          String sql =
              "INSERT INTO prescriptions(clinical_record_id, medication, dosage, frequency, "
                  + "duration, instructions, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
          try (PreparedStatement statement =
              connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, prescription.clinicalRecordId());
            statement.setString(2, prescription.medication());
            statement.setString(3, prescription.dosage());
            statement.setString(4, prescription.frequency());
            statement.setString(5, prescription.duration());
            statement.setString(6, prescription.instructions());
            statement.setString(7, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
              if (!generatedKeys.next()) {
                throw new SQLException("SQLite did not return the prescription identifier");
              }
              return withId(prescription, generatedKeys.getLong(1));
            }
          }
        });
  }

  /**
   * Returns prescriptions for a clinical record in creation order.
   *
   * @param clinicalRecordId clinical record identifier
   * @return immutable prescription list ordered by identifier
   * @throws SQLException if the query fails
   */
  public List<Prescription> findPrescriptions(long clinicalRecordId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT "
                    + PRESCRIPTION_COLUMNS
                    + " FROM prescriptions WHERE clinical_record_id = ? ORDER BY id")) {
      statement.setLong(1, clinicalRecordId);
      return SqliteQueries.readAll(statement, ClinicalRecordRepository::readPrescription);
    }
  }

  private static ClinicalRecord insertRecord(java.sql.Connection connection, ClinicalRecord record)
      throws SQLException {
    String sql =
        "INSERT INTO clinical_records(patient_id, appointment_id, doctor_id, diagnosis, "
            + "consultation_notes, follow_up_notes, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement statement =
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setLong(1, record.patientId());
      statement.setLong(2, record.appointmentId());
      statement.setLong(3, record.doctorId());
      statement.setString(4, record.diagnosis());
      statement.setString(5, record.consultationNotes());
      statement.setString(6, record.followUpNotes());
      statement.setString(7, SqliteQueries.formatTimestamp(LocalDateTime.now()));
      statement.executeUpdate();
      try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
        if (!generatedKeys.next()) {
          throw new SQLException("SQLite did not return the clinical record identifier");
        }
        return withId(record, generatedKeys.getLong(1));
      }
    }
  }

  private static void updateRecord(java.sql.Connection connection, long id, ClinicalRecord record)
      throws SQLException {
    String sql =
        "UPDATE clinical_records SET patient_id = ?, appointment_id = ?, doctor_id = ?, "
            + "diagnosis = ?, consultation_notes = ?, follow_up_notes = ?, updated_at = ? WHERE id = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, record.patientId());
      statement.setLong(2, record.appointmentId());
      statement.setLong(3, record.doctorId());
      statement.setString(4, record.diagnosis());
      statement.setString(5, record.consultationNotes());
      statement.setString(6, record.followUpNotes());
      statement.setString(7, SqliteQueries.formatTimestamp(LocalDateTime.now()));
      statement.setLong(8, id);
      statement.executeUpdate();
    }
  }

  private static Optional<Long> findIdByAppointment(
      java.sql.Connection connection, long appointmentId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM clinical_records WHERE appointment_id = ?")) {
      statement.setLong(1, appointmentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(resultSet.getLong(1)) : Optional.empty();
      }
    }
  }

  private static ClinicalRecord readRecord(ResultSet resultSet) throws SQLException {
    return new ClinicalRecord(
        resultSet.getLong("id"),
        resultSet.getLong("patient_id"),
        resultSet.getLong("appointment_id"),
        resultSet.getLong("doctor_id"),
        resultSet.getString("diagnosis"),
        resultSet.getString("consultation_notes"),
        resultSet.getString("follow_up_notes"));
  }

  private static Prescription readPrescription(ResultSet resultSet) throws SQLException {
    return new Prescription(
        resultSet.getLong("id"),
        resultSet.getLong("clinical_record_id"),
        resultSet.getString("medication"),
        resultSet.getString("dosage"),
        resultSet.getString("frequency"),
        resultSet.getString("duration"),
        resultSet.getString("instructions"));
  }

  private static ClinicalRecord withId(ClinicalRecord record, long id) {
    return new ClinicalRecord(
        id,
        record.patientId(),
        record.appointmentId(),
        record.doctorId(),
        record.diagnosis(),
        record.consultationNotes(),
        record.followUpNotes());
  }

  private static Prescription withId(Prescription prescription, long id) {
    return new Prescription(
        id,
        prescription.clinicalRecordId(),
        prescription.medication(),
        prescription.dosage(),
        prescription.frequency(),
        prescription.duration(),
        prescription.instructions());
  }
}
