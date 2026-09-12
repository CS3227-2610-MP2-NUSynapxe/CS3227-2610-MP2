package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AppointmentRepositoryScheduleTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void pagesDoctorScheduleByStartAndIdWithoutDuplicatesOrSkips() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Account anotherDoctor = createAccount(database, "doctor-two", "Dr. Grace", Role.DOCTOR);
      Patient patient = createPatient(database, "Grace", "Hopper");
      AppointmentRepository appointments = new AppointmentRepository(database);

      Appointment first =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 3, 9, 0),
              AppointmentStatus.PENDING);
      appointments.updateStatus(first.id(), AppointmentStatus.CANCELLED);
      Appointment sameStartCancelled =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 3, 9, 0),
              AppointmentStatus.CANCELLED);
      Appointment sameStartDeclined =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 3, 9, 0),
              AppointmentStatus.DECLINED);
      appointments.updateStatus(first.id(), AppointmentStatus.PENDING);
      Appointment third =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 4, 9, 0),
              AppointmentStatus.ACCEPTED);
      Appointment fourth =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 5, 9, 0),
              AppointmentStatus.ACCEPTED);
      createAppointment(
          appointments,
          patient,
          anotherDoctor,
          LocalDateTime.of(2026, 9, 3, 8, 0),
          AppointmentStatus.ACCEPTED);

      CalendarSchedulePage firstPage =
          appointments.findCalendarPageByDoctor(
              doctor.id(), LocalDate.of(2026, 9, 3).atStartOfDay(), null, 2);
      CalendarSchedulePage secondPage =
          appointments.findCalendarPageByDoctor(
              doctor.id(), LocalDate.of(2026, 9, 3).atStartOfDay(), firstPage.nextCursor(), 2);

      assertEquals(List.of(first.id(), third.id()), ids(firstPage));
      assertTrue(firstPage.hasMore());
      assertEquals(List.of(fourth.id()), ids(secondPage));
      assertTrue(!secondPage.hasMore());
      assertEquals(AppointmentStatus.PENDING, firstPage.appointments().get(0).status());
      assertTrue(
          firstPage.appointments().stream()
              .noneMatch(
                  appointment ->
                      appointment.appointmentId() == sameStartCancelled.id()
                          || appointment.appointmentId() == sameStartDeclined.id()));
    }
  }

  @Test
  void anchorIsInclusiveAndInvalidPageSizesAreRejected() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Patient patient = createPatient(database, "Grace", "Hopper");
      AppointmentRepository appointments = new AppointmentRepository(database);
      Appointment appointment =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 3, 9, 0),
              AppointmentStatus.PENDING);

      CalendarSchedulePage page =
          appointments.findCalendarPageByDoctor(
              doctor.id(), appointment.startsAt(), null, CalendarSchedulePage.DEFAULT_PAGE_SIZE);

      assertEquals(List.of(appointment.id()), ids(page));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              appointments.findCalendarPageByDoctor(doctor.id(), appointment.startsAt(), null, 0));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              appointments.findCalendarPageByDoctor(
                  doctor.id(),
                  appointment.startsAt(),
                  new CalendarScheduleCursor(appointment.startsAt(), 0),
                  1));

      CalendarSchedulePage emptyPage =
          appointments.findCalendarPageByDoctor(
              doctor.id(), LocalDate.of(2030, 1, 1).atStartOfDay(), null, 1);
      assertTrue(emptyPage.appointments().isEmpty());
      assertTrue(!emptyPage.hasMore());
    }
  }

  @Test
  void largeSparsePagesIncludeLaterInsertsWithoutRepeatingRows() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Patient patient = createPatient(database, "Grace", "Hopper");
      AppointmentRepository appointments = new AppointmentRepository(database);
      LocalDateTime base = LocalDateTime.of(2026, 9, 3, 9, 0);
      for (int index = 0; index < 110; index++) {
        LocalDateTime startsAt = base.plusDays(index);
        createAppointment(
            appointments,
            patient,
            doctor,
            startsAt,
            index == 0
                ? AppointmentStatus.CANCELLED
                : index == 1 ? AppointmentStatus.DECLINED : AppointmentStatus.PENDING);
      }

      CalendarSchedulePage firstPage =
          appointments.findCalendarPageByDoctor(
              doctor.id(), base, null, CalendarSchedulePage.MAX_PAGE_SIZE);
      Appointment laterInsert =
          createAppointment(
              appointments, patient, doctor, base.plusDays(200), AppointmentStatus.ACCEPTED);
      CalendarSchedulePage secondPage =
          appointments.findCalendarPageByDoctor(
              doctor.id(), base, firstPage.nextCursor(), CalendarSchedulePage.MAX_PAGE_SIZE);

      List<Long> firstIds = ids(firstPage);
      List<Long> secondIds = ids(secondPage);
      Set<Long> allIds = new HashSet<>(firstIds);
      allIds.addAll(secondIds);
      assertEquals(CalendarSchedulePage.MAX_PAGE_SIZE, firstIds.size());
      assertTrue(firstPage.hasMore());
      assertEquals(9, secondIds.size());
      assertTrue(secondIds.contains(laterInsert.id()));
      assertEquals(firstIds.size() + secondIds.size(), allIds.size());
      assertTrue(
          appointments
              .findCalendarPageByDoctor(
                  doctor.id(), base.plusDays(300), null, CalendarSchedulePage.DEFAULT_PAGE_SIZE)
              .appointments()
              .isEmpty());
    }
  }

  @Test
  void unopenedDatabaseFailureIsPropagatedToTheScheduleCaller() throws SQLException {
    try (SqliteDatabase database =
        new SqliteDatabase(temporaryDirectory.resolve("unopened-clinic.db"))) {
      AppointmentRepository appointments = new AppointmentRepository(database);
      assertThrows(
          IllegalStateException.class,
          () ->
              appointments.findCalendarPageByDoctor(
                  1, LocalDate.of(2026, 9, 3).atStartOfDay(), null, 1));
    }
  }

  @Test
  void declinedAndCancelledAppointmentsReleaseTheirIntervals() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Patient patient = createPatient(database, "Grace", "Hopper");
      AppointmentRepository appointments = new AppointmentRepository(database);
      LocalDateTime start = LocalDateTime.of(2026, 9, 3, 9, 0);

      Appointment declined =
          createAppointment(appointments, patient, doctor, start, AppointmentStatus.PENDING);
      appointments.updateStatus(declined.id(), AppointmentStatus.DECLINED);
      Appointment replacement =
          createAppointment(appointments, patient, doctor, start, AppointmentStatus.PENDING);
      appointments.updateStatus(replacement.id(), AppointmentStatus.CANCELLED);

      assertEquals(
          AppointmentStatus.PENDING,
          createAppointment(appointments, patient, doctor, start, AppointmentStatus.PENDING)
              .status());
      assertEquals(
          AppointmentStatus.DECLINED, appointments.findById(declined.id()).orElseThrow().status());
    }
  }

  @Test
  void activeAppointmentStatesBlockBookingReschedulingAndTimeOff() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Patient patient = createPatient(database, "Grace", "Hopper");
      AppointmentRepository appointments = new AppointmentRepository(database);
      List<AppointmentStatus> blockingStatuses =
          List.of(
              AppointmentStatus.PENDING,
              AppointmentStatus.ACCEPTED,
              AppointmentStatus.CHECKED_IN,
              AppointmentStatus.COMPLETED,
              AppointmentStatus.CHECKED_OUT);

      for (int index = 0; index < blockingStatuses.size(); index++) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 4 + index, 9, 0);
        createAppointment(appointments, patient, doctor, start, blockingStatuses.get(index));
        assertThrows(
            SQLException.class,
            () ->
                createAppointment(appointments, patient, doctor, start, AppointmentStatus.PENDING));
        assertThrows(
            SQLException.class,
            () -> appointments.createTimeOff(doctor.id(), start, start.plusMinutes(30)));
      }

      LocalDateTime declinedStart = LocalDateTime.of(2026, 9, 12, 9, 0);
      Appointment declined =
          createAppointment(
              appointments, patient, doctor, declinedStart, AppointmentStatus.DECLINED);
      DoctorTimeOff replacement =
          appointments.createTimeOff(doctor.id(), declinedStart, declinedStart.plusMinutes(30));
      assertEquals(
          AppointmentStatus.DECLINED, appointments.findById(declined.id()).orElseThrow().status());
      assertEquals(declinedStart, replacement.startsAt());
    }
  }

  @Test
  void rescheduleCanReuseDeclinedIntervalAndStillChecksReplacementConflicts() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Patient patient = createPatient(database, "Grace", "Hopper");
      AppointmentRepository appointments = new AppointmentRepository(database);
      LocalDateTime releasedStart = LocalDateTime.of(2026, 9, 3, 9, 0);
      Appointment declined =
          createAppointment(
              appointments, patient, doctor, releasedStart, AppointmentStatus.DECLINED);
      Appointment moving =
          createAppointment(
              appointments, patient, doctor, releasedStart.plusHours(1), AppointmentStatus.PENDING);

      Appointment moved =
          appointments.reschedule(moving.id(), releasedStart, releasedStart.plusMinutes(30));
      assertEquals(releasedStart, moved.startsAt());
      assertEquals(
          AppointmentStatus.DECLINED, appointments.findById(declined.id()).orElseThrow().status());

      Appointment blocker =
          createAppointment(
              appointments,
              patient,
              doctor,
              releasedStart.plusHours(2),
              AppointmentStatus.ACCEPTED);
      assertThrows(
          SQLException.class,
          () -> appointments.reschedule(moved.id(), blocker.startsAt(), blocker.endsAt()));
      assertEquals(releasedStart, appointments.findById(moved.id()).orElseThrow().startsAt());
    }
  }

  @Test
  void readsOverlappingTimeOffByRangeAndDeletesOnlyForItsOwner() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createAccount(database, "doctor", "Dr. Ada", Role.DOCTOR);
      Account otherDoctor = createAccount(database, "other", "Dr. Grace", Role.DOCTOR);
      AppointmentRepository appointments = new AppointmentRepository(database);
      LocalDateTime dayStart = LocalDate.of(2026, 9, 3).atStartOfDay();
      DoctorTimeOff crossing =
          appointments.createTimeOff(doctor.id(), dayStart.minusHours(1), dayStart.plusHours(1));
      appointments.createTimeOff(doctor.id(), dayStart.minusHours(2), dayStart.minusHours(1));
      appointments.createTimeOff(
          doctor.id(), dayStart.plusDays(1), dayStart.plusDays(1).plusHours(1));
      appointments.createTimeOff(otherDoctor.id(), dayStart.plusHours(2), dayStart.plusHours(3));

      assertEquals(
          List.of(crossing),
          appointments.findTimeOffByDoctor(doctor.id(), dayStart, dayStart.plusDays(1)));
      assertTrue(!appointments.deleteTimeOff(crossing.id(), otherDoctor.id()));
      assertTrue(appointments.deleteTimeOff(crossing.id(), doctor.id()));
      assertTrue(
          appointments.findTimeOffByDoctor(doctor.id()).stream()
              .noneMatch(item -> item.id() == crossing.id()));
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clinic.db"));
    database.open();
    return database;
  }

  private static Account createAccount(
      SqliteDatabase database, String username, String displayName, Role role) throws SQLException {
    return new AccountRepository(database)
        .create(username, displayName, role, new byte[] {1}, new byte[] {2});
  }

  private static Patient createPatient(SqliteDatabase database, String firstName, String lastName)
      throws SQLException {
    return new PatientRepository(database)
        .create(
            new Patient(
                0,
                firstName,
                lastName,
                "1906-12-09",
                "555-0100",
                firstName.toLowerCase(Locale.ROOT) + "@example.test",
                "1 Main Street"));
  }

  private static Appointment createAppointment(
      AppointmentRepository appointments,
      Patient patient,
      Account doctor,
      LocalDateTime startsAt,
      AppointmentStatus status)
      throws SQLException {
    return appointments.create(
        patient.id(), doctor.id(), startsAt, startsAt.plusMinutes(30), status);
  }

  private static List<Long> ids(CalendarSchedulePage page) {
    return page.appointments().stream().map(appointment -> appointment.appointmentId()).toList();
  }
}
