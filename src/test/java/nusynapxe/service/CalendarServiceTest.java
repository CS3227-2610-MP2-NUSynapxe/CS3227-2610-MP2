package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import nusynapxe.domain.Account;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CalendarServiceTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void returnsOnlyTheSignedInDoctorsAdministrativeWeekProjection() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      appointments.create(
          fixture.patientId(),
          fixture.doctor().id(),
          LocalDateTime.of(2026, 9, 6, 9, 0),
          LocalDateTime.of(2026, 9, 6, 9, 30),
          AppointmentStatus.CANCELLED);
      appointments.create(
          fixture.patientId(),
          fixture.otherDoctor().id(),
          LocalDateTime.of(2026, 9, 6, 10, 0),
          LocalDateTime.of(2026, 9, 6, 10, 30),
          AppointmentStatus.ACCEPTED);

      CalendarService service = ClinicServices.forDatabase(database).calendarService();
      var week = service.getWeek(fixture.doctorSession(), LocalDate.of(2026, 9, 6));

      assertEquals(1, week.appointments().size());
      assertEquals(AppointmentStatus.CANCELLED, week.appointments().get(0).status());
      assertTrue(week.appointments().get(0).patientDisplayName().contains("Grace Hopper"));
      assertEquals(0, week.settings().intervals(DayOfWeek.SUNDAY).size());
    }
  }

  @Test
  void savesOwnPreferencesAndRejectsCrossDoctorSnapshots() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      CalendarService service = ClinicServices.forDatabase(database).calendarService();
      DoctorCalendarSettings settings = customSettings(fixture.doctor().id());

      assertEquals(settings, service.saveSettings(fixture.doctorSession(), settings));
      assertEquals(settings, service.getSettings(fixture.doctorSession()));
      assertThrows(
          AuthorizationException.class,
          () ->
              service.saveSettings(
                  fixture.doctorSession(), customSettings(fixture.otherDoctor().id())));
      assertThrows(
          AuthorizationException.class, () -> service.getSettings(fixture.receptionistSession()));
    }
  }

  @Test
  void workingHoursDoNotBlockOutsideHoursAppointments() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Accounts fixture = accounts(database);
      ClinicServices services = ClinicServices.forDatabase(database);
      CalendarService calendar = services.calendarService();
      calendar.saveSettings(fixture.doctorSession(), customSettings(fixture.doctor().id()));

      var appointment =
          services
              .appointmentService()
              .book(
                  fixture.receptionistSession(),
                  fixture.patientId(),
                  fixture.doctor().id(),
                  LocalDateTime.of(2026, 9, 7, 22, 0),
                  LocalDateTime.of(2026, 9, 7, 22, 30));

      assertEquals(
          appointment.id(),
          calendar
              .getWeek(fixture.doctorSession(), LocalDate.of(2026, 9, 7))
              .appointments()
              .get(0)
              .appointmentId());
    }
  }

  private DoctorCalendarSettings customSettings(long doctorId) {
    EnumMap<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    intervals.put(
        DayOfWeek.MONDAY, List.of(new WorkingInterval(480, 720), new WorkingInterval(780, 1080)));
    return new DoctorCalendarSettings(doctorId, DayOfWeek.MONDAY, intervals);
  }

  private Accounts accounts(SqliteDatabase database) throws SQLException {
    AccountRepository accounts = new AccountRepository(database);
    Account doctor =
        accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
    Account otherDoctor =
        accounts.create("other", "Dr. Babbage", Role.DOCTOR, new byte[] {3}, new byte[] {4});
    Account receptionist =
        accounts.create(
            "reception", "Reception", Role.RECEPTIONIST, new byte[] {5}, new byte[] {6});
    long patientId =
        new PatientRepository(database)
            .create(new nusynapxe.domain.Patient(0, "Grace", "Hopper", "1906-12-09", "555", "", ""))
            .id();
    return new Accounts(
        doctor,
        otherDoctor,
        receptionist,
        patientId,
        new Session(doctor.id(), doctor.username(), Role.DOCTOR),
        new Session(otherDoctor.id(), otherDoctor.username(), Role.DOCTOR),
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST));
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("calendar-service.db"));
    database.open();
    return database;
  }

  private record Accounts(
      Account doctor,
      Account otherDoctor,
      Account receptionist,
      long patientId,
      Session doctorSession,
      Session otherDoctorSession,
      Session receptionistSession) {
    // Test fixture value.
  }
}
