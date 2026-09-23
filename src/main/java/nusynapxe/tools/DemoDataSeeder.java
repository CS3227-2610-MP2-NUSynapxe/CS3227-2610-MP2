package nusynapxe.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import nusynapxe.DatabasePaths;
import nusynapxe.domain.Appointment;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.AccountService;

/** Creates and removes the local-development SQLite database used by the scripts. */
public final class DemoDataSeeder {
  private DemoDataSeeder() {
    throw new AssertionError("Utility class");
  }

  /** Summary of the records created by one successful seed operation. */
  public record SeedSummary(int accounts, int patients, int appointments) {
    /** Validates that seed counts are non-negative. */
    public SeedSummary {
      if (accounts < 0 || patients < 0 || appointments < 0) {
        throw new IllegalArgumentException("Seed counts cannot be negative");
      }
    }
  }

  /** Resets a database file and recreates its empty, current schema. */
  public static void reset(Path requestedPath) throws SQLException {
    Path databasePath = databasePath(requestedPath);
    List<Path> databaseFiles = databaseFiles(databasePath);
    rejectDirectories(databaseFiles);
    for (Path file : databaseFiles) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException exception) {
        throw new SQLException("Could not remove database file: " + file, exception);
      }
    }
    try (SqliteDatabase database = new SqliteDatabase(databasePath)) {
      database.open();
    }
  }

  /** Seeds a fresh database with staff, patients, calendar data, appointments, and history. */
  public static SeedSummary seed(Path requestedPath) throws SQLException {
    Path databasePath = databasePath(requestedPath);
    try (SqliteDatabase database = new SqliteDatabase(databasePath)) {
      database.open();
      AccountRepository accounts = new AccountRepository(database);
      PatientRepository patients = new PatientRepository(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      ClinicalRecordRepository clinicalRecords = new ClinicalRecordRepository(database);
      requireEmpty(accounts, patients, appointments);

      DemoDataAccountSeeder.SeedAccounts seededAccounts =
          DemoDataAccountSeeder.seed(new AccountService(accounts));
      List<nusynapxe.domain.Patient> createdPatients = DemoDataPatientSeeder.seed(patients);
      DemoDataScheduleSeeder.saveCalendarSettings(
          database, seededAccounts.ada(), seededAccounts.grace());
      List<Appointment> createdAppointments =
          DemoDataScheduleSeeder.seedAppointments(
              appointments, seededAccounts.ada(), seededAccounts.grace(), createdPatients);
      DemoDataClinicalSeeder.seed(clinicalRecords, createdAppointments);
      return new SeedSummary(4, createdPatients.size(), createdAppointments.size());
    }
  }

  private static void requireEmpty(
      AccountRepository accounts, PatientRepository patients, AppointmentRepository appointments)
      throws SQLException {
    if (accounts.hasAccounts()
        || !patients.findAll().isEmpty()
        || !appointments.findAll().isEmpty()) {
      throw new IllegalStateException(
          "The selected database is not empty. Reset it before seeding demo data.");
    }
  }

  private static Path databasePath(Path requestedPath) throws SQLException {
    Path path = DatabasePaths.resolve(requestedPath);
    if (path.getFileName() == null) {
      throw new SQLException("The database path must identify a file");
    }
    return path;
  }

  private static List<Path> databaseFiles(Path databasePath) {
    Path fileNamePath = databasePath.getFileName();
    if (fileNamePath == null) {
      throw new IllegalArgumentException("The database path must identify a file");
    }
    String fileName = fileNamePath.toString();
    return List.of(
        databasePath,
        databasePath.resolveSibling(fileName + "-wal"),
        databasePath.resolveSibling(fileName + "-shm"),
        databasePath.resolveSibling(fileName + "-journal"));
  }

  private static void rejectDirectories(List<Path> databaseFiles) throws SQLException {
    for (Path file : databaseFiles) {
      if (Files.isDirectory(file)) {
        throw new SQLException("The database path is a directory: " + file);
      }
    }
  }
}
