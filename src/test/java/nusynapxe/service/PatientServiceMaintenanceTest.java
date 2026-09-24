package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PatientDeletionBlockers;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.PaymentRepository;
import nusynapxe.persistence.ReceiptRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;

/** Verifies authorized patient maintenance and deletion behavior. */
final class PatientServiceMaintenanceTest extends PatientServiceTestSupport {
  @Test
  void authorizedStaffMaintainBasicDataWithoutChangingClinicalOrPaymentInformation()
      throws SQLException {
    try (SqliteDatabase database = openDatabase("confidentiality.db")) {
      Fixture fixture = fixture(database);
      Patient patient =
          fixture.service.register(
              fixture.receptionistSession,
              patient(0, IdentityType.NRIC, " s1234567d ", " sg ", "+6587654321"));
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
      new AppointmentRepository(database)
          .updateStatus(appointment.id(), AppointmentStatus.COMPLETED);
      Payment payment =
          new PaymentRepository(database)
              .createCheckout(
                  new Payment(
                      0,
                      appointment.id(),
                      patient.id(),
                      fixture.receptionistSession.accountId(),
                      4500,
                      PaymentMethod.CARD,
                      PaymentStatus.SUCCESSFUL,
                      LocalDateTime.of(2026, 9, 1, 10, 0)));
      ReceiptRepository receipts = new ReceiptRepository(database);
      Receipt receiptBefore =
          receipts
              .findAll("grace@example.test", fixture.doctor.id(), LocalDate.of(2026, 9, 1))
              .get(0);

      Patient updated =
          fixture.service.updateAdministrative(
              fixture.receptionistSession, withPhone(patient, "+442071234567"));
      Patient inactive =
          fixture.service.deactivateAdministrative(fixture.receptionistSession, patient.id());
      Patient activeAgain =
          fixture.service.activateAdministrative(fixture.receptionistSession, patient.id());
      Patient doctorUpdated =
          fixture.service.updateAdministrative(
              fixture.doctorSession, withPhone(activeAgain, "+442071234568"));

      assertEquals("S1234567D", updated.identityNumber());
      assertEquals("SG", updated.issuingCountry());
      assertEquals("+442071234567", updated.phone());
      assertEquals("+442071234568", doctorUpdated.phone());
      assertFalse(inactive.active());
      assertTrue(activeAgain.active());
      assertEquals(
          record,
          new ClinicalRecordRepository(database).findByAppointment(appointment.id()).orElseThrow());
      assertEquals(
          payment,
          new PaymentRepository(database).findByAppointment(appointment.id()).orElseThrow());
      assertEquals(
          receiptBefore,
          receipts
              .findAll("grace@example.test", fixture.doctor.id(), LocalDate.of(2026, 9, 1))
              .get(0));
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
  void doctorCanRegisterAdministrativePatient() throws SQLException {
    try (SqliteDatabase database = openDatabase("doctor-register.db")) {
      Fixture fixture = fixture(database);

      Patient created = fixture.service.register(fixture.doctorSession, validPatient());

      assertEquals(1, created.id());
      assertEquals(created, fixture.service.getAdministrative(fixture.doctorSession, created.id()));
      assertEquals(
          List.of(created), fixture.service.searchAdministrative(fixture.doctorSession, ""));
    }
  }

  @Test
  void distinguishesPatientDeletionOutcomesAndKeepsBlockedPatient() throws SQLException {
    try (SqliteDatabase database = openDatabase("service-delete.db")) {
      Fixture fixture = fixture(database);
      Patient unused = fixture.service.register(fixture.doctorSession, validPatient());

      assertTrue(fixture.service.deletionBlockers(fixture.doctorSession, unused.id()).canDelete());
      fixture.service.deleteAdministrative(fixture.receptionistSession, unused.id());
      assertThrows(
          ValidationException.class,
          () -> fixture.service.deletionBlockers(fixture.doctorSession, unused.id()));
      assertThrows(
          ValidationException.class,
          () -> fixture.service.deleteAdministrative(fixture.doctorSession, unused.id()));

      Patient linked =
          fixture.service.register(
              fixture.doctorSession, withIdentity(IdentityType.OTHER, "LINKED"));
      Appointment appointment =
          new AppointmentRepository(database)
              .create(
                  linked.id(),
                  fixture.doctor.id(),
                  LocalDateTime.of(2026, 9, 3, 9, 0),
                  LocalDateTime.of(2026, 9, 3, 9, 30),
                  AppointmentStatus.PENDING);
      PatientDeletionBlockers blockers =
          fixture.service.deletionBlockers(fixture.doctorSession, linked.id());
      assertEquals(1, blockers.appointments());

      PatientDeletionBlockedException blocked =
          assertThrows(
              PatientDeletionBlockedException.class,
              () -> fixture.service.deleteAdministrative(fixture.doctorSession, linked.id()));
      assertEquals(blockers, blocked.blockers());
      assertFalse(blocked.getMessage().contains("LINKED"));
      assertEquals(
          linked, fixture.service.getAdministrative(fixture.receptionistSession, linked.id()));
      assertEquals(
          appointment,
          new AppointmentRepository(database).findById(appointment.id()).orElseThrow());
      assertThrows(
          AuthorizationException.class,
          () ->
              fixture.service.deleteAdministrative(
                  new Session(99, "admin", Role.SYSTEM_ADMIN), linked.id()));
    }
  }

  @Test
  void allowsDoctorsAndReceptionistsToMaintainPatientsAndCompletesLegacyIdentity()
      throws SQLException {
    try (SqliteDatabase database = openDatabase("authorization.db")) {
      Fixture fixture = fixture(database);
      Patient legacy =
          new PatientRepository(database)
              .create(new Patient(0, "Legacy", "Patient", "1990-01-01", "123", "", ""));
      Patient completed = patient(legacy.id(), IdentityType.FIN, "G1234567A", "SG", "+6588888888");

      assertThrows(
          ValidationException.class,
          () -> fixture.service.updateAdministrative(fixture.receptionistSession, legacy));
      assertEquals(
          completed, fixture.service.updateAdministrative(fixture.receptionistSession, completed));
      assertEquals(
          completed,
          fixture.service.searchAdministrative(fixture.receptionistSession, "p000001").get(0));

      Patient doctorUpdated =
          fixture.service.updateAdministrative(
              fixture.doctorSession, withPhone(completed, "+6566666666"));
      assertEquals("+6566666666", doctorUpdated.phone());
      assertEquals(
          doctorUpdated, fixture.service.getAdministrative(fixture.doctorSession, completed.id()));
      assertEquals(1, fixture.service.searchAdministrative(fixture.doctorSession, "").size());
      assertFalse(
          fixture.service.deactivateAdministrative(fixture.doctorSession, completed.id()).active());
      assertTrue(
          fixture.service.activateAdministrative(fixture.doctorSession, completed.id()).active());
      assertThrows(
          AuthorizationException.class,
          () ->
              fixture.service.searchAdministrative(new Session(9, "admin", Role.SYSTEM_ADMIN), ""));
      assertThrows(
          AuthorizationException.class,
          () ->
              fixture.service.getAdministrative(
                  new Session(9, "admin", Role.SYSTEM_ADMIN), completed.id()));
      assertThrows(
          AuthorizationException.class, () -> fixture.service.searchAdministrative(null, ""));
    }
  }
}
