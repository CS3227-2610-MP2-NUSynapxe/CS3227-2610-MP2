package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalHistoryEntry;
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
  void repositoryTimestampsUseSingaporeDateAtUtcBoundary() throws SQLException {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-09-21T16:30:00Z"), ZoneOffset.UTC);
    try (SqliteDatabase database = openDatabase()) {
      new AccountRepository(database, fixedClock)
          .create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});

      try (var statement = database.connection().prepareStatement("SELECT created_at FROM users")) {
        try (var resultSet = statement.executeQuery()) {
          assertTrue(resultSet.next());
          assertEquals("2026-09-22T00:30:00", resultSet.getString(1));
        }
      }
    }
  }

  @Test
  void findsTerminalClinicalHistoryByPatientWithDoctorContextAndStableNewestFirstOrder()
      throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = accountRepository(database);
      Account doctor =
          accounts.create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
      Account otherDoctor =
          accounts.create("other", "Dr. Babbage", Role.DOCTOR, new byte[] {3}, new byte[] {4});
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
      Patient otherPatient =
          patients.create(
              new Patient(
                  0,
                  "Katherine",
                  "Johnson",
                  "1918-08-26",
                  "555-0101",
                  "katherine@example.test",
                  "2 Main Street"));
      AppointmentRepository appointments = new AppointmentRepository(database);
      Appointment older =
          appointments.create(
              patient.id(),
              doctor.id(),
              LocalDateTime.of(2026, 9, 1, 9, 0),
              LocalDateTime.of(2026, 9, 1, 9, 30),
              AppointmentStatus.COMPLETED);
      Appointment newer =
          appointments.create(
              patient.id(),
              otherDoctor.id(),
              LocalDateTime.of(2026, 9, 2, 9, 0),
              LocalDateTime.of(2026, 9, 2, 9, 30),
              AppointmentStatus.CHECKED_OUT);
      Appointment inProgress =
          appointments.create(
              patient.id(),
              doctor.id(),
              LocalDateTime.of(2026, 9, 3, 9, 0),
              LocalDateTime.of(2026, 9, 3, 9, 30),
              AppointmentStatus.CHECKED_IN);
      Appointment unrelated =
          appointments.create(
              otherPatient.id(),
              doctor.id(),
              LocalDateTime.of(2026, 9, 4, 9, 0),
              LocalDateTime.of(2026, 9, 4, 9, 30),
              AppointmentStatus.COMPLETED);

      ClinicalRecordRepository clinicalRecords = new ClinicalRecordRepository(database);
      ClinicalRecord olderRecord =
          clinicalRecords.save(
              new ClinicalRecord(
                  0,
                  patient.id(),
                  older.id(),
                  doctor.id(),
                  "Older diagnosis",
                  "Older notes",
                  "Older follow-up"));
      ClinicalRecord newerRecord =
          clinicalRecords.save(
              new ClinicalRecord(
                  0,
                  patient.id(),
                  newer.id(),
                  otherDoctor.id(),
                  "Newer diagnosis",
                  "Newer notes",
                  "Newer follow-up"));
      clinicalRecords.save(
          new ClinicalRecord(
              0,
              patient.id(),
              inProgress.id(),
              doctor.id(),
              "Draft diagnosis",
              "Draft notes",
              "Draft follow-up"));
      clinicalRecords.save(
          new ClinicalRecord(
              0,
              otherPatient.id(),
              unrelated.id(),
              doctor.id(),
              "Unrelated diagnosis",
              "Unrelated notes",
              "Unrelated follow-up"));
      Prescription prescription =
          clinicalRecords.addPrescription(
              new Prescription(
                  0, newerRecord.id(), "Medicine", "10 mg", "Daily", "7 days", "Take with food"));

      List<ClinicalHistoryEntry> history = clinicalRecords.findHistoryByPatient(patient.id());

      assertEquals(2, history.size());
      assertEquals(newer.id(), history.get(0).appointment().id());
      assertEquals("Dr. Babbage", history.get(0).doctorName());
      assertEquals(newerRecord, history.get(0).clinicalRecord());
      assertEquals(List.of(prescription), history.get(0).prescriptions());
      assertEquals(
          Map.of(newerRecord.id(), List.of(prescription)),
          clinicalRecords.findPrescriptionsByRecordIds(List.of(newerRecord.id())));
      assertEquals(older.id(), history.get(1).appointment().id());
      assertEquals("Dr. Ada", history.get(1).doctorName());
      assertEquals(olderRecord, history.get(1).clinicalRecord());
      assertTrue(clinicalRecords.findHistoryByPatient(otherPatient.id()).size() == 1);
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

      ReceiptRepository receipts = new ReceiptRepository(database);
      database.connection().setAutoCommit(false);
      try {
        receipts.create(
            database.connection(),
            payment.id(),
            appointment.id(),
            patient.id(),
            payment.amountMinor(),
            payment.method(),
            LocalDate.of(2026, 9, 1),
            LocalDateTime.of(2026, 9, 1, 10, 0));
        database.connection().commit();
      } finally {
        database.connection().setAutoCommit(true);
      }
      assertEquals(1, receipts.findAll("grace", doctor.id(), LocalDate.of(2026, 9, 1)).size());
      assertTrue(receipts.findAll("unknown", null, LocalDate.of(2026, 9, 1)).isEmpty());
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
