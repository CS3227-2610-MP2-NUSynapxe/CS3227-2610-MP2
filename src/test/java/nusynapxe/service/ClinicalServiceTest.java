package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
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

final class ClinicalServiceTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void assignedDoctorCanSaveAndEditConsultationAndPrescription() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      ClinicalService service =
          new ClinicalService(
              new AppointmentRepository(database), new ClinicalRecordRepository(database));

      var record =
          service.saveConsultation(
              fixture.doctorSession(),
              fixture.appointment().id(),
              "Diagnosis",
              "Initial notes",
              "Review");
      var updated =
          service.saveConsultation(
              fixture.doctorSession(),
              fixture.appointment().id(),
              "Updated diagnosis",
              "Updated notes",
              "Later");
      var prescription =
          service.addPrescription(
              fixture.doctorSession(),
              fixture.appointment().id(),
              "Medicine",
              "10 mg",
              "Daily",
              "7 days",
              "Take with food");

      assertEquals(record.id(), updated.id());
      assertEquals("Updated diagnosis", updated.diagnosis());
      assertEquals(record.id(), prescription.clinicalRecordId());
      assertEquals(
          1,
          service
              .prescriptionsForDoctor(fixture.doctorSession(), fixture.appointment().id())
              .size());
    }
  }

  @Test
  void deniesUnassignedDoctorsAndReceptionistsAndPreservesExistingRecordOnInvalidInput()
      throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      ClinicalService service =
          new ClinicalService(
              new AppointmentRepository(database), new ClinicalRecordRepository(database));
      service.saveConsultation(
          fixture.doctorSession(),
          fixture.appointment().id(),
          "Diagnosis",
          "Initial notes",
          "Review");

      assertThrows(
          AuthorizationException.class,
          () -> service.findForDoctor(fixture.otherDoctorSession(), fixture.appointment().id()));
      assertThrows(
          AuthorizationException.class,
          () -> service.findForDoctor(fixture.receptionistSession(), fixture.appointment().id()));
      assertThrows(
          ValidationException.class,
          () ->
              service.addPrescription(
                  fixture.doctorSession(),
                  fixture.appointment().id(),
                  "",
                  "10 mg",
                  "Daily",
                  "7 days",
                  "Take with food"));
      assertEquals(
          "Initial notes",
          service
              .findForDoctor(fixture.doctorSession(), fixture.appointment().id())
              .orElseThrow()
              .consultationNotes());
    }
  }

  private Fixture fixture(SqliteDatabase database) throws SQLException {
    AccountRepository accounts = new AccountRepository(database);
    Account doctor =
        accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
    Account otherDoctor =
        accounts.create("other", "Dr. Babbage", Role.DOCTOR, new byte[] {3}, new byte[] {4});
    Account receptionist =
        accounts.create(
            "reception", "Reception", Role.RECEPTIONIST, new byte[] {5}, new byte[] {6});
    Patient patient =
        new PatientRepository(database)
            .create(
                new Patient(
                    0,
                    "Grace",
                    "Hopper",
                    "1906-12-09",
                    "555-0100",
                    "grace@example.test",
                    "Address"));
    Appointment appointment =
        new AppointmentRepository(database)
            .create(
                patient.id(),
                doctor.id(),
                LocalDateTime.of(2026, 9, 1, 9, 0),
                LocalDateTime.of(2026, 9, 1, 9, 30),
                AppointmentStatus.CHECKED_IN);
    return new Fixture(
        appointment,
        new Session(doctor.id(), doctor.username(), doctor.role()),
        new Session(otherDoctor.id(), otherDoctor.username(), otherDoctor.role()),
        new Session(receptionist.id(), receptionist.username(), receptionist.role()));
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clinical.db"));
    database.open();
    return database;
  }

  private record Fixture(
      Appointment appointment,
      Session doctorSession,
      Session otherDoctorSession,
      Session receptionistSession) {
    // Shared fixture for clinical authorization tests.
  }
}
