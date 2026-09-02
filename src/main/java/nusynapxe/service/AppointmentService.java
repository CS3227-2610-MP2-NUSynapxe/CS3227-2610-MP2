package nusynapxe.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;

/** Applies role and lifecycle rules to clinic appointments. */
public final class AppointmentService {
  private final AppointmentRepository appointments;
  private final AccountRepository accounts;
  private final PatientRepository patients;
  private final Clock clock;

  /** Creates an appointment service using the system clock. */
  public AppointmentService(
      AppointmentRepository appointments, AccountRepository accounts, PatientRepository patients) {
    this(appointments, accounts, patients, Clock.systemDefaultZone());
  }

  AppointmentService(
      AppointmentRepository appointments,
      AccountRepository accounts,
      PatientRepository patients,
      Clock clock) {
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.accounts = Objects.requireNonNull(accounts, "accounts");
    this.patients = Objects.requireNonNull(patients, "patients");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Books a pending appointment for any Doctor as a Receptionist. */
  public Appointment book(
      Session actor, long patientId, long doctorId, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    requirePatient(patientId);
    requireDoctor(doctorId);
    return persist(
        () ->
            appointments.create(patientId, doctorId, startsAt, endsAt, AppointmentStatus.PENDING));
  }

  /** Returns a doctor's schedule to that Doctor or to a Receptionist. */
  public List<Appointment> schedule(Session actor, long doctorId) throws SQLException {
    if (actor == null) {
      throw new AuthorizationException("Authentication is required");
    }
    if (actor.role() == Role.DOCTOR) {
      Authorization.requireDoctorOwnership(actor, doctorId);
    } else {
      Authorization.requireRole(actor, Role.RECEPTIONIST);
    }
    requireDoctor(doctorId);
    return appointments.findByDoctor(doctorId);
  }

  /** Returns all appointments to a Receptionist. */
  public List<Appointment> allAppointments(Session actor) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return appointments.findAll();
  }

  /** Returns Receptionist-visible appointments matching optional dashboard filters. */
  public List<Appointment> searchAppointments(
      Session actor, LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (doctorId != null) {
      requireDoctor(doctorId);
    }
    return appointments.search(date, doctorId, patientQuery, status);
  }

  /** Accepts a pending appointment as its assigned Doctor. */
  public Appointment accept(Session actor, long appointmentId) throws SQLException {
    Appointment appointment = appointment(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    AppointmentTransitions.requireAllowed(appointment.status(), AppointmentStatus.ACCEPTED);
    return appointments.updateStatus(appointmentId, AppointmentStatus.ACCEPTED);
  }

  /** Reschedules an appointment as a Receptionist or its assigned Doctor. */
  public Appointment reschedule(
      Session actor, long appointmentId, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    Appointment appointment = appointment(appointmentId);
    requireScheduleOwnerOrReceptionist(actor, appointment.doctorId());
    if (appointment.status() != AppointmentStatus.PENDING
        && appointment.status() != AppointmentStatus.ACCEPTED) {
      throw new ValidationException("Only pending or accepted appointments can be rescheduled");
    }
    return persist(() -> appointments.reschedule(appointmentId, startsAt, endsAt));
  }

  /** Cancels a pending or accepted appointment as a Receptionist or its Doctor. */
  public Appointment cancel(Session actor, long appointmentId) throws SQLException {
    Appointment appointment = appointment(appointmentId);
    requireScheduleOwnerOrReceptionist(actor, appointment.doctorId());
    AppointmentTransitions.requireAllowed(appointment.status(), AppointmentStatus.CANCELLED);
    return appointments.updateStatus(appointmentId, AppointmentStatus.CANCELLED);
  }

  /** Checks in an accepted appointment as a Receptionist at or after its start time. */
  public Appointment checkIn(Session actor, long appointmentId) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    Appointment appointment = appointment(appointmentId);
    AppointmentTransitions.requireAllowed(appointment.status(), AppointmentStatus.CHECKED_IN);
    if (LocalDateTime.now(clock).isBefore(appointment.startsAt())) {
      throw new ValidationException("An appointment cannot be checked in before its start time");
    }
    return appointments.updateStatus(appointmentId, AppointmentStatus.CHECKED_IN);
  }

  /** Completes a checked-in appointment as its assigned Doctor. */
  public Appointment complete(Session actor, long appointmentId) throws SQLException {
    Appointment appointment = appointment(appointmentId);
    Authorization.requireDoctorOwnership(actor, appointment.doctorId());
    AppointmentTransitions.requireAllowed(appointment.status(), AppointmentStatus.COMPLETED);
    return appointments.updateStatus(appointmentId, AppointmentStatus.COMPLETED);
  }

  /** Blocks a doctor's available time as that Doctor. */
  public DoctorTimeOff blockTimeOff(Session actor, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    Authorization.requireRole(actor, Role.DOCTOR);
    return persist(() -> appointments.createTimeOff(actor.accountId(), startsAt, endsAt));
  }

  /** Finds an appointment or reports a user-safe validation failure. */
  public Appointment get(long appointmentId) throws SQLException {
    return appointment(appointmentId);
  }

  private Appointment appointment(long appointmentId) throws SQLException {
    return appointments
        .findById(appointmentId)
        .orElseThrow(() -> new ValidationException("Appointment does not exist"));
  }

  private void requireDoctor(long doctorId) throws SQLException {
    Account account =
        accounts
            .findById(doctorId)
            .orElseThrow(() -> new ValidationException("Doctor does not exist"));
    if (account.role() != Role.DOCTOR) {
      throw new ValidationException("The selected account is not a Doctor");
    }
  }

  private void requirePatient(long patientId) throws SQLException {
    Patient patient =
        patients
            .findById(patientId)
            .orElseThrow(() -> new ValidationException("Patient does not exist"));
    if (!patient.active()) {
      throw new ValidationException("Inactive patients cannot be scheduled");
    }
  }

  private static void requireScheduleOwnerOrReceptionist(Session actor, long doctorId) {
    if (actor == null) {
      throw new AuthorizationException("Authentication is required");
    }
    if (actor.role() == Role.DOCTOR) {
      Authorization.requireDoctorOwnership(actor, doctorId);
    } else {
      Authorization.requireRole(actor, Role.RECEPTIONIST);
    }
  }

  private static <T> T persist(SqliteOperation<T> operation) throws SQLException {
    try {
      return operation.run();
    } catch (SQLException exception) {
      if (exception.getMessage() != null
          && exception.getMessage().contains("schedule has a conflict")) {
        throw new ValidationException("The doctor's schedule has a conflict", exception);
      }
      throw exception;
    }
  }

  @FunctionalInterface
  private interface SqliteOperation<T> {
    T run() throws SQLException;
  }
}
