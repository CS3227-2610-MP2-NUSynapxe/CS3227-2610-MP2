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
          AuthorizationException.class,
          () ->
              service.historyForDoctor(
                  fixture.receptionistSession(), fixture.appointment().patientId()));
      assertThrows(
          AuthorizationException.class,
          () ->
              service.saveConsultation(
                  fixture.otherDoctorSession(),
                  fixture.appointment().id(),
                  "Other diagnosis",
                  "Other notes",
                  "Other follow-up"));
      assertThrows(
          AuthorizationException.class,
          () ->
              service.addPrescription(
                  fixture.otherDoctorSession(),
                  fixture.appointment().id(),
                  "Other medicine",
                  "10 mg",
                  "Daily",
                  "7 days",
                  "Take with food"));
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

  @Test
  void anyDoctorCanReadCompletedHistoryButNotInProgressClinicalData() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      ClinicalService service =
          new ClinicalService(appointments, new ClinicalRecordRepository(database));

      service.saveConsultation(
          fixture.doctorSession(),
          fixture.appointment().id(),
          "Diagnosis",
          "Completed notes",
          "Review");
      appointments.updateStatus(fixture.appointment().id(), AppointmentStatus.COMPLETED);

      assertEquals(
          "Completed notes",
          service
              .findForDoctorHistory(fixture.otherDoctorSession(), fixture.appointment().id())
              .orElseThrow()
              .consultationNotes());

      Appointment inProgress =
          appointments.create(
              fixture.appointment().patientId(),
              fixture.appointment().doctorId(),
              LocalDateTime.of(2026, 9, 1, 10, 0),
              LocalDateTime.of(2026, 9, 1, 10, 30),
              AppointmentStatus.CHECKED_IN);
      service.saveConsultation(
          fixture.doctorSession(), inProgress.id(), "Diagnosis", "Draft notes", "Review");

      assertThrows(
          AuthorizationException.class,
          () -> service.findForDoctorHistory(fixture.otherDoctorSession(), inProgress.id()));
    }
  }

  @Test
  void historyForDoctorReturnsCompleteTerminalProjectionForAnyDoctor() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      AppointmentRepository appointments = new AppointmentRepository(database);
      ClinicalService service =
          new ClinicalService(appointments, new ClinicalRecordRepository(database));

      service.saveConsultation(
          fixture.doctorSession(),
          fixture.appointment().id(),
          "First diagnosis",
          "First notes",
          "First follow-up");
      service.addPrescription(
          fixture.doctorSession(),
          fixture.appointment().id(),
          "First medicine",
          "10 mg",
          "Daily",
          "7 days",
          "Take with food");
      appointments.updateStatus(fixture.appointment().id(), AppointmentStatus.COMPLETED);

      Appointment secondAppointment =
          appointments.create(
              fixture.appointment().patientId(),
              fixture.otherDoctorSession().accountId(),
              LocalDateTime.of(2026, 9, 2, 9, 0),
              LocalDateTime.of(2026, 9, 2, 9, 30),
              AppointmentStatus.CHECKED_OUT);
      service.saveConsultation(
          fixture.otherDoctorSession(),
          secondAppointment.id(),
          "Second diagnosis",
          "Second notes",
          "Second follow-up");

      Appointment inProgress =
          appointments.create(
              fixture.appointment().patientId(),
              fixture.appointment().doctorId(),
              LocalDateTime.of(2026, 9, 3, 9, 0),
              LocalDateTime.of(2026, 9, 3, 9, 30),
              AppointmentStatus.CHECKED_IN);
      service.saveConsultation(
          fixture.doctorSession(), inProgress.id(), "Draft diagnosis", "Draft notes", "Draft");

      var history =
          service.historyForDoctor(fixture.otherDoctorSession(), fixture.appointment().patientId());

      assertEquals(2, history.size());
      assertEquals(secondAppointment.id(), history.get(0).appointment().id());
      assertEquals("Dr. Babbage", history.get(0).doctorName());
      assertEquals("Second diagnosis", history.get(0).clinicalRecord().diagnosis());
      assertEquals(fixture.appointment().id(), history.get(1).appointment().id());
      assertEquals("Dr. Ada", history.get(1).doctorName());
      assertEquals(1, history.get(1).prescriptions().size());
      assertEquals(
          2,
          service
              .historyForDoctor(fixture.doctorSession(), fixture.appointment().patientId())
              .size());
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
