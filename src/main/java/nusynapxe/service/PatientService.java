package nusynapxe.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
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
  /** Non-sensitive duplicate feedback shared by registration and updates. */
  public static final String DUPLICATE_IDENTITY_MESSAGE =
      "A patient with this identity document already exists";

  private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]+$");
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

  /** Registers a patient using Receptionist-authorized basic fields. */
  public Patient register(Session actor, Patient requestedPatient) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    Patient patient = validate(requestedPatient, true);
    requireUniqueIdentity(patient, 0);
    try {
      return patients.create(patient);
    } catch (SQLException exception) {
      throw translateDuplicate(exception);
    }
  }

  /** Atomically updates only a patient's non-clinical basic information. */
  public Patient updateAdministrative(Session actor, Patient requestedPatient) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    Objects.requireNonNull(requestedPatient, "patient");
    if (requestedPatient.id() <= 0 || patients.findById(requestedPatient.id()).isEmpty()) {
      throw new ValidationException("Patient does not exist");
    }
    Patient patient = validate(requestedPatient, false);
    requireUniqueIdentity(patient, patient.id());
    try {
      return patients.update(patient);
    } catch (SQLException exception) {
      throw translateDuplicate(exception);
    }
  }

  /** Deactivates a patient without deleting the Patient ID or retained history. */
  public Patient deactivateAdministrative(Session actor, long patientId) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (patientId <= 0 || patients.findById(patientId).isEmpty()) {
      throw new ValidationException("Patient does not exist");
    }
    return patients.deactivate(patientId);
  }

  /** Searches non-clinical patient information for a Receptionist. */
  public List<Patient> searchAdministrative(Session actor, String query) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return patients.search(query);
  }

  /** Returns non-clinical patient information to reception staff. */
  public List<Patient> listAdministrative(Session actor) throws SQLException {
    return searchAdministrative(actor, "");
  }

  /** Returns non-clinical information for a Receptionist. */
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

  private void requireUniqueIdentity(Patient patient, long allowedPatientId) throws SQLException {
    Optional<Patient> existing =
        patients.findByIdentity(
            patient.identityType(), patient.issuingCountry(), patient.identityNumber());
    if (existing.isPresent() && existing.orElseThrow().id() != allowedPatientId) {
      throw new ValidationException(DUPLICATE_IDENTITY_MESSAGE);
    }
  }

  private static Patient validate(Patient patient, boolean registration) {
    Objects.requireNonNull(patient, "patient");
    if (patient.identityType() == null) {
      throw new ValidationException("Identity type is required");
    }
    if (patient.sex() == null) {
      throw new ValidationException("Sex is required");
    }
    String dateOfBirth = required(patient.dateOfBirth(), "Date of birth");
    try {
      LocalDate.parse(dateOfBirth);
    } catch (DateTimeParseException exception) {
      throw new ValidationException("Date of birth must use yyyy-MM-dd", exception);
    }
    String phone = required(patient.phone(), "Phone");
    if (!PHONE_PATTERN.matcher(phone).matches()) {
      throw new ValidationException("Phone must contain an optional leading + followed by digits");
    }
    Double height = positiveMeasurement(patient.heightCm(), "Height");
    Double weight = positiveMeasurement(patient.weightKg(), "Weight");
    return new Patient(
        patient.id(),
        patient.identityType(),
        normalizedRequired(patient.identityNumber(), "Identity number"),
        normalizedRequired(patient.issuingCountry(), "Issuing country"),
        required(patient.firstName(), "First name"),
        required(patient.lastName(), "Last name"),
        dateOfBirth,
        patient.sex(),
        phone,
        optional(patient.email()),
        optional(patient.address()),
        optional(patient.billingInformation()),
        height,
        weight,
        registration || patient.active());
  }

  private static Double positiveMeasurement(Double value, String fieldName) {
    if (value == null) {
      return null;
    }
    if (!Double.isFinite(value) || value <= 0) {
      throw new ValidationException(fieldName + " must be a positive number");
    }
    return value;
  }

  private static String normalizedRequired(String value, String fieldName) {
    return required(value, fieldName).toUpperCase(Locale.ROOT);
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

  private static SQLException translateDuplicate(SQLException exception) {
    if (isUniqueConstraint(exception)) {
      throw new ValidationException(DUPLICATE_IDENTITY_MESSAGE, exception);
    }
    return exception;
  }

  private static boolean isUniqueConstraint(SQLException exception) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains("unique")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
