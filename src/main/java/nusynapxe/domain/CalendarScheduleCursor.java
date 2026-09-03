package nusynapxe.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Stable keyset position for a doctor's schedule stream.
 *
 * <p>The pair is ordered in the same way as schedule appointments: by start time and then by
 * appointment id.
 */
public record CalendarScheduleCursor(LocalDateTime startsAt, long appointmentId) {

  public CalendarScheduleCursor {
    Objects.requireNonNull(startsAt, "startsAt");
    if (appointmentId <= 0) {
      throw new IllegalArgumentException("appointmentId must be positive");
    }
  }
}
