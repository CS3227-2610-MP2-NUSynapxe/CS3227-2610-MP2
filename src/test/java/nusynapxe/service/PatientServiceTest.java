package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
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
  void receptionistOwnsAdministrativePatientDataAndDoctorReadsAssignedClinicalData()
      throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = new AccountRepository(database);
      Account doctor =
          accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
      Account receptionist =
          accounts.create(
              "reception", "Reception", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4});
      Session doctorSession = new Session(doctor.id(), doctor.username(), doctor.role());
      Session receptionistSession =
          new Session(receptionist.id(), receptionist.username(), receptionist.role());
      PatientService service =
          new PatientService(
              new PatientRepository(database),
              new AppointmentRepository(database),
              new ClinicalRecordRepository(database));

      Patient patient =
          service.register(
              receptionistSession,
              new Patient(
                  0,
                  "Grace",
                  "Hopper",
                  "1906-12-09",
                  "555-0100",
                  "grace@example.test",
                  "Address",
                  "Billing"));
      Appointment appointment =
          new AppointmentRepository(database)
              .create(
                  patient.id(),
                  doctor.id(),
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
                      doctor.id(),
                      "Diagnosis",
                      "Notes",
                      "Follow up"));

      assertEquals(patient, service.getAdministrative(receptionistSession, patient.id()));
      assertTrue(service.findClinicalForDoctor(doctorSession, appointment.id()).isPresent());
      assertEquals(
          record, service.findClinicalForDoctor(doctorSession, appointment.id()).orElseThrow());
      assertThrows(
          AuthorizationException.class,
          () -> service.findClinicalForDoctor(receptionistSession, appointment.id()));
    }
  }

  @Test
  void rejectsInvalidPatientIdentity() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account receptionist =
          new AccountRepository(database)
              .create("reception", "Reception", Role.RECEPTIONIST, new byte[] {1}, new byte[] {2});
      PatientService service =
          new PatientService(
              new PatientRepository(database),
              new AppointmentRepository(database),
              new ClinicalRecordRepository(database));

      assertThrows(
          ValidationException.class,
          () ->
              service.register(
                  new Session(receptionist.id(), receptionist.username(), receptionist.role()),
                  new Patient(0, "", "Hopper", "", "", "", "", "")));
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("patients.db"));
    database.open();
    return database;
  }
}
