package nusynapxe.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;

/** Applies role-specific access rules to patient information. */
public final class PatientService {
  private final PatientRepository patients;
  private final AppointmentRepository appointments;
  private final ClinicalRecordRepository clinicalRecords;

  /** Creates a patient service with its persistence collaborators. */
  public PatientService(
      PatientRepository patients,
      AppointmentRepository appointments,
      ClinicalRecordRepository clinicalRecords) {
    this.patients = Objects.requireNonNull(patients, "patients");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.clinicalRecords = Objects.requireNonNull(clinicalRecords, "clinicalRecords");
  }

  /** Registers a patient using the receptionist's administrative fields. */
  public Patient register(Session actor, Patient patient) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return patients.create(validate(patient));
  }

  /** Updates only a patient's administrative information. */
  public Patient updateAdministrative(Session actor, Patient patient) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return patients.update(validate(patient));
  }

  /** Returns administrative patient information to reception staff. */
  public List<Patient> listAdministrative(Session actor) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return patients.findAll();
  }

  /** Returns administrative information for a receptionist. */
  public Patient getAdministrative(Session actor, long patientId) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return patients
        .findById(patientId)
        .orElseThrow(() -> new ValidationException("Patient does not exist"));
  }

  /** Returns the clinical record for an appointment owned by the Doctor. */
  public Optional<ClinicalRecord> findClinicalForDoctor(Session actor, long appointmentId)
      throws SQLException {
    Appointment appointment = appointment(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    return clinicalRecords.findByAppointment(appointmentId);
  }

  private Appointment appointment(long appointmentId) throws SQLException {
    return appointments
        .findById(appointmentId)
        .orElseThrow(() -> new ValidationException("Appointment does not exist"));
  }

  private static Patient validate(Patient patient) {
    Objects.requireNonNull(patient, "patient");
    return new Patient(
        patient.id(),
        required(patient.firstName(), "First name"),
        required(patient.lastName(), "Last name"),
        optional(patient.dateOfBirth()),
        optional(patient.phone()),
        optional(patient.email()),
        optional(patient.address()),
        optional(patient.billingInformation()));
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
