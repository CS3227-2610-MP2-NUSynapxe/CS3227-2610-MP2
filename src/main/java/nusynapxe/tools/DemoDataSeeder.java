package nusynapxe.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import nusynapxe.DatabasePaths;
import nusynapxe.domain.Account;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.AccountService;

/** Creates and removes the local-development SQLite database used by the scripts. */
public final class DemoDataSeeder {
  /** Singapore timezone used to place showcase appointments around the current week. */
  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Singapore");

  private static final String ADMIN_USERNAME = "admin.demo";
  private static final String ADMIN_PASSWORD = "DemoAdmin123!";
  private static final String ADA_USERNAME = "doctor.ada";
  private static final String ADA_PASSWORD = "DemoDoctor123!";
  private static final String GRACE_USERNAME = "doctor.grace";
  private static final String GRACE_PASSWORD = "DemoDoctor123!";
  private static final String RECEPTION_USERNAME = "reception.demo";
  private static final String RECEPTION_PASSWORD = "DemoReception123!";
  private static final int GENERATED_FUTURE_APPOINTMENTS = 32;
  private static final List<AppointmentStatus> FUTURE_STATUSES =
      List.of(
          AppointmentStatus.PENDING,
          AppointmentStatus.ACCEPTED,
          AppointmentStatus.ACCEPTED,
          AppointmentStatus.CANCELLED,
          AppointmentStatus.PENDING,
          AppointmentStatus.ACCEPTED);

  private DemoDataSeeder() {
    throw new AssertionError("Utility class");
  }

  /**
   * Summary of the records created by one successful seed operation.
   *
   * @param accounts number of staff accounts created
   * @param patients number of patients created
   * @param appointments number of appointments created
   */
  public record SeedSummary(int accounts, int patients, int appointments) {
    /**
     * Validates seed counts.
     *
     * @throws IllegalArgumentException if a count is negative
     */
    public SeedSummary {
      if (accounts < 0 || patients < 0 || appointments < 0) {
        throw new IllegalArgumentException("Seed counts cannot be negative");
      }
    }
  }

  /**
   * Resets a database file and recreates its empty, current schema.
   *
   * <p>The caller is responsible for obtaining confirmation before invoking this destructive
   * operation. Only the SQLite database and its adjacent journal files are removed.
   *
   * @param requestedPath database file to reset
   * @throws SQLException if the files cannot be removed or the schema cannot be initialized
   */
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

