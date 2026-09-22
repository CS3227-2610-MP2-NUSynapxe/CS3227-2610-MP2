package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalHistoryEntry;
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
    String sql =
        "SELECT "
            + PRESCRIPTION_COLUMNS
            + " FROM prescriptions WHERE clinical_record_id = ? ORDER BY id";
    try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
      statement.setLong(1, clinicalRecordId);
      return SqliteQueries.readAll(statement, ClinicalRecordRepository::readPrescription);
    }
  }

  /**
   * Returns terminal consultation history for one patient, newest appointment first.
   *
   * <p>The projection intentionally joins appointment and assigned Doctor context here so callers
   * cannot accidentally display an in-progress consultation or lose the Doctor attribution.
   *
   * @param patientId patient identifier
   * @return immutable terminal consultation history ordered by appointment timestamp and id
   * @throws SQLException if the query fails
   */
  public List<ClinicalHistoryEntry> findHistoryByPatient(long patientId) throws SQLException {
    String sql =
        "SELECT c.id AS record_id, c.patient_id AS record_patient_id, "
            + "c.appointment_id AS record_appointment_id, c.doctor_id AS record_doctor_id, "
            + "c.diagnosis, c.consultation_notes, c.follow_up_notes, "
            + "a.id AS appointment_id, a.patient_id AS appointment_patient_id, "
            + "a.doctor_id AS appointment_doctor_id, a.starts_at, a.ends_at, a.status, "
            + "u.display_name AS doctor_name "
            + "FROM clinical_records c "
            + "JOIN appointments a ON a.id = c.appointment_id "
            + "JOIN users u ON u.id = a.doctor_id "
            + "WHERE c.patient_id = ? AND a.status IN ('COMPLETED', 'CHECKED_OUT') "
            + "ORDER BY a.starts_at DESC, a.id DESC";
    try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
      statement.setLong(1, patientId);
      try (ResultSet resultSet = statement.executeQuery()) {
        List<HistoryProjection> projections = new ArrayList<>();
        while (resultSet.next()) {
          projections.add(
              new HistoryProjection(
                  readHistoryAppointment(resultSet),
                  resultSet.getString("doctor_name"),
                  readHistoryRecord(resultSet)));
        }
        Map<Long, List<Prescription>> prescriptionsByRecord = findPrescriptionsByPatient(patientId);
        List<ClinicalHistoryEntry> history = new ArrayList<>(projections.size());
        for (HistoryProjection projection : projections) {
          history.add(
              new ClinicalHistoryEntry(
                  projection.appointment(),
                  projection.doctorName(),
                  projection.record(),
                  prescriptionsByRecord.getOrDefault(projection.record().id(), List.of())));
        }
        return List.copyOf(history);
      }
    }
  }

  private Map<Long, List<Prescription>> findPrescriptionsByPatient(long patientId)
      throws SQLException {
    String sql =
        "SELECT p.id, p.clinical_record_id, p.medication, p.dosage, p.frequency, p.duration, "
            + "p.instructions FROM prescriptions p "
            + "JOIN clinical_records c ON c.id = p.clinical_record_id "
            + "JOIN appointments a ON a.id = c.appointment_id "
            + "WHERE c.patient_id = ? AND a.status IN ('COMPLETED', 'CHECKED_OUT') "
            + "ORDER BY p.clinical_record_id, p.id";
    Map<Long, List<Prescription>> prescriptionsByRecord = new HashMap<>();
    try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
      statement.setLong(1, patientId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          Prescription prescription = readPrescription(resultSet);
          prescriptionsByRecord
              .computeIfAbsent(prescription.clinicalRecordId(), ignored -> new ArrayList<>())
              .add(prescription);
        }
      }
    }
    prescriptionsByRecord.replaceAll((ignored, prescriptions) -> List.copyOf(prescriptions));
    return Map.copyOf(prescriptionsByRecord);
  }

  private record HistoryProjection(
      Appointment appointment, String doctorName, ClinicalRecord record) {
    // Groups the joined history row before prescription enrichment.
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

  private static ClinicalRecord readHistoryRecord(ResultSet resultSet) throws SQLException {
    return new ClinicalRecord(
        resultSet.getLong("record_id"),
        resultSet.getLong("record_patient_id"),
        resultSet.getLong("record_appointment_id"),
        resultSet.getLong("record_doctor_id"),
        resultSet.getString("diagnosis"),
        resultSet.getString("consultation_notes"),
        resultSet.getString("follow_up_notes"));
  }

  private static Appointment readHistoryAppointment(ResultSet resultSet) throws SQLException {
    return new Appointment(
        resultSet.getLong("appointment_id"),
        resultSet.getLong("appointment_patient_id"),
        resultSet.getLong("appointment_doctor_id"),
        SqliteQueries.parseTimestamp(resultSet.getString("starts_at")),
        SqliteQueries.parseTimestamp(resultSet.getString("ends_at")),
        AppointmentStatus.valueOf(resultSet.getString("status")));
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
