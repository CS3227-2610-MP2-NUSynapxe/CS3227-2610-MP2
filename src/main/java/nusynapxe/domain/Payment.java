package nusynapxe.domain;

import java.time.LocalDateTime;

/**
 * A checkout payment recorded for a completed appointment.
 *
 * @param id payment identifier
 * @param appointmentId paid appointment identifier
 * @param patientId patient identifier
 * @param receptionistId recording receptionist identifier
 * @param amountMinor amount in minor currency units
 * @param method payment method
 * @param status payment outcome
 * @param recordedAt local timestamp at which the payment was recorded
 */
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