  /**
   * Seeds a fresh database with staff, patients, calendar preferences, and appointments.
   *
   * <p>Seeding refuses a database that already contains accounts, patients, or appointments. Use
   * the reset script first when replacing an existing local-development database.
   *
   * @param requestedPath database file to seed
   * @return counts of the created records
   * @throws SQLException if the database cannot be opened or a record cannot be created
   * @throws IllegalStateException if the database is not empty
   */
  public static SeedSummary seed(Path requestedPath) throws SQLException {
    Path databasePath = databasePath(requestedPath);
    try (SqliteDatabase database = new SqliteDatabase(databasePath)) {
      database.open();

      AccountRepository accounts = new AccountRepository(database);
      PatientRepository patients = new PatientRepository(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      requireEmpty(accounts, patients, appointments);

      AccountService accountService = new AccountService(accounts);
      Account admin =
          accountService.createInitialAdmin(
              ADMIN_USERNAME, "Demo Administrator", ADMIN_PASSWORD.toCharArray());
      Session adminSession = session(admin);
      Account ada =
          accountService.createStaff(
              adminSession,
              ADA_USERNAME,
              "Dr. Ada Lovelace",
              Role.DOCTOR,
              ADA_PASSWORD.toCharArray());
      Account grace =
          accountService.createStaff(
              adminSession,
              GRACE_USERNAME,
              "Dr. Grace Hopper",
              Role.DOCTOR,
              GRACE_PASSWORD.toCharArray());
      accountService.createStaff(
          adminSession,
          RECEPTION_USERNAME,
          "Demo Receptionist",
          Role.RECEPTIONIST,
          RECEPTION_PASSWORD.toCharArray());

      List<Patient> createdPatients = createPatients(patients);
      saveCalendarSettings(database, ada, grace);
      int appointmentCount = createAppointments(appointments, ada, grace, createdPatients);
      return new SeedSummary(4, createdPatients.size(), appointmentCount);
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

  private static List<Patient> createPatients(PatientRepository repository) throws SQLException {
    List<Patient> patients = new ArrayList<>();
    for (Patient requestedPatient : requestedPatients()) {
      patients.add(repository.create(requestedPatient));
    }
    return List.copyOf(patients);
  }

  private static List<Patient> requestedPatients() {
    return List.of(
        new Patient(
            0,
            IdentityType.NRIC,
            "S1234567D",
            "SG",
            "Aisha",
            "Rahman",
            "1991-04-12",
            Sex.FEMALE,
            "65",
            "81234567",
            "aisha.rahman@example.test",
            "12 Demo Avenue",
            165.0,
            60.0,
            true),
        new Patient(
            0,
            IdentityType.FIN,
            "G2345678N",
            "SG",
            "Benjamin",
            "Tan",
            "1985-09-23",
            Sex.MALE,
            "65",
            "82345678",
            "benjamin.tan@example.test",
            "48 Showcase Road",
            178.0,
            74.5,
            true),
        new Patient(
            0,
            IdentityType.PASSPORT,
            "GB7K29M4",
            "GB",
            "Chloe",
            "Morgan",
            "1998-02-18",
            Sex.FEMALE,
            "44",
            "77009001",
            "chloe.morgan@example.test",
            "7 Orchard Walk",
            171.0,
            63.2,
            true),
        new Patient(
            0,
            IdentityType.PASSPORT,
            "AU4P82Q1",
            "AU",
            "Daniel",
            "Wong",
            "1977-11-05",
            Sex.MALE,
            "61",
            "412345678",
            "daniel.wong@example.test",
            "31 Riverside Crescent",
            182.0,
            86.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-05",
            "SG",
            "Elena",
            "Lim",
            "2002-06-30",
            Sex.FEMALE,
            "65",
            "83456789",
            "elena.lim@example.test",
            "5 Gallery Lane",
            160.0,
            52.8,
            true),
        new Patient(
            0,
            IdentityType.NRIC,
            "T7654321H",
            "SG",
            "Farid",
            "Ismail",
            "1969-01-27",
            Sex.MALE,
            "65",
            "84567890",
            "farid.ismail@example.test",
            "90 Heritage Street",
            174.0,
            79.0,
            true));
  }

  private static void saveCalendarSettings(SqliteDatabase database, Account ada, Account grace)
      throws SQLException {
    CalendarSettingsRepository settings = new CalendarSettingsRepository(database);
    settings.save(splitWeekSettings(ada.id(), DayOfWeek.MONDAY));
    settings.save(singleShiftSettings(grace.id(), DayOfWeek.SUNDAY));
  }

  private static DoctorCalendarSettings splitWeekSettings(long doctorId, DayOfWeek firstDay) {
    Map<DayOfWeek, List<WorkingInterval>> intervals = emptyIntervals();
    for (DayOfWeek day : weekdays()) {
      intervals.put(
          day,
          List.of(new WorkingInterval(8 * 60, 12 * 60), new WorkingInterval(13 * 60, 18 * 60)));
    }
    return new DoctorCalendarSettings(doctorId, firstDay, intervals);
  }

  private static DoctorCalendarSettings singleShiftSettings(long doctorId, DayOfWeek firstDay) {
    Map<DayOfWeek, List<WorkingInterval>> intervals = emptyIntervals();
    for (DayOfWeek day : weekdays()) {
      intervals.put(day, List.of(new WorkingInterval(9 * 60, 17 * 60)));
    }
    return new DoctorCalendarSettings(doctorId, firstDay, intervals);
  }

  private static Map<DayOfWeek, List<WorkingInterval>> emptyIntervals() {
    Map<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      intervals.put(day, List.of());
    }
    return intervals;
  }

  private static List<DayOfWeek> weekdays() {
    return List.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY);
  }

  private static int createAppointments(
      AppointmentRepository repository, Account ada, Account grace, List<Patient> patients)
      throws SQLException {
    LocalDate weekStart =
        CalendarWeek.containing(LocalDate.now(CLINIC_ZONE), DayOfWeek.MONDAY).start();
    int count = createAdaWeek(repository, ada, patients, weekStart);
    count += createAdaFutureSchedule(repository, ada, patients, weekStart);
    count += createGraceSchedule(repository, grace, patients, weekStart);
    return count;
  }

  private static int createAdaWeek(
      AppointmentRepository repository, Account doctor, List<Patient> patients, LocalDate weekStart)
      throws SQLException {
    List<AppointmentSpec> appointments =
        List.of(
            new AppointmentSpec(0, LocalTime.of(9, 0), AppointmentStatus.COMPLETED, 0),
            new AppointmentSpec(1, LocalTime.of(10, 30), AppointmentStatus.ACCEPTED, 1),
            new AppointmentSpec(2, LocalTime.of(13, 0), AppointmentStatus.CHECKED_IN, 2),
            new AppointmentSpec(3, LocalTime.of(15, 0), AppointmentStatus.PENDING, 3),
            new AppointmentSpec(4, LocalTime.of(11, 0), AppointmentStatus.CANCELLED, 4),
            new AppointmentSpec(5, LocalTime.of(9, 30), AppointmentStatus.ACCEPTED, 5),
            new AppointmentSpec(6, LocalTime.of(14, 0), AppointmentStatus.PENDING, 0));
    createAppointmentSpecs(repository, doctor, patients, weekStart, appointments);
    return appointments.size();
  }

  private static int createAdaFutureSchedule(
      AppointmentRepository repository, Account doctor, List<Patient> patients, LocalDate weekStart)
      throws SQLException {
    for (int index = 0; index < GENERATED_FUTURE_APPOINTMENTS; index++) {
      LocalDate date = weekStart.plusDays(7L + index);
      AppointmentStatus status = FUTURE_STATUSES.get(index % FUTURE_STATUSES.size());
      createAppointment(
          repository,
          doctor,
          patients.get(index % patients.size()),
          date.atTime(LocalTime.of(9, 0)),
          status);
    }
    return GENERATED_FUTURE_APPOINTMENTS;
  }

  private static int createGraceSchedule(
      AppointmentRepository repository, Account doctor, List<Patient> patients, LocalDate weekStart)
      throws SQLException {
    List<AppointmentSpec> appointments =
        List.of(
            new AppointmentSpec(1, LocalTime.of(9, 0), AppointmentStatus.ACCEPTED, 2),
            new AppointmentSpec(2, LocalTime.of(11, 0), AppointmentStatus.PENDING, 3),
            new AppointmentSpec(4, LocalTime.of(15, 0), AppointmentStatus.COMPLETED, 4),
            new AppointmentSpec(6, LocalTime.of(10, 0), AppointmentStatus.ACCEPTED, 5),
            new AppointmentSpec(7, LocalTime.of(14, 0), AppointmentStatus.PENDING, 1));
    createAppointmentSpecs(repository, doctor, patients, weekStart, appointments);
    return appointments.size();
  }

  private static void createAppointmentSpecs(
      AppointmentRepository repository,
      Account doctor,
      List<Patient> patients,
      LocalDate weekStart,
      List<AppointmentSpec> specifications)
      throws SQLException {
    for (AppointmentSpec specification : specifications) {
      createAppointment(
          repository,
          doctor,
          patients.get(specification.patientIndex()),
          weekStart.plusDays(specification.dayOffset()).atTime(specification.time()),
          specification.status());
    }
  }

  private static void createAppointment(
      AppointmentRepository repository,
      Account doctor,
      Patient patient,
      LocalDateTime startsAt,
      AppointmentStatus status)
      throws SQLException {
    repository.create(patient.id(), doctor.id(), startsAt, startsAt.plusMinutes(30), status);
  }

  private static Session session(Account account) {
    return new Session(account.id(), account.username(), account.role());
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

  private record AppointmentSpec(
      int dayOffset, LocalTime time, AppointmentStatus status, int patientIndex) {
    // Immutable appointment creation input.
  }
}
