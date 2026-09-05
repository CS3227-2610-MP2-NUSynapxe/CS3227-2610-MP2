package nusynapxe.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Non-clinical appointment projection used by the Doctor Calendar.
 *
 * @param appointmentId appointment identifier
 * @param patientId assigned patient identifier
 * @param patientDisplayName patient name safe for administrative display
 * @param startsAt local appointment start timestamp
 * @param endsAt local appointment end timestamp
 * @param status appointment lifecycle status
 */
public record CalendarAppointment(
    long appointmentId,
    long patientId,
    String patientDisplayName,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    AppointmentStatus status) {
  /**
   * Validates the administrative appointment projection.
   *
   * @throws IllegalArgumentException if an identifier is not positive, the patient name is blank,
   *     or the end timestamp does not follow the start timestamp
   * @throws NullPointerException if a required value is {@code null}
   */
  public CalendarAppointment {
    if (appointmentId <= 0 || patientId <= 0) {
      throw new IllegalArgumentException("Appointment and patient identifiers must be positive");
    }
    if (patientDisplayName == null || patientDisplayName.isBlank()) {
      throw new IllegalArgumentException("Patient display name is required");
    }
    Objects.requireNonNull(startsAt, "startsAt");
    Objects.requireNonNull(endsAt, "endsAt");
    if (!endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("Appointment must end after it starts");
    }
    Objects.requireNonNull(status, "status");
  }
}
