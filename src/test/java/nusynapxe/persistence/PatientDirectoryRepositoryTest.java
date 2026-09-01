package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import nusynapxe.domain.Account;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Sex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PatientDirectoryRepositoryTest {
  @TempDir
  private Path temporaryDirectory;

  @Test
  void persistsLocalForeignAndLegacyAdministrativeProjections() throws SQLException {
    try (SqliteDatabase database = openDatabase("round-trip.db")) {
      PatientRepository patients = new PatientRepository(database);
      Patient local = patients.create(patient(IdentityType.NRIC, " s123 ", " sg ", "Local"));
      Patient foreign = patients.create(patient(IdentityType.PASSPORT, " ab-12 ", " gb ", "Foreign"));
      Patient legacy = patients.create(new Patient(0, "Legacy", "Patient", "1990-01-01", "123", "", ""));

      assertEquals("S123", local.identityNumber());
      assertEquals("SG", local.issuingCountry());
      assertEquals("AB-12", foreign.identityNumber());
      assertEquals("GB", foreign.issuingCountry());
      assertEquals(local, patients.findById(local.id()).orElseThrow());
      assertEquals(foreign, patients.findById(foreign.id()).orElseThrow());
      assertEquals(legacy, patients.findById(legacy.id()).orElseThrow());
    }
  }

  @Test
  void searchesEveryPermittedFieldAndEscapesWildcards() throws SQLException {
    try (SqliteDatabase database = openDatabase("search.db")) {
      PatientRepository patients = new PatientRepository(database);
      Patient alpha = patients.create(patient(IdentityType.PASSPORT, "AB%_12", "GB", "Percent%"));
      Patient beta = patients.create(patient(IdentityType.FIN, "G765", "SG", "Zeta"));

      assertEquals(List.of(alpha), patients.search(alpha.displayedId()));
      assertTrue(patients.search(Long.toString(alpha.id())).contains(alpha));
      assertEquals(List.of(alpha), patients.search("ab%_"));
      assertEquals(List.of(alpha), patients.search("percent%"));
      assertEquals(List.of(beta), patients.search("fin"));
      assertEquals(List.of(beta), patients.search("g765"));
      assertEquals(List.of(alpha, beta), patients.search("+441234"));
      assertTrue(patients.search("no-match").isEmpty());
      assertEquals(List.of(alpha, beta), patients.search(""));
    }
  }

  @Test
  void rejectsNormalizedDuplicateCreateAndUpdateWithoutPartialChanges() throws SQLException {
    try (SqliteDatabase database = openDatabase("duplicates.db")) {
      PatientRepository patients = new PatientRepository(database);
      Patient first = patients.create(patient(IdentityType.PASSPORT, "AB12", "GB", "Alpha"));
      Patient second = patients.create(patient(IdentityType.PASSPORT, "CD34", "GB", "Beta"));

      assertThrows(
          SQLException.class,
          () -> patients.create(patient(IdentityType.PASSPORT, " ab12 ", " gb ", "Duplicate")));
      Patient conflicting = withIdentity(second, first);
      assertThrows(SQLException.class, () -> patients.update(conflicting));
      assertEquals(second, patients.findById(second.id()).orElseThrow());
      assertEquals(2, patients.findAll().size());
    }
  }

  @Test
  void concurrentDuplicateCreationCommitsAtMostOneIdentity() throws Exception {
    Path path = temporaryDirectory.resolve("concurrent.db");
    try (SqliteDatabase firstDatabase = new SqliteDatabase(path);
        SqliteDatabase secondDatabase = new SqliteDatabase(path)) {
      firstDatabase.open();
      secondDatabase.open();
      PatientRepository first = new PatientRepository(firstDatabase);
      PatientRepository second = new PatientRepository(secondDatabase);
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        Future<Boolean> firstResult = executor.submit(() -> attemptCreate(first, start, "First"));
        Future<Boolean> secondResult = executor.submit(() -> attemptCreate(second, start, "Second"));
        start.countDown();
        int successes = (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0);

        assertEquals(1, successes);
        assertTrue(first.findByIdentity(IdentityType.PASSPORT, "GB", "CONCURRENT").isPresent());
      }
    }
  }

  @Test
  void deactivationPreservesPatientAndAppointmentRelationship() throws SQLException {
    try (SqliteDatabase database = openDatabase("deactivate.db")) {
      PatientRepository patients = new PatientRepository(database);
      Patient patient = patients.create(patient(IdentityType.OTHER, "X1", "ZZ", "History"));
      Account doctor = new AccountRepository(database)
          .create("doctor", "Doctor", Role.DOCTOR, new byte[] { 1 }, new byte[] { 2 });
      long appointmentId = new AppointmentRepository(database)
          .create(
              patient.id(),
              doctor.id(),
              LocalDateTime.of(2026, 9, 2, 9, 0),
              LocalDateTime.of(2026, 9, 2, 9, 30),
              AppointmentStatus.PENDING)
          .id();

      Patient inactive = patients.deactivate(patient.id());
      Patient activeAgain = patients.activate(patient.id());

      assertFalse(inactive.active());
      assertTrue(activeAgain.active());
      assertTrue(patients.findById(patient.id()).orElseThrow().active());
      assertEquals(
          patient.id(),
          new AppointmentRepository(database).findById(appointmentId).orElseThrow().patientId());
    }
  }

  private SqliteDatabase openDatabase(String name) throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve(name));
    database.open();
    return database;
  }

  private static Patient patient(
      IdentityType type, String identityNumber, String country, String firstName) {
    return new Patient(
        0,
        type,
        identityNumber,
        country,
        firstName,
        "Patient",
        "1990-01-01",
        Sex.FEMALE,
        "44",
        "1234",
        firstName.toLowerCase(Locale.ROOT) + "@example.test",
        "Address",
        170.5,
        65.5,
        true);
  }

  private static Patient withIdentity(Patient patient, Patient identitySource) {
    return new Patient(
        patient.id(),
        identitySource.identityType(),
        identitySource.identityNumber(),
        identitySource.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        patient.phoneCountryCode(),
        patient.phoneNumber(),
        patient.email(),
        patient.address(),
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  private static boolean attemptCreate(
      PatientRepository repository, CountDownLatch start, String firstName)
      throws InterruptedException, ExecutionException {
    start.await();
    try {
      repository.create(patient(IdentityType.PASSPORT, "CONCURRENT", "GB", firstName));
      return true;
    } catch (SQLException exception) {
      return false;
    }
  }
}
