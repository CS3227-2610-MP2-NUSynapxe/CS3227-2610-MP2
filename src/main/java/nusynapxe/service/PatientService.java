package nusynapxe.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.IdentityType;
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

  private static final String PATIENT_NOT_FOUND_MESSAGE = "Patient does not exist";
  private static final Pattern PHONE_COUNTRY_CODE_PATTERN = Pattern.compile("^[1-9][0-9]{0,2}$");
  private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^[0-9]+$");
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
  private static final Pattern NRIC_PATTERN = Pattern.compile("^[ST][0-9]{7}[A-Z]$");
  private static final Pattern FIN_PATTERN = Pattern.compile("^[FGM][0-9]{7}[A-Z]$");
  private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z0-9]{5,20}$");
  private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");
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
      throw new ValidationException(PATIENT_NOT_FOUND_MESSAGE);
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
      throw new ValidationException(PATIENT_NOT_FOUND_MESSAGE);
    }
    return patients.deactivate(patientId);
  }

  /** Reactivates a patient without changing the Patient ID or retained history. */
  public Patient activateAdministrative(Session actor, long patientId) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (patientId <= 0 || patients.findById(patientId).isEmpty()) {
      throw new ValidationException(PATIENT_NOT_FOUND_MESSAGE);
    }
    return patients.activate(patientId);
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
        .orElseThrow(() -> new ValidationException(PATIENT_NOT_FOUND_MESSAGE));
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
    String identityNumber = normalizedRequired(patient.identityNumber(), "Identity number");
    validateIdentityNumber(patient.identityType(), identityNumber);
    String issuingCountry = normalizedRequired(patient.issuingCountry(), "Issuing country");
    if ((patient.identityType() == IdentityType.NRIC || patient.identityType() == IdentityType.FIN)
        && !"SG".equals(issuingCountry)) {
      throw new ValidationException("Issuing country must be Singapore for NRIC and FIN");
    }
    String dateOfBirth = required(patient.dateOfBirth(), "Date of birth");
    LocalDate birthDate;
    try {
      birthDate = LocalDate.parse(dateOfBirth);
    } catch (DateTimeParseException exception) {
      throw new ValidationException("Date of birth must use yyyy-MM-dd", exception);
    }
    if (birthDate.isAfter(LocalDate.now(SINGAPORE_ZONE))) {
      throw new ValidationException("Date of birth cannot be in the future");
    }
    String phoneCountryCode = required(patient.phoneCountryCode(), "Phone country code");
    if (!PHONE_COUNTRY_CODE_PATTERN.matcher(phoneCountryCode).matches()) {
      throw new ValidationException("Phone country code must contain 1 to 3 digits only");
    }
    String phoneNumber = required(patient.phoneNumber(), "Phone number");
    if (!PHONE_NUMBER_PATTERN.matcher(phoneNumber).matches()) {
      throw new ValidationException("Phone number must contain digits only");
    }
    String email = required(patient.email(), "Email");
    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw new ValidationException("Email must contain @ with text before and after it");
    }
    Double height = validateHeight(patient.heightCm());
    Double weight = validateWeight(patient.weightKg());
    return new Patient(
        patient.id(),
        patient.identityType(),
        identityNumber,
        issuingCountry,
        required(patient.firstName(), "First name"),
        required(patient.lastName(), "Last name"),
        dateOfBirth,
        patient.sex(),
        phoneCountryCode,
        phoneNumber,
        email,
        required(patient.address(), "Address"),
        height,
        weight,
        registration || patient.active());
  }

  private static void validateIdentityNumber(IdentityType type, String identityNumber) {
    Pattern pattern =
        switch (type) {
          case NRIC -> NRIC_PATTERN;
          case FIN -> FIN_PATTERN;
          case PASSPORT -> PASSPORT_PATTERN;
          case OTHER -> null;
        };
    if (pattern != null && !pattern.matcher(identityNumber).matches()) {
      String rule =
          switch (type) {
            case NRIC -> "NRIC must start with S or T, followed by 7 digits and a letter";
            case FIN -> "FIN must start with F, G or M, followed by 7 digits and a letter";
            case PASSPORT -> "Passport number must contain 5 to 20 letters or digits";
            case OTHER -> throw new AssertionError("OTHER has no format rule");
          };
      throw new ValidationException(rule);
    }
  }

  private static Double validateHeight(Double value) {
    Double result = positiveMeasurement(value, "Height");
    if (result != null && result.doubleValue() != Math.rint(result.doubleValue())) {
      throw new ValidationException("Height must be a whole number of centimetres");
    }
    return result;
  }

  private static Double validateWeight(Double value) {
    Double result = positiveMeasurement(value, "Weight");
    if (result != null && BigDecimal.valueOf(result).stripTrailingZeros().scale() > 1) {
      throw new ValidationException("Weight must have at most 1 decimal place");
    }
    return result;
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
