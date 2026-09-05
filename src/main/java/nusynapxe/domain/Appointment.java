package nusynapxe.domain;

import java.time.LocalDateTime;

/**
 * A scheduled patient visit assigned to one doctor.
 *
 * @param id appointment identifier
 * @param patientId assigned patient identifier
 * @param doctorId assigned doctor account identifier
 * @param startsAt local appointment start timestamp
 * @param endsAt local appointment end timestamp
 * @param status appointment lifecycle status
 */
public record Appointment(
    long id,
    long patientId,
    long doctorId,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    AppointmentStatus status) {
  // Immutable appointment projection.
}
