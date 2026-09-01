package nusynapxe.domain;

import java.time.LocalDateTime;

/** A checkout payment recorded for a completed appointment. */
public record Payment(
    long id,
    long appointmentId,
    long patientId,
    long receptionistId,
    long amountMinor,
    PaymentMethod method,
    PaymentStatus status,
    LocalDateTime recordedAt) {
  // Immutable payment projection.
}
