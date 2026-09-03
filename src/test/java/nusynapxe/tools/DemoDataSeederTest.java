package nusynapxe.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import nusynapxe.domain.Account;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DemoDataSeederTest {
  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Singapore");

  @TempDir private Path temporaryDirectory;

  @Test
  void seedsAccountsPatientsCalendarSettingsAndFutureSchedule() throws SQLException {
    Path databasePath = temporaryDirectory.resolve("demo.db");

    DemoDataSeeder.SeedSummary summary = DemoDataSeeder.seed(databasePath);

    assertEquals(new DemoDataSeeder.SeedSummary(4, 6, 44), summary);
    try (SqliteDatabase database = openDatabase(databasePath)) {
      AccountRepository accounts = new AccountRepository(database);
      PatientRepository patients = new PatientRepository(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      assertEquals(4, accounts.findAll().size());
      assertEquals(6, patients.findAll().size());
      assertEquals(44, appointments.findAll().size());

      AuthenticationService authentication = new AuthenticationService(accounts);
      assertTrue(authentication.login("admin.demo", "DemoAdmin123!".toCharArray()).isPresent());
      assertTrue(authentication.login("doctor.ada", "DemoDoctor123!".toCharArray()).isPresent());
      assertTrue(
          authentication.login("reception.demo", "DemoReception123!".toCharArray()).isPresent());

      Account ada = accounts.findCredentials("doctor.ada").orElseThrow().account();
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
          appointments.findCalendarPageByDoctor(
              ada.id(), LocalDate.now(CLINIC_ZONE).atStartOfDay(), null, 25);
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
}
