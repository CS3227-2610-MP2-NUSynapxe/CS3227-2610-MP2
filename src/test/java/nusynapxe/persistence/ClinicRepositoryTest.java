package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClinicRepositoryTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void persistsAdministrativeAndClinicalRecordsSeparately() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor =
          accountRepository(database)
              .create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
      Account receptionist =
          accountRepository(database)
              .create("reception", "Reception", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4});
      PatientRepository patients = new PatientRepository(database);
      Patient patient =
          patients.create(
              new Patient(
                  0,
                  "Grace",
                  "Hopper",
                  "1906-12-09",
                  "555-0100",
                  "grace@example.test",
                  "1 Main Street"));
      Appointment appointment =
          new AppointmentRepository(database)
              .create(
                  patient.id(),
                  doctor.id(),
                  LocalDateTime.of(2026, 9, 1, 9, 0),
                  LocalDateTime.of(2026, 9, 1, 9, 30),
                  AppointmentStatus.ACCEPTED);
      ClinicalRecord record =
          new ClinicalRecordRepository(database)
              .save(
                  new ClinicalRecord(
                      0,
                      patient.id(),
                      appointment.id(),
                      doctor.id(),
                      "Migraine",
                      "Rest",
                      "Review in one week"));
      Prescription prescription =
          new ClinicalRecordRepository(database)
              .addPrescription(
                  new Prescription(
                      0, record.id(), "Medicine", "10 mg", "Daily", "7 days", "Take with food"));

      assertEquals(patient, patients.findById(patient.id()).orElseThrow());
      assertEquals(
          record,
          new ClinicalRecordRepository(database).findByAppointment(appointment.id()).orElseThrow());
      assertEquals(
          prescription,
          new ClinicalRecordRepository(database).findPrescriptions(record.id()).get(0));
      assertEquals(receptionist.role(), Role.RECEPTIONIST);
    }
  }

  @Test
  void persistsPaymentsAndAggregatesSuccessfulRevenue() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = accountRepository(database);
      Account doctor =
          accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
      Account receptionist =
          accounts.create(
              "reception", "Reception", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4});
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
                      "1 Main Street"));
      Appointment appointment =
          new AppointmentRepository(database)
              .create(
                  patient.id(),
                  doctor.id(),
                  LocalDateTime.of(2026, 9, 1, 9, 0),
                  LocalDateTime.of(2026, 9, 1, 9, 30),
                  AppointmentStatus.COMPLETED);
      PaymentRepository payments = new PaymentRepository(database);
      Payment payment =
          payments.create(
              new Payment(
                  0,
                  appointment.id(),
                  patient.id(),
                  receptionist.id(),
                  2500,
                  PaymentMethod.CARD,
                  PaymentStatus.SUCCESSFUL,
                  LocalDateTime.of(2026, 9, 1, 10, 0)));

      RevenueSummary summary = payments.revenueFor(LocalDate.of(2026, 9, 1));

      assertEquals(payment, payments.findByAppointment(appointment.id()).orElseThrow());
      assertEquals(1, summary.transactionCount());
      assertEquals(2500, summary.totalMinor());
      assertTrue(summary.date().isEqual(LocalDate.of(2026, 9, 1)));
    }
  }

  @Test
  void searchesAppointmentsByDateDoctorPatientAndStatus() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = accountRepository(database);
      Account doctor =
          accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
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
                      "1 Main Street"));
      AppointmentRepository appointments = new AppointmentRepository(database);
      appointments.create(
          patient.id(),
          doctor.id(),
          LocalDateTime.of(2026, 9, 1, 9, 0),
          LocalDateTime.of(2026, 9, 1, 9, 30),
          AppointmentStatus.PENDING);
      assertEquals(
          1,
          appointments
              .search(LocalDate.of(2026, 9, 1), doctor.id(), "grace", AppointmentStatus.PENDING)
              .size());
      assertTrue(appointments.search(LocalDate.of(2026, 9, 2), null, null, null).isEmpty());
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clinic.db"));
    database.open();
    return database;
  }

  private static AccountRepository accountRepository(SqliteDatabase database) {
    return new AccountRepository(database);
  }
}
