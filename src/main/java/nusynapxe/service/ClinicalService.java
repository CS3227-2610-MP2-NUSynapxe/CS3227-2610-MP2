package nusynapxe.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;

/** Applies Doctor ownership and validation rules to clinical consultations. */
public final class ClinicalService {
  private final AppointmentRepository appointments;
  private final ClinicalRecordRepository clinicalRecords;

  /**
   * Creates a clinical service with appointment and clinical persistence.
   *
   * @param appointments repository used to validate appointment ownership and state
   * @param clinicalRecords repository used for consultation data
   * @throws NullPointerException if a repository is {@code null}
   */
  public ClinicalService(
      AppointmentRepository appointments, ClinicalRecordRepository clinicalRecords) {
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.clinicalRecords = Objects.requireNonNull(clinicalRecords, "clinicalRecords");
  }

  /**
   * Returns an assigned Doctor's clinical record for an appointment.
   *
   * @param actor assigned Doctor session
   * @param appointmentId appointment identifier
   * @return the clinical record, or empty when consultation notes have not been saved
   * @throws AuthorizationException if the actor is not the assigned Doctor
   * @throws SQLException if the appointment or clinical record query fails
   * @throws ValidationException if the appointment does not exist
   */
  public Optional<ClinicalRecord> findForDoctor(Session actor, long appointmentId)
      throws SQLException {
    Appointment appointment = appointment(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    return clinicalRecords.findByAppointment(appointmentId);
  }

  /**
   * Saves consultation notes for the assigned Doctor.
   *
   * @param actor assigned Doctor session
   * @param appointmentId appointment identifier
   * @param diagnosis required diagnosis text
   * @param consultationNotes required consultation notes
   * @param followUpNotes optional follow-up notes
   * @return the persisted clinical record
   * @throws AuthorizationException if the actor is not the assigned Doctor
   * @throws SQLException if the record cannot be saved
   * @throws ValidationException if the appointment state or required text is invalid
   */
  public ClinicalRecord saveConsultation(
      Session actor,
      long appointmentId,
      String diagnosis,
      String consultationNotes,
      String followUpNotes)
      throws SQLException {
    Appointment appointment = checkedInOrLater(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    String validDiagnosis = required(diagnosis, "Diagnosis");
    String validNotes = required(consultationNotes, "Consultation notes");
    String validFollowUp = optional(followUpNotes);
    return clinicalRecords.save(
        new ClinicalRecord(
            0,
            appointment.patientId(),
            appointment.id(),
            appointment.doctorId(),
            validDiagnosis,
            validNotes,
            validFollowUp));
  }

  /**
   * Adds a validated prescription to an assigned Doctor's consultation.
   *
   * @param actor assigned Doctor session
   * @param appointmentId appointment identifier
   * @param medication medication name
   * @param dosage dosage instructions
   * @param frequency administration frequency
   * @param duration treatment duration
   * @param instructions additional usage instructions
   * @return the persisted prescription
   * @throws AuthorizationException if the actor is not the assigned Doctor
   * @throws SQLException if the prescription cannot be saved
   * @throws ValidationException if the appointment state, consultation, or input is invalid
   */
  public Prescription addPrescription(
      Session actor,
      long appointmentId,
      String medication,
      String dosage,
      String frequency,
      String duration,
      String instructions)
      throws SQLException {
    Appointment appointment = checkedInOrLater(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    ClinicalRecord record =
        clinicalRecords
            .findByAppointment(appointmentId)
            .orElseThrow(
                () -> new ValidationException("Save consultation notes before a prescription"));
    Prescription prescription =
        new Prescription(
            0,
            record.id(),
            required(medication, "Medication"),
            required(dosage, "Dosage"),
            required(frequency, "Frequency"),
            required(duration, "Duration"),
            required(instructions, "Instructions"));
    return clinicalRecords.addPrescription(prescription);
  }

  /**
   * Lists prescriptions for an assigned Doctor's consultation.
   *
   * @param actor assigned Doctor session
   * @param appointmentId appointment identifier
   * @return immutable prescription list
   * @throws AuthorizationException if the actor is not the assigned Doctor
   * @throws SQLException if the appointment or prescription query fails
   * @throws ValidationException if the appointment or clinical record does not exist
   */
  public List<Prescription> prescriptionsForDoctor(Session actor, long appointmentId)
      throws SQLException {
    Appointment appointment = appointment(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    ClinicalRecord record =
        clinicalRecords
            .findByAppointment(appointmentId)
            .orElseThrow(() -> new ValidationException("Clinical record does not exist"));
    return clinicalRecords.findPrescriptions(record.id());
  }

  private Appointment checkedInOrLater(long appointmentId) throws SQLException {
    Appointment appointment = appointment(appointmentId);
    if (appointment.status() != AppointmentStatus.CHECKED_IN
        && appointment.status() != AppointmentStatus.COMPLETED
        && appointment.status() != AppointmentStatus.CHECKED_OUT) {
      throw new ValidationException("Clinical information requires a checked-in appointment");
    }
    return appointment;
  }

  private Appointment appointment(long appointmentId) throws SQLException {
    return appointments
        .findById(appointmentId)
        .orElseThrow(() -> new ValidationException("Appointment does not exist"));
  }

  private static String required(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(fieldName + " is required");
    }
    return value.trim();
  }

  private static String optional(String value) {
    return value == null ? "" : value.trim();
  }
}
