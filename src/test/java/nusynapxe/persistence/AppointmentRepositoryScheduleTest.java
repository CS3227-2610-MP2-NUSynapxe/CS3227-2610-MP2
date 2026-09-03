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
      appointments.updateStatus(first.id(), AppointmentStatus.PENDING);
      Appointment third =
          createAppointment(
              appointments,
              patient,
              doctor,
              LocalDateTime.of(2026, 9, 4, 9, 0),
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

      assertEquals(List.of(first.id(), sameStartCancelled.id()), ids(firstPage));
      assertTrue(firstPage.hasMore());
      assertEquals(List.of(third.id()), ids(secondPage));
      assertTrue(!secondPage.hasMore());
      assertEquals(AppointmentStatus.CANCELLED, firstPage.appointments().get(1).status());
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
      for (int index = 0; index < 105; index++) {
        LocalDateTime startsAt = base.plusDays(index);
        createAppointment(
            appointments,
            patient,
            doctor,
            startsAt,
            index % 10 == 0 ? AppointmentStatus.CANCELLED : AppointmentStatus.PENDING);
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
      assertEquals(6, secondIds.size());
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
