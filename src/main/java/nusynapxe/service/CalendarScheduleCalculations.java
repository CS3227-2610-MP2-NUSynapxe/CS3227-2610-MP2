package nusynapxe.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleGroup;

/** Pure calculations shared by the doctor's schedule view and its tests. */
public final class CalendarScheduleCalculations {

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter CROSS_MIDNIGHT_END_FORMATTER =
      DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

  private CalendarScheduleCalculations() {
    // Utility class.
  }

  /**
   * Returns the current clinic date in Singapore.
   *
   * @param clock source of the current instant
   * @return current Singapore-local date
   * @throws NullPointerException if {@code clock} is {@code null}
   */
  public static LocalDate today(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return LocalDate.now(clock.withZone(CalendarService.CLINIC_ZONE));
  }

  /**
   * Moves a schedule anchor by a whole number of seven-day periods.
   *
   * @param anchor starting schedule date
   * @param weekDelta number of weeks to move, positive or negative
   * @return moved schedule date
   * @throws NullPointerException if {@code anchor} is {@code null}
   * @throws ArithmeticException if the day offset overflows
   */
  public static LocalDate moveAnchor(LocalDate anchor, int weekDelta) {
    Objects.requireNonNull(anchor, "anchor");
    return anchor.plusDays(Math.multiplyExact((long) weekDelta, 7L));
  }

  /**
   * Sorts appointments by the repository's stable order and groups them by the date on which each
   * appointment starts.
   *
   * @param appointments appointments to sort and group
   * @return immutable groups in chronological start-date order
   * @throws NullPointerException if {@code appointments} is {@code null}
   */
  public static List<CalendarScheduleGroup> groupByDate(List<CalendarAppointment> appointments) {
    Objects.requireNonNull(appointments, "appointments");
    List<CalendarAppointment> sortedAppointments = new ArrayList<>(appointments);
    sortedAppointments.sort(
        Comparator.comparing(CalendarAppointment::startsAt)
            .thenComparingLong(CalendarAppointment::appointmentId));

    Map<LocalDate, List<CalendarAppointment>> grouped = new LinkedHashMap<>();
    for (CalendarAppointment appointment : sortedAppointments) {
      grouped
          .computeIfAbsent(appointment.startsAt().toLocalDate(), ignored -> new ArrayList<>())
          .add(appointment);
    }
    return grouped.entrySet().stream()
        .map(entry -> new CalendarScheduleGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  /**
   * Returns whether the appointment has completely elapsed at the supplied clinic time.
   *
   * @param appointment appointment to inspect
   * @param now current local clinic timestamp
   * @return {@code true} when the appointment ends at or before {@code now}
   * @throws NullPointerException if an argument is {@code null}
   */
  public static boolean isElapsed(CalendarAppointment appointment, LocalDateTime now) {
    Objects.requireNonNull(appointment, "appointment");
    Objects.requireNonNull(now, "now");
    return !appointment.endsAt().isAfter(now);
  }

  /**
   * Formats an appointment's time range. Cross-midnight appointments include the end date so the
   * schedule does not imply that they finish on the start date.
   *
   * @param appointment appointment to format
   * @return formatted local time range
   * @throws NullPointerException if {@code appointment} is {@code null}
   */
  public static String formatTimeRange(CalendarAppointment appointment) {
    Objects.requireNonNull(appointment, "appointment");
    String start = TIME_FORMATTER.format(appointment.startsAt());
    String end = TIME_FORMATTER.format(appointment.endsAt());
    if (appointment.startsAt().toLocalDate().equals(appointment.endsAt().toLocalDate())) {
      return start + " – " + end;
    }
    return start + " – " + CROSS_MIDNIGHT_END_FORMATTER.format(appointment.endsAt());
  }

  /**
   * Formats a date for a schedule section heading.
   *
   * @param date date to format
   * @return full English date label
   * @throws NullPointerException if {@code date} is {@code null}
   */
  public static String formatGroupDate(LocalDate date) {
    Objects.requireNonNull(date, "date");
    return date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.ENGLISH));
  }
}
