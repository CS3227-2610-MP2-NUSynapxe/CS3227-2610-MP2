package nusynapxe.domain;

import java.time.LocalDate;
import java.util.Objects;

/** One day's visible portion of a Calendar appointment and its overlap lane. */
public record CalendarAppointmentBlock(
    CalendarAppointment appointment,
    LocalDate day,
    int startMinute,
    int endMinute,
    int lane,
    int laneCount) {
  /** Validates the visible appointment geometry. */
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
