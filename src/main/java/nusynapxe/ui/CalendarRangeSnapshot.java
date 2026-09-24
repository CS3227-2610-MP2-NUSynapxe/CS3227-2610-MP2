package nusynapxe.ui;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable date range captured before a calendar task is submitted. */
record CalendarRangeSnapshot(LocalDate from, LocalDate to) {
  CalendarRangeSnapshot {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
  }

  static CalendarRangeSnapshot capture(LocalDate from, LocalDate to) {
    return new CalendarRangeSnapshot(from, to);
  }
}
