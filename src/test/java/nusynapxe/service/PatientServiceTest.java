package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PatientServiceTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void receptionistMaintainsBasicDataWithoutChangingClinicalInformation() throws SQLException {
    try (SqliteDatabase database = openDatabase("confidentiality.db")) {
      Fixture fixture = fixture(database);
      Patient patient =
          fixture.service.register(
              fixture.receptionistSession,
              patient(0, IdentityType.NRIC, " s123unknown ", " sg ", "+6587654321"));
      Appointment appointment =
          new AppointmentRepository(database)
              .create(
                  patient.id(),
                  fixture.doctor.id(),
                  LocalDateTime.of(2026, 9, 1, 9, 0),
                  LocalDateTime.of(2026, 9, 1, 9, 30),
                  AppointmentStatus.CHECKED_IN);
      ClinicalRecord record =
          new ClinicalRecordRepository(database)
              .save(
                  new ClinicalRecord(
                      0,
                      patient.id(),
                      appointment.id(),
                      fixture.doctor.id(),
                      "Diagnosis",
                      "Notes",
                      "Follow up"));

      Patient updated =
          fixture.service.updateAdministrative(
              fixture.receptionistSession, withPhone(patient, "+442071234567"));
      Patient inactive =
          fixture.service.deactivateAdministrative(fixture.receptionistSession, patient.id());

      assertEquals("S123UNKNOWN", updated.identityNumber());
      assertEquals("SG", updated.issuingCountry());
      assertEquals("+442071234567", updated.phone());
      assertFalse(inactive.active());
      assertEquals(
          record,
          new ClinicalRecordRepository(database).findByAppointment(appointment.id()).orElseThrow());
      assertTrue(
          fixture
              .service
              .findClinicalForDoctor(fixture.doctorSession, appointment.id())
              .isPresent());
      assertThrows(
          AuthorizationException.class,
          () ->
              fixture.service.findClinicalForDoctor(fixture.receptionistSession, appointment.id()));
    }
  }

  @Test
  void acceptsFlexibleDocumentsAndRejectsNormalizedDuplicatesAtomically() throws SQLException {
    try (SqliteDatabase database = openDatabase("identity.db")) {
      Fixture fixture = fixture(database);
      Patient first =
          fixture.service.register(
              fixture.receptionistSession,
              patient(0, IdentityType.PASSPORT, " odd/format ", " gb ", "+1"));
      Patient second =
          fixture.service.register(
              fixture.receptionistSession,
              patient(0, IdentityType.OTHER, "document two", "zz", "9"));

      ValidationException duplicateCreate =
          assertThrows(
              ValidationException.class,
              () ->
                  fixture.service.register(
                      fixture.receptionistSession,
                      patient(0, IdentityType.PASSPORT, "ODD/FORMAT", "GB", "+123")));
      ValidationException duplicateUpdate =
          assertThrows(
              ValidationException.class,
              () ->
                  fixture.service.updateAdministrative(
                      fixture.receptionistSession, withIdentity(second, first)));

      assertEquals(PatientService.DUPLICATE_IDENTITY_MESSAGE, duplicateCreate.getMessage());
      assertEquals(PatientService.DUPLICATE_IDENTITY_MESSAGE, duplicateUpdate.getMessage());
      assertEquals(
          second, fixture.service.getAdministrative(fixture.receptionistSession, second.id()));
      assertEquals(2, fixture.service.searchAdministrative(fixture.receptionistSession, "").size());
    }
  }

  @Test
  void validatesRequiredIdentityPhoneDateAndMeasurements() throws SQLException {
    try (SqliteDatabase database = openDatabase("validation.db")) {
      Fixture fixture = fixture(database);

      assertInvalid(fixture, withIdentityType(validPatient(), null), "Identity type is required");
      assertInvalid(
          fixture, withIdentityNumber(validPatient(), " "), "Identity number is required");
      assertInvalid(fixture, withCountry(validPatient(), ""), "Issuing country is required");
      assertInvalid(fixture, withSex(validPatient(), null), "Sex is required");
      assertInvalid(fixture, withDate(validPatient(), "01/09/1990"), "Date of birth must use");
      assertInvalid(fixture, withPhone(validPatient(), "+"), "Phone must contain");
      assertInvalid(fixture, withPhone(validPatient(), "12-34"), "Phone must contain");
      assertInvalid(fixture, withPhone(validPatient(), "1+234"), "Phone must contain");
      assertInvalid(fixture, withHeight(validPatient(), 0.0), "Height must be a positive number");
      assertInvalid(fixture, withWeight(validPatient(), -1.0), "Weight must be a positive number");

      assertEquals(
          "+12345678901234567890",
          fixture
              .service
              .register(
                  fixture.receptionistSession, withPhone(validPatient(), "+12345678901234567890"))
              .phone());
    }
  }

  @Test
  void requiresReceptionistForEveryAdministrativeOperationAndCompletesLegacyIdentity()
      throws SQLException {
    try (SqliteDatabase database = openDatabase("authorization.db")) {
      Fixture fixture = fixture(database);
      Patient legacy =
          new PatientRepository(database)
              .create(new Patient(0, "Legacy", "Patient", "1990-01-01", "123", "", "", ""));
      Patient completed = patient(legacy.id(), IdentityType.FIN, "G123", "SG", "+6588888888");

      assertThrows(
          ValidationException.class,
          () -> fixture.service.updateAdministrative(fixture.receptionistSession, legacy));
      assertEquals(
          completed, fixture.service.updateAdministrative(fixture.receptionistSession, completed));
      assertEquals(
          completed,
          fixture.service.searchAdministrative(fixture.receptionistSession, "p000001").get(0));

      assertThrows(
          AuthorizationException.class,
          () -> fixture.service.searchAdministrative(fixture.doctorSession, ""));
      assertThrows(
          AuthorizationException.class,
          () -> fixture.service.getAdministrative(fixture.doctorSession, completed.id()));
      assertThrows(
          AuthorizationException.class,
          () -> fixture.service.updateAdministrative(fixture.doctorSession, completed));
      assertThrows(
          AuthorizationException.class,
          () -> fixture.service.deactivateAdministrative(fixture.doctorSession, completed.id()));
      assertThrows(
          AuthorizationException.class, () -> fixture.service.searchAdministrative(null, ""));
    }
  }

  private static void assertInvalid(Fixture fixture, Patient patient, String messageFragment) {
    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> fixture.service.register(fixture.receptionistSession, patient));
    assertTrue(exception.getMessage().contains(messageFragment));
  }

  private Fixture fixture(SqliteDatabase database) throws SQLException {
    AccountRepository accounts = new AccountRepository(database);
    Account doctor =
        accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
    Account receptionist =
        accounts.create(
            "reception", "Reception", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4});
    return new Fixture(
        doctor,
        new Session(doctor.id(), doctor.username(), doctor.role()),
        new Session(receptionist.id(), receptionist.username(), receptionist.role()),
        new PatientService(
            new PatientRepository(database),
            new AppointmentRepository(database),
            new ClinicalRecordRepository(database)));
  }

  private SqliteDatabase openDatabase(String name) throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve(name));
    database.open();
    return database;
  }

  private static Patient validPatient() {
    return patient(0, IdentityType.PASSPORT, "AB123", "GB", "+441234");
  }

  private static Patient patient(
      long id, IdentityType type, String identityNumber, String country, String phone) {
    return new Patient(
        id,
        type,
        identityNumber,
        country,
        "Grace",
        "Hopper",
        "1906-12-09",
        Sex.FEMALE,
        phone,
        "grace@example.test",
        "Address",
        "Billing",
        170.5,
        65.5,
        true);
  }

  private static Patient withPhone(Patient patient, String phone) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        phone,
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withIdentity(Patient patient, Patient identitySource) {
    return copy(
        patient,
        identitySource.identityType(),
        identitySource.identityNumber(),
        identitySource.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phone(),
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withIdentityType(Patient patient, IdentityType type) {
    return copy(
        patient,
        type,
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phone(),
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withIdentityNumber(Patient patient, String number) {
    return copy(
        patient,
        patient.identityType(),
        number,
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phone(),
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withCountry(Patient patient, String country) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        country,
        patient.sex(),
        patient.dateOfBirth(),
        patient.phone(),
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withSex(Patient patient, Sex sex) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        sex,
        patient.dateOfBirth(),
        patient.phone(),
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withDate(Patient patient, String date) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        date,
        patient.phone(),
        patient.heightCm(),
        patient.weightKg());
  }

  private static Patient withHeight(Patient patient, Double height) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phone(),
        height,
        patient.weightKg());
  }

  private static Patient withWeight(Patient patient, Double weight) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phone(),
        patient.heightCm(),
        weight);
  }

  private static Patient copy(
      Patient patient,
      IdentityType type,
      String identityNumber,
      String country,
      Sex sex,
      String date,
      String phone,
      Double height,
      Double weight) {
    return new Patient(
        patient.id(),
        type,
        identityNumber,
        country,
        patient.firstName(),
        patient.lastName(),
        date,
        sex,
        phone,
        patient.email(),
        patient.address(),
        patient.billingInformation(),
        height,
        weight,
        patient.active());
  }

  private record Fixture(
      Account doctor, Session doctorSession, Session receptionistSession, PatientService service) {
    // Groups the collaborators shared by this focused service-test fixture.
  }
}
