package nusynapxe.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One day's clipped portion of a Doctor time-off interval.
 *
 * @param timeOff source Doctor-owned interval
 * @param day displayed calendar date
 * @param startMinute inclusive minute within the displayed day
 * @param endMinute exclusive minute within the displayed day
 */
public record CalendarTimeOffBlock(
    DoctorTimeOff timeOff, LocalDate day, int startMinute, int endMinute) {
  /** Validates that the visible block lies within one civil day. */
  public CalendarTimeOffBlock {
    Objects.requireNonNull(timeOff, "timeOff");
    Objects.requireNonNull(day, "day");
    if (startMinute < 0
        || endMinute > WorkingInterval.MINUTES_PER_DAY
        || endMinute <= startMinute) {
      throw new IllegalArgumentException("Time-off block must be inside one day");
    }
  }
}
