package nusynapxe.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DemoDataSeederTest {
  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Singapore");

  @TempDir private Path temporaryDirectory;

  @Test
  void seedsAccountsPatientsCalendarSettingsAndRollingScheduleWithClinicalHistory()
      throws SQLException {
    Path databasePath = temporaryDirectory.resolve("demo.db");

    DemoDataSeeder.SeedSummary summary = DemoDataSeeder.seed(databasePath);

    assertEquals(new DemoDataSeeder.SeedSummary(4, 18, 88), summary);
    try (SqliteDatabase database = openDatabase(databasePath)) {
      AccountRepository accounts = new AccountRepository(database);
      PatientRepository patients = new PatientRepository(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      ClinicalRecordRepository clinicalRecords = new ClinicalRecordRepository(database);
      assertEquals(4, accounts.findAll().size());
      assertEquals(18, patients.findAll().size());
      assertTrue(patients.findAll().stream().anyMatch(patient -> !patient.active()));

      List<Appointment> seededAppointments = appointments.findAll();
      assertEquals(88, seededAppointments.size());
      LocalDate today = LocalDate.now(CLINIC_ZONE);
      Account ada = accounts.findCredentials("ada").orElseThrow().account();
      Account grace = accounts.findCredentials("grace").orElseThrow().account();
      assertEquals(
          today.minusDays(7),
          seededAppointments.stream()
              .map(appointment -> appointment.startsAt().toLocalDate())
              .min(LocalDate::compareTo)
              .orElseThrow());
      assertEquals(
          today.plusDays(14),
          seededAppointments.stream()
              .map(appointment -> appointment.startsAt().toLocalDate())
              .max(LocalDate::compareTo)
              .orElseThrow());
      Set<LocalDate> scheduledDates = new HashSet<>();
      Set<LocalDate> adaDates = new HashSet<>();
      Set<LocalDate> graceDates = new HashSet<>();
      Set<AppointmentStatus> statuses = EnumSet.noneOf(AppointmentStatus.class);
      int historicalConsultations = 0;
      int historicalPrescriptions = 0;
      for (Appointment appointment : seededAppointments) {
        scheduledDates.add(appointment.startsAt().toLocalDate());
        if (appointment.doctorId() == ada.id()) {
          adaDates.add(appointment.startsAt().toLocalDate());
        }
        if (appointment.doctorId() == grace.id()) {
          graceDates.add(appointment.startsAt().toLocalDate());
        }
        statuses.add(appointment.status());
        if (appointment.startsAt().toLocalDate().isBefore(today)
            && isConsultationStatus(appointment.status())) {
          ClinicalRecord record = clinicalRecords.findByAppointment(appointment.id()).orElseThrow();
          assertEquals(appointment.patientId(), record.patientId());
          assertEquals(appointment.doctorId(), record.doctorId());
          historicalConsultations++;
          if (appointment.status() == AppointmentStatus.COMPLETED
              || appointment.status() == AppointmentStatus.CHECKED_OUT) {
            assertTrue(clinicalRecords.findPrescriptions(record.id()).size() >= 1);
            historicalPrescriptions++;
          }
        } else {
          assertTrue(clinicalRecords.findByAppointment(appointment.id()).isEmpty());
        }
      }
      assertEquals(
          Set.of(
              today.minusDays(7),
              today.minusDays(6),
              today.minusDays(5),
              today.minusDays(4),
              today.minusDays(3),
              today.minusDays(2),
              today.minusDays(1),
              today,
              today.plusDays(1),
              today.plusDays(2),
              today.plusDays(3),
              today.plusDays(4),
              today.plusDays(5),
              today.plusDays(6),
              today.plusDays(7),
              today.plusDays(8),
              today.plusDays(9),
              today.plusDays(10),
              today.plusDays(11),
              today.plusDays(12),
              today.plusDays(13),
              today.plusDays(14)),
          scheduledDates);
      assertEquals(scheduledDates, adaDates);
      assertEquals(scheduledDates, graceDates);
      assertEquals(EnumSet.allOf(AppointmentStatus.class), statuses);
      assertTrue(historicalConsultations > 0);
      assertTrue(historicalPrescriptions > 0);

      AuthenticationService authentication = new AuthenticationService(accounts);
      assertTrue(authentication.login("admin.demo", "DemoAdmin123!".toCharArray()).isPresent());
      assertTrue(authentication.login("ada", "ada1234!".toCharArray()).isPresent());
      assertTrue(authentication.login("grace", "grace123!".toCharArray()).isPresent());
      assertTrue(authentication.login("reception", "recept123!".toCharArray()).isPresent());

      DoctorCalendarSettings settings =
          new CalendarSettingsRepository(database).findByDoctor(ada.id()).orElseThrow();
      assertEquals(DayOfWeek.MONDAY, settings.firstDayOfWeek());
      assertEquals(
          List.of(
              new nusynapxe.domain.WorkingInterval(480, 720),
              new nusynapxe.domain.WorkingInterval(780, 1080)),
          settings.intervals(DayOfWeek.MONDAY));
      assertTrue(settings.intervals(DayOfWeek.SATURDAY).isEmpty());

      CalendarSchedulePage page =
          appointments.findCalendarPageByDoctor(ada.id(), today.atStartOfDay(), null, 25);
      assertEquals(25, page.appointments().size());
      assertTrue(page.hasMore());
    }
  }

  @Test
  void refusesToSeedNonEmptyDatabaseAndResetRestoresEmptySchema() throws SQLException {
    Path databasePath = temporaryDirectory.resolve("demo.db");
    DemoDataSeeder.seed(databasePath);

    assertThrows(IllegalStateException.class, () -> DemoDataSeeder.seed(databasePath));

    DemoDataSeeder.reset(databasePath);
    try (SqliteDatabase database = openDatabase(databasePath)) {
      assertTrue(new AccountRepository(database).findAll().isEmpty());
      assertTrue(new PatientRepository(database).findAll().isEmpty());
      assertTrue(new AppointmentRepository(database).findAll().isEmpty());
    }
  }

  private static SqliteDatabase openDatabase(Path path) throws SQLException {
    SqliteDatabase database = new SqliteDatabase(path);
    database.open();
    return database;
  }

  private static boolean isConsultationStatus(AppointmentStatus status) {
    return status == AppointmentStatus.CHECKED_IN
        || status == AppointmentStatus.COMPLETED
        || status == AppointmentStatus.CHECKED_OUT;
  }
}
