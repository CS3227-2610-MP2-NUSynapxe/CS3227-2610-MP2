package nusynapxe.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable receipt projection for a successful checkout.
 *
 * @param id receipt identifier
 * @param paymentId linked payment identifier
 * @param appointmentId linked appointment identifier
 * @param patientId linked patient identifier
 * @param patientName patient display name
 * @param doctorName doctor display name
 * @param amountMinor payment amount in minor currency units
 * @param method payment method
 * @param receiptDate Singapore-local receipt date
 * @param sequenceNumber daily receipt sequence number
 * @param recordedAt payment recording timestamp
 */
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
  /**
   * Validates the daily receipt sequence.
   *
   * @throws IllegalArgumentException if {@code sequenceNumber} is not positive
   */
  public Receipt {
    if (sequenceNumber <= 0) {
      throw new IllegalArgumentException("Receipt sequence must be positive");
    }
  }
}
