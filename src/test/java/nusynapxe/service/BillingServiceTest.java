package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.PaymentRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BillingServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneId.of("UTC"));

  @TempDir private Path temporaryDirectory;

  @Test
  void checkoutCommitsPaymentAndCheckedOutStateTogether() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      BillingService billing = billing(database, fixture);

      var payment =
          billing.checkout(
              fixture.receptionistSession(), fixture.appointment().id(), 2500, PaymentMethod.CARD);

      assertEquals(2500, payment.amountMinor());
      var receipts = billing.receiptHistory(fixture.receptionistSession(), "", null, null);
      assertEquals(1, receipts.size());
      assertEquals(1, receipts.get(0).sequenceNumber());
      assertThrows(
          ValidationException.class,
          () ->
              billing.checkout(
                  fixture.receptionistSession(),
                  fixture.appointment().id(),
                  2500,
                  PaymentMethod.CARD));
      assertEquals(
          AppointmentStatus.CHECKED_OUT,
          fixture.appointments().get(fixture.appointment().id()).status());
      assertEquals(
          2500,
          billing
              .dailyRevenue(fixture.receptionistSession(), LocalDate.of(2026, 9, 1))
              .totalMinor());
    }
  }

  @Test
  void rejectsInvalidAmountAndAppointmentsNotReadyForCheckout() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      BillingService billing = billing(database, fixture);

      assertThrows(
          ValidationException.class,
          () ->
              billing.checkout(
                  fixture.receptionistSession(),
                  fixture.appointment().id(),
                  0,
                  PaymentMethod.CASH));
      assertThrows(
          ValidationException.class,
          () ->
              billing.checkout(
                  fixture.receptionistSession(),
                  fixture.appointment().id(),
                  -1,
                  PaymentMethod.CASH));
      assertThrows(
          ValidationException.class,
          () ->
              billing.checkout(
                  fixture.receptionistSession(),
                  fixture.pendingAppointment().id(),
                  100,
                  PaymentMethod.CASH));
      assertEquals(
          0, new PaymentRepository(database).revenueFor(LocalDate.of(2026, 9, 1)).totalMinor());
    }
  }

  @Test
  void excludesUnsuccessfulPaymentAttemptsFromRevenue() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Fixture fixture = fixture(database);
      PaymentRepository payments = new PaymentRepository(database);
      payments.create(
          new nusynapxe.domain.Payment(
              0,
              fixture.pendingAppointment().id(),
              fixture.patient().id(),
              fixture.receptionist().id(),
              100,
              PaymentMethod.CASH,
              nusynapxe.domain.PaymentStatus.UNSUCCESSFUL,
              LocalDateTime.of(2026, 9, 1, 10, 0)));

      assertEquals(0, payments.revenueFor(LocalDate.of(2026, 9, 1)).transactionCount());
      assertEquals(0, payments.revenueFor(LocalDate.of(2026, 9, 1)).totalMinor());
    }
  }

  private BillingService billing(SqliteDatabase database, Fixture fixture) {
    return new BillingService(
        new PaymentRepository(database), fixture.appointmentService(), FIXED_CLOCK);
  }

  private Fixture fixture(SqliteDatabase database) throws SQLException {
    AccountRepository accounts = new AccountRepository(database);
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
                    "Address"));
    AppointmentRepository repository = new AppointmentRepository(database);
    Appointment appointment =
        repository.create(
            patient.id(),
            doctor.id(),
            LocalDateTime.of(2026, 9, 1, 9, 0),
            LocalDateTime.of(2026, 9, 1, 9, 30),
            AppointmentStatus.COMPLETED);
    Appointment pending =
        repository.create(
            patient.id(),
            doctor.id(),
            LocalDateTime.of(2026, 9, 1, 11, 0),
            LocalDateTime.of(2026, 9, 1, 11, 30),
            AppointmentStatus.PENDING);
    AppointmentService appointmentService =
        new AppointmentService(repository, accounts, new PatientRepository(database), FIXED_CLOCK);
    return new Fixture(
        appointment,
        pending,
        patient,
        receptionist,
        appointmentService,
        new Session(receptionist.id(), receptionist.username(), receptionist.role()));
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("billing.db"));
    database.open();
    return database;
  }

  private record Fixture(
      Appointment appointment,
      Appointment pendingAppointment,
      Patient patient,
      Account receptionist,
      AppointmentService appointmentService,
      Session receptionistSession) {
    private AppointmentService appointments() {
      return appointmentService;
    }
  }
}
