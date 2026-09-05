package nusynapxe.domain;

import java.time.LocalDateTime;

/**
 * An unavailable interval in a doctor's schedule.
 *
 * @param id time-off identifier
 * @param doctorId owning doctor identifier
 * @param startsAt local interval start timestamp
 * @param endsAt local interval end timestamp
 */
public record DoctorTimeOff(long id, long doctorId, LocalDateTime startsAt, LocalDateTime endsAt) {
  // Immutable availability projection.
}
