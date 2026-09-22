package nusynapxe.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nusynapxe.ClinicClock;
import nusynapxe.DatabasePaths;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.AccountService;

/** Creates and removes the local-development SQLite database used by the scripts. */
public final class DemoDataSeeder {
  private static final String ADMIN_USERNAME = "admin.demo";
  private static final String ADMIN_PASSWORD = "DemoAdmin123!";
  private static final String ADA_USERNAME = "ada";
  private static final String ADA_PASSWORD = "ada1234!";
  private static final String GRACE_USERNAME = "grace";
  private static final String GRACE_PASSWORD = "grace123!";
  private static final String RECEPTION_USERNAME = "reception";
  private static final String RECEPTION_PASSWORD = "recept123!";
  private static final int HISTORICAL_DAYS = 7;
  private static final int FUTURE_DAYS = 14;
  private static final int APPOINTMENTS_PER_DOCTOR_PER_DAY = 2;
  private static final List<AppointmentStatus> HISTORICAL_STATUSES =
      List.of(
          AppointmentStatus.COMPLETED,
          AppointmentStatus.CHECKED_IN,
          AppointmentStatus.CHECKED_OUT,
          AppointmentStatus.ACCEPTED,
          AppointmentStatus.DECLINED,
          AppointmentStatus.CANCELLED,
          AppointmentStatus.PENDING);
  private static final List<AppointmentStatus> FUTURE_STATUSES =
      List.of(
          AppointmentStatus.PENDING,
          AppointmentStatus.ACCEPTED,
          AppointmentStatus.DECLINED,
          AppointmentStatus.CANCELLED);
  private static final List<LocalTime> ADA_APPOINTMENT_TIMES =
      List.of(LocalTime.of(9, 0), LocalTime.of(14, 0));
  private static final List<LocalTime> GRACE_APPOINTMENT_TIMES =
      List.of(LocalTime.of(10, 0), LocalTime.of(15, 0));
  private static final Set<AppointmentStatus> CONSULTATION_STATUSES =
      Set.of(
          AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED, AppointmentStatus.CHECKED_OUT);

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
   * Seeds a fresh database with staff, patients, calendar preferences, appointments, and history.
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
      ClinicalRecordRepository clinicalRecords = new ClinicalRecordRepository(database);
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
      List<Appointment> createdAppointments =
          createAppointments(appointments, ada, grace, createdPatients);
      seedClinicalHistory(clinicalRecords, createdAppointments);
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
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-07",
            "SG",
            "Grace",
            "Chong",
            "1993-08-16",
            Sex.FEMALE,
            "65",
            "85678901",
            "grace.chong@example.test",
            "18 Harbour View",
            168.0,
            58.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-08",
            "SG",
            "Hassan",
            "Khan",
            "1988-03-09",
            Sex.MALE,
            "65",
            "86789012",
            "hassan.khan@example.test",
            "22 Hilltop Drive",
            176.0,
            81.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-09",
            "SG",
            "Irene",
            "Goh",
            "1975-12-21",
            Sex.FEMALE,
            "65",
            "87890123",
            "irene.goh@example.test",
            "6 Maple Street",
            162.0,
            67.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-10",
            "SG",
            "Jared",
            "Lee",
            "2000-05-14",
            Sex.MALE,
            "65",
            "88901234",
            "jared.lee@example.test",
            "44 Lakeside Walk",
            181.0,
            76.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-11",
            "SG",
            "Kavita",
            "Nair",
            "1990-10-03",
            Sex.FEMALE,
            "65",
            "89012345",
            "kavita.nair@example.test",
            "9 Palm Grove",
            158.0,
            55.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-12",
            "SG",
            "Leon",
            "Ong",
            "1982-07-28",
            Sex.MALE,
            "65",
            "80123456",
            "leon.ong@example.test",
            "73 Garden Terrace",
            179.0,
            83.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-13",
            "SG",
            "Mei",
            "Soh",
            "1996-01-19",
            Sex.FEMALE,
            "65",
            "81235670",
            "mei.soh@example.test",
            "15 Sunset Avenue",
            164.0,
            57.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-14",
            "SG",
            "Nabil",
            "Rahim",
            "1970-09-07",
            Sex.MALE,
            "65",
            "82356781",
            "nabil.rahim@example.test",
            "28 Heritage Lane",
            173.0,
            78.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-15",
            "SG",
            "Olivia",
            "Teo",
            "2004-04-25",
            Sex.FEMALE,
            "65",
            "83467892",
            "olivia.teo@example.test",
            "3 Studio Crescent",
            169.0,
            61.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-16",
            "SG",
            "Pavel",
            "Ivanov",
            "1987-06-11",
            Sex.MALE,
            "65",
            "84578903",
            "pavel.ivanov@example.test",
            "52 Riverbank Road",
            184.0,
            88.0,
            true),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-17",
            "SG",
            "Sara",
            "Yeo",
            "1999-11-02",
            Sex.FEMALE,
            "65",
            "85689014",
            "sara.yeo@example.test",
            "11 Orchard Rise",
            161.0,
            54.0,
            false),
        new Patient(
            0,
            IdentityType.OTHER,
            "SHOWCASE-18",
            "SG",
            "Tariq",
            "Halim",
            "1964-02-13",
            Sex.MALE,
            "65",
            "86790125",
            "tariq.halim@example.test",
            "87 Heritage Street",
            177.0,
            82.0,
            false));
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

  private static List<Appointment> createAppointments(
      AppointmentRepository repository, Account ada, Account grace, List<Patient> patients)
      throws SQLException {
    LocalDate today = ClinicClock.today(ClinicClock.system());
    List<Patient> schedulablePatients = patients.stream().filter(Patient::active).toList();
    List<Appointment> created = new ArrayList<>();
    for (int dayOffset = -HISTORICAL_DAYS; dayOffset <= FUTURE_DAYS; dayOffset++) {
      LocalDate date = today.plusDays(dayOffset);
      int dayIndex = dayOffset + HISTORICAL_DAYS;
      for (int slot = 0; slot < APPOINTMENTS_PER_DOCTOR_PER_DAY; slot++) {
        int patientIndex = Math.floorMod(dayIndex * 4 + slot, schedulablePatients.size());
        created.add(
            createAppointment(
                repository,
                ada,
                schedulablePatients.get(patientIndex),
                date.atTime(ADA_APPOINTMENT_TIMES.get(slot)),
                appointmentStatus(dayIndex, 0, slot)));
        created.add(
            createAppointment(
                repository,
                grace,
                schedulablePatients.get((patientIndex + 2) % schedulablePatients.size()),
                date.atTime(GRACE_APPOINTMENT_TIMES.get(slot)),
                appointmentStatus(dayIndex, 1, slot)));
      }
    }
    return List.copyOf(created);
  }

  private static AppointmentStatus appointmentStatus(int dayIndex, int doctorIndex, int slot) {
    List<AppointmentStatus> statuses =
        dayIndex < HISTORICAL_DAYS ? HISTORICAL_STATUSES : FUTURE_STATUSES;
    return statuses.get(Math.floorMod(dayIndex * 4 + doctorIndex * 2 + slot, statuses.size()));
  }

  private static Appointment createAppointment(
      AppointmentRepository repository,
      Account doctor,
      Patient patient,
      LocalDateTime startsAt,
      AppointmentStatus status)
      throws SQLException {
    return repository.create(patient.id(), doctor.id(), startsAt, startsAt.plusMinutes(30), status);
  }

  private static void seedClinicalHistory(
      ClinicalRecordRepository repository, List<Appointment> appointments) throws SQLException {
    LocalDate today = ClinicClock.today(ClinicClock.system());
    for (Appointment appointment : appointments) {
      if (!appointment.startsAt().toLocalDate().isBefore(today)
          || !CONSULTATION_STATUSES.contains(appointment.status())) {
        continue;
      }
      ClinicalRecord record =
          repository.save(
              new ClinicalRecord(
                  0,
                  appointment.patientId(),
                  appointment.id(),
                  appointment.doctorId(),
                  diagnosisFor(appointment.status()),
                  "Seeded consultation notes for dashboard and clinical-history demos.",
                  "Seeded follow-up instructions for the next appointment."));
      if (appointment.status() == AppointmentStatus.COMPLETED
          || appointment.status() == AppointmentStatus.CHECKED_OUT) {
        repository.addPrescription(
            new Prescription(
                0,
                record.id(),
                "DemoCare tablets",
                "1 tablet",
                "Twice daily",
                "5 days",
                "Take after meals."));
      }
    }
  }

  private static String diagnosisFor(AppointmentStatus status) {
    return switch (status) {
      case CHECKED_IN -> "Routine consultation in progress";
      case COMPLETED -> "Routine follow-up completed";
      case CHECKED_OUT -> "Stable condition at checkout";
      default -> throw new IllegalArgumentException("Unsupported clinical status: " + status);
    };
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
}
