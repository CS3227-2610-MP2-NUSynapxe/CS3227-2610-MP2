package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import nusynapxe.domain.Account;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.Role;
import nusynapxe.domain.WorkingInterval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CalendarSettingsRepositoryTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void roundTripsSplitShiftsAndRemovedBreaks() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createDoctor(database, "doctor");
      CalendarSettingsRepository repository = new CalendarSettingsRepository(database);
      DoctorCalendarSettings settings = settings(doctor.id(), true);

      repository.save(settings);

      assertEquals(settings, repository.findByDoctor(doctor.id()).orElseThrow());
      DoctorCalendarSettings withoutBreak = settings(doctor.id(), false);
      repository.save(withoutBreak);
      assertEquals(withoutBreak, repository.findByDoctor(doctor.id()).orElseThrow());
    }
  }

  @Test
  void rollsBackWhenTheDoctorForeignKeyRejectsTheSnapshot() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor = createDoctor(database, "doctor");
      CalendarSettingsRepository repository = new CalendarSettingsRepository(database);
      DoctorCalendarSettings original = settings(doctor.id(), true);
      repository.save(original);

      assertThrows(SQLException.class, () -> repository.save(settings(999, false)));

      assertEquals(original, repository.findByDoctor(doctor.id()).orElseThrow());
    }
  }

  private DoctorCalendarSettings settings(long doctorId, boolean splitShift) {
    EnumMap<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    intervals.put(
        DayOfWeek.MONDAY,
        splitShift
            ? List.of(new WorkingInterval(480, 720), new WorkingInterval(780, 1080))
            : List.of(new WorkingInterval(480, 1080)));
    intervals.put(DayOfWeek.TUESDAY, List.of(new WorkingInterval(480, 1440)));
    return new DoctorCalendarSettings(doctorId, DayOfWeek.MONDAY, intervals);
  }

  private Account createDoctor(SqliteDatabase database, String username) throws SQLException {
    return new AccountRepository(database)
        .create(username, "Dr. " + username, Role.DOCTOR, new byte[] {1}, new byte[] {2});
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("calendar.db"));
    database.open();
    return database;
  }
}
