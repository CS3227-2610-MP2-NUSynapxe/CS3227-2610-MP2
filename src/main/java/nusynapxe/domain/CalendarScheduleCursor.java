package nusynapxe.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Stable keyset position for a doctor's schedule stream.
 *
 * <p>The pair is ordered in the same way as schedule appointments: by start time and then by
 * appointment id.
 *
 * @param startsAt start timestamp of the last appointment on the page
 * @param appointmentId identifier of the last appointment on the page
 */
public record CalendarScheduleCursor(LocalDateTime startsAt, long appointmentId) {

  /**
   * Validates a keyset cursor.
   *
   * @throws NullPointerException if {@code startsAt} is {@code null}
   * @throws IllegalArgumentException if {@code appointmentId} is not positive
   */
  public CalendarScheduleCursor {
    Objects.requireNonNull(startsAt, "startsAt");
    if (appointmentId <= 0) {
      throw new IllegalArgumentException("appointmentId must be positive");
    }
  }
}
