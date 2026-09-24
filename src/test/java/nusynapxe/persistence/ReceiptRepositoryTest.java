package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReceiptRepositoryTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void rangeOrdersDatesAscendingAndDailySequencesDescending() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      Account doctor =
          new AccountRepository(database)
              .create("doctor", "Dr. Ada", Role.DOCTOR, new byte[] {1}, new byte[] {2});
      Account receptionist =
          new AccountRepository(database)
              .create("reception", "Reception", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4});
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
      Appointment first =
          appointment(database, patient, doctor, LocalDateTime.of(2026, 9, 1, 9, 0));
      Appointment second =
          appointment(database, patient, doctor, LocalDateTime.of(2026, 9, 1, 10, 0));
      Appointment third =
          appointment(database, patient, doctor, LocalDateTime.of(2026, 9, 2, 9, 0));
      PaymentRepository payments = new PaymentRepository(database);

      payments.createCheckout(
          payment(first, patient, receptionist, LocalDateTime.of(2026, 9, 1, 9, 30)));
      payments.createCheckout(
          payment(second, patient, receptionist, LocalDateTime.of(2026, 9, 1, 10, 30)));
      payments.createCheckout(
          payment(third, patient, receptionist, LocalDateTime.of(2026, 9, 2, 9, 30)));

      List<Receipt> receipts =
          new ReceiptRepository(database)
              .findRange(
                  null, doctor.id(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), null);

      assertEquals(
          List.of("2026-09-01-2", "2026-09-01-1", "2026-09-02-1"),
          receipts.stream()
              .map(receipt -> receipt.receiptDate() + "-" + receipt.sequenceNumber())
              .toList());
    }
  }

  private Appointment appointment(
      SqliteDatabase database, Patient patient, Account doctor, LocalDateTime startsAt)
      throws SQLException {
    return new AppointmentRepository(database)
        .create(
            patient.id(),
            doctor.id(),
            startsAt,
            startsAt.plusMinutes(30),
            AppointmentStatus.COMPLETED);
  }

  private static Payment payment(
      Appointment appointment, Patient patient, Account receptionist, LocalDateTime recordedAt) {
    return new Payment(
        0,
        appointment.id(),
        patient.id(),
        receptionist.id(),
        2500,
        PaymentMethod.CARD,
        PaymentStatus.SUCCESSFUL,
        recordedAt);
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("receipts.db"));
    database.open();
    return database;
  }
}
