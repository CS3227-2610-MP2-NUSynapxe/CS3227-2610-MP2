package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZoneId;
import nusynapxe.domain.Account;
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
import org.junit.jupiter.api.io.TempDir;

/** Shared database fixture and patient builders for patient-service tests. */
abstract class PatientServiceTestSupport {
  protected static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");

  @TempDir protected Path temporaryDirectory;

  protected static void assertInvalid(Fixture fixture, Patient patient, String messageFragment) {
    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> fixture.service.register(fixture.receptionistSession, patient));
    assertTrue(exception.getMessage().contains(messageFragment));
  }

  protected Fixture fixture(SqliteDatabase database) throws SQLException {
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

  protected SqliteDatabase openDatabase(String name) throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve(name));
    database.open();
    return database;
  }

  protected static Patient validPatient() {
    return patient(0, IdentityType.PASSPORT, "AB123", "GB", "+441234");
  }

  protected static Patient patient(
      long id, IdentityType type, String identityNumber, String country, String phone) {
    String countryCode = phone.startsWith("+44") ? "44" : phone.startsWith("+1") ? "1" : "65";
    String prefix = "+" + countryCode;
    String number = phone.startsWith(prefix) ? phone.substring(prefix.length()) : phone;
    return new Patient(
        id,
        type,
        identityNumber,
        country,
        "Grace",
        "Hopper",
        "1906-12-09",
        Sex.FEMALE,
        countryCode,
        number,
        "grace@example.test",
        "Address",
        170.0,
        65.5,
        true);
  }

  protected static Patient withPhone(Patient patient, String phone) {
    String countryCode = phone.startsWith("+44") ? "44" : patient.phoneCountryCode();
    String prefix = "+" + countryCode;
    String number = phone.startsWith(prefix) ? phone.substring(prefix.length()) : phone;
    return new Patient(
        patient.id(),
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        countryCode,
        number,
        patient.email(),
        patient.address(),
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  protected static Patient withPhoneCountryCode(Patient patient, String countryCode) {
    return new Patient(
        patient.id(),
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        countryCode,
        patient.phoneNumber(),
        patient.email(),
        patient.address(),
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  protected static Patient withEmail(Patient patient, String email) {
    return new Patient(
        patient.id(),
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        patient.phoneCountryCode(),
        patient.phoneNumber(),
        email,
        patient.address(),
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  protected static Patient withAddress(Patient patient, String address) {
    return new Patient(
        patient.id(),
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.firstName(),
        patient.lastName(),
        patient.dateOfBirth(),
        patient.sex(),
        patient.phoneCountryCode(),
        patient.phoneNumber(),
        patient.email(),
        address,
        patient.heightCm(),
        patient.weightKg(),
        patient.active());
  }

  protected static Patient withIdentity(IdentityType type, String number) {
    Patient patient = validPatient();
    return new Patient(
        patient.id(),
        type,
        number,
        "SG",
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

  protected static Patient withIdentity(Patient patient, Patient identitySource) {
    return copy(
        patient,
        identitySource.identityType(),
        identitySource.identityNumber(),
        identitySource.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phoneNumber(),
        patient.heightCm(),
        patient.weightKg());
  }

  protected static Patient withIdentityType(Patient patient, IdentityType type) {
    return copy(
        patient,
        type,
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phoneNumber(),
        patient.heightCm(),
        patient.weightKg());
  }

  protected static Patient withIdentityNumber(Patient patient, String number) {
    return copy(
        patient,
        patient.identityType(),
        number,
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phoneNumber(),
        patient.heightCm(),
        patient.weightKg());
  }

  protected static Patient withCountry(Patient patient, String country) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        country,
        patient.sex(),
        patient.dateOfBirth(),
        patient.phoneNumber(),
        patient.heightCm(),
        patient.weightKg());
  }

  protected static Patient withSex(Patient patient, Sex sex) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        sex,
        patient.dateOfBirth(),
        patient.phoneNumber(),
        patient.heightCm(),
        patient.weightKg());
  }

  protected static Patient withDate(Patient patient, String date) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        date,
        patient.phoneNumber(),
        patient.heightCm(),
        patient.weightKg());
  }

  protected static Patient withHeight(Patient patient, Double height) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phoneNumber(),
        height,
        patient.weightKg());
  }

  protected static Patient withWeight(Patient patient, Double weight) {
    return copy(
        patient,
        patient.identityType(),
        patient.identityNumber(),
        patient.issuingCountry(),
        patient.sex(),
        patient.dateOfBirth(),
        patient.phoneNumber(),
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
        patient.phoneCountryCode(),
        phone,
        patient.email(),
        patient.address(),
        height,
        weight,
        patient.active());
  }

  protected static final class Fixture {
    final Account doctor;
    final Session doctorSession;
    final Session receptionistSession;
    final PatientService service;

    Fixture(
        Account doctor,
        Session doctorSession,
        Session receptionistSession,
        PatientService service) {
      this.doctor = doctor;
      this.doctorSession = doctorSession;
      this.receptionistSession = receptionistSession;
      this.service = service;
    }

    // Groups the collaborators shared by this focused service-test fixture.
  }
}
