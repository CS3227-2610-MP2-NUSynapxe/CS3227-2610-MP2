package nusynapxe.domain;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A normalized seven-day Calendar period ordered by a Doctor's preferred first day. */
public record CalendarWeek(LocalDate start, DayOfWeek firstDayOfWeek) {
  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Singapore");
  private static final DateTimeFormatter DAY_MONTH =
      DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

  /** Validates that the supplied start is the configured first day. */
  public CalendarWeek {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek");
    if (start.getDayOfWeek() != firstDayOfWeek) {
      throw new IllegalArgumentException("Week start must match the preferred first day");
    }
  }

  /** Returns the preferred week containing a date. */
  public static CalendarWeek containing(LocalDate date, DayOfWeek firstDayOfWeek) {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek");
    int offset = Math.floorMod(date.getDayOfWeek().getValue() - firstDayOfWeek.getValue(), 7);
    return new CalendarWeek(date.minusDays(offset), firstDayOfWeek);
  }

  /** Returns the current Singapore clinic week for a clock. */
  public static CalendarWeek today(Clock clock, DayOfWeek firstDayOfWeek) {
    Objects.requireNonNull(clock, "clock");
    return containing(LocalDate.now(clock.withZone(CLINIC_ZONE)), firstDayOfWeek);
  }

  /** Returns the week immediately before this one. */
  public CalendarWeek previous() {
    return new CalendarWeek(start.minusDays(7), firstDayOfWeek);
  }

  /** Returns the week immediately after this one. */
  public CalendarWeek next() {
    return new CalendarWeek(start.plusDays(7), firstDayOfWeek);
  }

  /** Returns the seven dates in display order. */
  public List<LocalDate> dates() {
    return java.util.stream.IntStream.range(0, 7).mapToObj(start::plusDays).toList();
  }

  /** Returns the ISO-like configured week number for the visible week row. */
  public int weekNumber() {
    return start.get(WeekFields.of(firstDayOfWeek, 1).weekOfWeekBasedYear());
  }

  /** Returns a compact range label suitable for the Calendar toolbar. */
  public String label() {
    LocalDate end = start.plusDays(6);
    String startText = start.format(DAY_MONTH);
    String endText = end.format(DAY_MONTH);
    if (start.getYear() == end.getYear()) {
      return startText + " – " + endText + ", " + end.getYear() + " (Week " + weekNumber() + ")";
    }
    return startText
        + ", "
        + start.getYear()
        + " – "
        + endText
        + ", "
        + end.getYear()
        + " (Week "
        + weekNumber()
        + ")";
  }
}
