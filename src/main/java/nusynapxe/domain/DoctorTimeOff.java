package nusynapxe.domain;

import java.time.LocalDateTime;

/** An unavailable interval in a doctor's schedule. */
public record DoctorTimeOff(long id, long doctorId, LocalDateTime startsAt, LocalDateTime endsAt) {
  // Immutable availability projection.
}
