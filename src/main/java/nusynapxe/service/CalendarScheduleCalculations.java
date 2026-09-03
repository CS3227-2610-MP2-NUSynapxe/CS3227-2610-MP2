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

  /** Returns the current clinic date in Singapore. */
  public static LocalDate today(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return LocalDate.now(clock.withZone(CalendarService.CLINIC_ZONE));
  }

  /** Moves a schedule anchor by a whole number of seven-day periods. */
  public static LocalDate moveAnchor(LocalDate anchor, int weekDelta) {
    Objects.requireNonNull(anchor, "anchor");
    return anchor.plusDays(Math.multiplyExact((long) weekDelta, 7L));
  }

  /**
   * Sorts appointments by the repository's stable order and groups them by the date on which each
   * appointment starts.
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

  /** Returns whether the appointment has completely elapsed at the supplied clinic time. */
  public static boolean isElapsed(CalendarAppointment appointment, LocalDateTime now) {
    Objects.requireNonNull(appointment, "appointment");
    Objects.requireNonNull(now, "now");
    return !appointment.endsAt().isAfter(now);
  }

  /**
   * Formats an appointment's time range. Cross-midnight appointments include the end date so the
   * schedule does not imply that they finish on the start date.
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

  /** Formats a date for a schedule section heading. */
  public static String formatGroupDate(LocalDate date) {
    Objects.requireNonNull(date, "date");
    return date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.ENGLISH));
  }
}
