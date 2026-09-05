package nusynapxe.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One day's visible portion of a Calendar appointment and its overlap lane.
 *
 * @param appointment appointment being rendered
 * @param day calendar date represented by this block
 * @param startMinute inclusive start minute within {@code day}
 * @param endMinute exclusive end minute within {@code day}
 * @param lane zero-based overlap lane
 * @param laneCount total number of overlap lanes
 */
public record CalendarAppointmentBlock(
    CalendarAppointment appointment,
    LocalDate day,
    int startMinute,
    int endMinute,
    int lane,
    int laneCount) {
  /**
   * Validates the visible appointment geometry.
   *
   * @throws IllegalArgumentException if the block is outside a civil day or its lane is invalid
   * @throws NullPointerException if the appointment or day is {@code null}
   */
  public CalendarAppointmentBlock {
    Objects.requireNonNull(appointment, "appointment");
    Objects.requireNonNull(day, "day");
    if (startMinute < 0
        || endMinute > WorkingInterval.MINUTES_PER_DAY
        || endMinute <= startMinute) {
      throw new IllegalArgumentException("Appointment block must be inside one day");
    }
    if (lane < 0 || laneCount <= lane || laneCount <= 0) {
      throw new IllegalArgumentException("Appointment lane is invalid");
    }
  }
}
