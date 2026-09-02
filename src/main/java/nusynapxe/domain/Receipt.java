package nusynapxe.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable receipt projection for a successful checkout. */
public record Receipt(
    long id,
    long paymentId,
    long appointmentId,
    long patientId,
    String patientName,
    String doctorName,
    long amountMinor,
    PaymentMethod method,
    LocalDate receiptDate,
    long sequenceNumber,
    LocalDateTime recordedAt) {
  public Receipt {
    if (sequenceNumber <= 0) {
      throw new IllegalArgumentException("Receipt sequence must be positive");
    }
  }
}
