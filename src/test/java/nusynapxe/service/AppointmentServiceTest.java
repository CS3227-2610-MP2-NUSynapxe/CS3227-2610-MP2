package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AppointmentServiceTest {
  private static final LocalDateTime APPOINTMENT_START = LocalDateTime.of(2026, 9, 1, 9, 0);
  private static final LocalDateTime APPOINTMENT_END = LocalDateTime.of(2026, 9, 1, 9, 30);
  private static final Clock AFTER_APPOINTMENT =
      Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneId.of("UTC"));

  @TempDir private Path temporaryDirectory;

  @Test
  void receptionistBooksForAnyDoctorAndDoctorOwnsScheduleActions() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      AppointmentService service = service(database);

      Appointment appointment =
          service.book(
              fixture.receptionistSession(),
              fixture.patient().id(),
              fixture.doctor().id(),
              APPOINTMENT_START,
              APPOINTMENT_END);

      assertEquals(
          appointment, service.schedule(fixture.doctorSession(), fixture.doctor().id()).get(0));
      assertEquals(1, service.allAppointments(fixture.receptionistSession()).size());
      assertThrows(
          AuthorizationException.class,
          () -> service.schedule(fixture.otherDoctorSession(), fixture.doctor().id()));
      assertThrows(
          AuthorizationException.class,
          () -> service.accept(fixture.otherDoctorSession(), appointment.id()));
    }
  }

  @Test
  void rejectsOverlappingAppointmentsAndTimeOffButAllowsAdjacentSlots() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      AppointmentService service = service(database);
      service.book(
          fixture.receptionistSession(),
          fixture.patient().id(),
          fixture.doctor().id(),
          APPOINTMENT_START,
          APPOINTMENT_END);

      assertThrows(
          ValidationException.class,
          () ->
              service.book(
                  fixture.receptionistSession(),
                  fixture.patient().id(),
                  fixture.doctor().id(),
                  LocalDateTime.of(2026, 9, 1, 9, 15),
                  LocalDateTime.of(2026, 9, 1, 9, 45)));
      Appointment adjacent =
          service.book(
              fixture.receptionistSession(),
              fixture.patient().id(),
              fixture.doctor().id(),
              APPOINTMENT_END,
              LocalDateTime.of(2026, 9, 1, 10, 0));
      assertEquals(AppointmentStatus.PENDING, adjacent.status());

      AppointmentService otherService = service(database);
      assertThrows(
          ValidationException.class,
          () ->
              otherService.blockTimeOff(
                  fixture.doctorSession(),
                  LocalDateTime.of(2026, 9, 1, 9, 45),
                  LocalDateTime.of(2026, 9, 1, 10, 15)));
    }
  }

  @Test
  void reschedulesAndCancelsOnlyAllowedAppointments() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      AppointmentService service = service(database);
      Appointment appointment =
          service.book(
              fixture.receptionistSession(),
              fixture.patient().id(),
              fixture.doctor().id(),
              APPOINTMENT_START,
              APPOINTMENT_END);

      Appointment rescheduled =
          service.reschedule(
              fixture.doctorSession(),
              appointment.id(),
              LocalDateTime.of(2026, 9, 1, 11, 0),
              LocalDateTime.of(2026, 9, 1, 11, 30));
      assertEquals(LocalDateTime.of(2026, 9, 1, 11, 0), rescheduled.startsAt());
      assertEquals(
          AppointmentStatus.CANCELLED,
          service.cancel(fixture.receptionistSession(), appointment.id()).status());
      assertThrows(
          ValidationException.class,
          () ->
              service.reschedule(
                  fixture.receptionistSession(),
                  appointment.id(),
                  APPOINTMENT_START,
                  APPOINTMENT_END));
    }
  }

  @Test
  void appliesWorkflowStateTransitionsAndCheckInTiming() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      AppointmentService service = service(database, AFTER_APPOINTMENT);
      Appointment appointment =
          service.book(
              fixture.receptionistSession(),
              fixture.patient().id(),
              fixture.doctor().id(),
              APPOINTMENT_START,
              APPOINTMENT_END);

      appointment = service.accept(fixture.doctorSession(), appointment.id());
      appointment = service.checkIn(fixture.receptionistSession(), appointment.id());
      appointment = service.complete(fixture.doctorSession(), appointment.id());
      assertEquals(AppointmentStatus.COMPLETED, appointment.status());
      long completedAppointmentId = appointment.id();
      assertThrows(
          ValidationException.class,
          () -> service.checkIn(fixture.receptionistSession(), completedAppointmentId));
      assertThrows(
          ValidationException.class,
          () ->
              AppointmentTransitions.requireAllowed(
                  AppointmentStatus.COMPLETED, AppointmentStatus.ACCEPTED));
    }
  }

  private AppointmentService service(SqliteDatabase database) {
    return service(database, AFTER_APPOINTMENT);
  }

  private AppointmentService service(SqliteDatabase database, Clock clock) {
    return new AppointmentService(
        new AppointmentRepository(database),
        new AccountRepository(database),
        new PatientRepository(database),
        clock);
  }

  private Accounts accounts(SqliteDatabase database) throws SQLException {
    AccountRepository repository = new AccountRepository(database);
    Account doctor =
        repository.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
    Account otherDoctor =
        repository.create("other", "Dr. Babbage", Role.DOCTOR, new byte[] {3}, new byte[] {4});
    Account receptionist =
        repository.create(
            "reception", "Reception", Role.RECEPTIONIST, new byte[] {5}, new byte[] {6});
    Patient patient =
        new PatientRepository(database)
            .create(
                new Patient(
                    0,
                    "Grace",
                    "Hopper",
                    "1906-12-09",
                    "555-0100",
                    "grace@example.test",
                    "Address"));
    return new Accounts(
        doctor,
        otherDoctor,
        receptionist,
        patient,
        new Session(doctor.id(), doctor.username(), doctor.role()),
        new Session(otherDoctor.id(), otherDoctor.username(), otherDoctor.role()),
        new Session(receptionist.id(), receptionist.username(), receptionist.role()));
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("appointments.db"));
    database.open();
    return database;
  }

  private record Accounts(
      Account doctor,
      Account otherDoctor,
      Account receptionist,
      Patient patient,
      Session doctorSession,
      Session otherDoctorSession,
      Session receptionistSession) {
    // Shared fixture for appointment authorization tests.
  }
}
