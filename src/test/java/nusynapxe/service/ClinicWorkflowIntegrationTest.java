package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClinicWorkflowIntegrationTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void completesClinicWorkflowWithThreeRoleSessionsAndConfidentiality() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      ClinicServices services = ClinicServices.forDatabase(database);
      Account admin =
          services
              .accountService()
              .createInitialAdmin("admin", "Admin", "secure-pass".toCharArray());
      Session adminSession = new Session(admin.id(), admin.username(), Role.SYSTEM_ADMIN);
      Account doctor =
          services
              .accountService()
              .createStaff(
                  adminSession, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray());
      Account receptionist =
          services
              .accountService()
              .createStaff(
                  adminSession,
                  "reception",
                  "Reception",
                  Role.RECEPTIONIST,
                  "reception-pass".toCharArray());
      Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
      Session receptionistSession =
          new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);

      Patient patient =
          services
              .patientService()
              .register(
                  receptionistSession,
                  new Patient(
                      0,
                      IdentityType.NRIC,
                      "S123UNKNOWN",
                      "SG",
                      "Grace",
                      "Hopper",
                      "1906-12-09",
                      Sex.FEMALE,
                      "+655550100",
                      "grace@example.test",
                      "Address",
                      "Billing",
                      170.0,
                      65.0,
                      true));
      LocalDateTime start = LocalDateTime.now().minusMinutes(5).withSecond(0).withNano(0);
      Appointment appointment =
          services
              .appointmentService()
              .book(receptionistSession, patient.id(), doctor.id(), start, start.plusMinutes(30));
      assertEquals(AppointmentStatus.PENDING, appointment.status());

      appointment = services.appointmentService().accept(doctorSession, appointment.id());
      appointment = services.appointmentService().checkIn(receptionistSession, appointment.id());
      services
          .clinicalService()
          .saveConsultation(
              doctorSession,
              appointment.id(),
              "Seasonal allergies",
              "Discussed treatment options",
              "Review in two weeks");
      services
          .clinicalService()
          .addPrescription(
              doctorSession,
              appointment.id(),
              "Cetirizine",
              "10 mg",
              "Once daily",
              "14 days",
              "Take in the evening");
      appointment = services.appointmentService().complete(doctorSession, appointment.id());
      assertEquals(AppointmentStatus.COMPLETED, appointment.status());

      var payment =
          services
              .billingService()
              .checkout(receptionistSession, appointment.id(), 4500, PaymentMethod.CARD);
      assertEquals(4500, payment.amountMinor());
      assertEquals(
          AppointmentStatus.CHECKED_OUT,
          services.appointmentService().get(appointment.id()).status());
      assertEquals(
          1,
          services
              .billingService()
              .dailyRevenue(receptionistSession, LocalDate.now())
              .transactionCount());
      assertEquals(
          4500,
          services
              .billingService()
              .dailyRevenue(receptionistSession, LocalDate.now())
              .totalMinor());

      var clinicalBefore =
          services.clinicalService().findForDoctor(doctorSession, appointment.id()).orElseThrow();
      var prescriptionsBefore =
          services.clinicalService().prescriptionsForDoctor(doctorSession, appointment.id());
      Patient updated =
          services
              .patientService()
              .updateAdministrative(
                  receptionistSession,
                  new Patient(
                      patient.id(),
                      patient.identityType(),
                      patient.identityNumber(),
                      patient.issuingCountry(),
                      patient.firstName(),
                      patient.lastName(),
                      patient.dateOfBirth(),
                      patient.sex(),
                      "+442071234567",
                      patient.email(),
                      patient.address(),
                      "Updated billing",
                      patient.heightCm(),
                      patient.weightKg(),
                      true));
      services.patientService().deactivateAdministrative(receptionistSession, patient.id());

      assertEquals("+442071234567", updated.phone());
      assertEquals(
          clinicalBefore,
          services.clinicalService().findForDoctor(doctorSession, appointment.id()).orElseThrow());
      assertEquals(
          prescriptionsBefore,
          services.clinicalService().prescriptionsForDoctor(doctorSession, appointment.id()));

      long appointmentId = appointment.id();
      assertThrows(
          AuthorizationException.class,
          () -> services.clinicalService().findForDoctor(receptionistSession, appointmentId));
      assertThrows(
          AuthorizationException.class,
          () -> services.clinicalService().findForDoctor(adminSession, appointmentId));
      assertEquals(1, services.patientService().listAdministrative(receptionistSession).size());
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("workflow.db"));
    database.open();
    return database;
  }
}
