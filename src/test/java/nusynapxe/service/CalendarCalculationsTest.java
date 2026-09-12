package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarTimeSegment.SegmentKind;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.WorkingInterval;
import org.junit.jupiter.api.Test;

final class CalendarCalculationsTest {
  @Test
  void classifiesElapsedWorkingAndBreakPeriods() {
    DoctorCalendarSettings settings = settings();
    LocalDate day = LocalDate.of(2026, 9, 7);
    LocalDateTime now = LocalDateTime.of(2026, 9, 7, 10, 15);

    var segments = CalendarCalculations.segmentsForDay(day, settings, now);

    assertEquals(SegmentKind.ELAPSED, segments.get(0).kind());
    assertEquals(SegmentKind.WORKING, segments.get(1).kind());
    assertTrue(
        segments.stream()
            .anyMatch(
                segment ->
                    segment.kind() == SegmentKind.NON_WORKING
                        && segment.startMinute() <= 12 * 60
                        && segment.endMinute() >= 13 * 60));
    assertEquals(615, CalendarCalculations.currentMinute(day, now));
    assertEquals(-1, CalendarCalculations.currentMinute(day.plusDays(1), now));
  }

  @Test
  void placesOverlappingAndCrossMidnightAppointmentsInSeparateLanes() {
    LocalDate day = LocalDate.of(2026, 9, 7);
    CalendarAppointment first = appointment(1, 9, 0, 10, 0, AppointmentStatus.PENDING);
    CalendarAppointment second = appointment(2, 9, 30, 10, 30, AppointmentStatus.CANCELLED);
    CalendarAppointment overnight =
        new CalendarAppointment(
            3,
            3,
            "P000003 - Night Patient",
            day.minusDays(1).atTime(23, 30),
            day.atTime(0, 30),
            AppointmentStatus.ACCEPTED);

    var blocks = CalendarCalculations.blocksForDay(day, List.of(first, second, overnight));

    assertEquals(3, blocks.size());
    var firstBlock =
        blocks.stream().filter(block -> block.appointment().appointmentId() == 1).findFirst();
    var secondBlock =
        blocks.stream().filter(block -> block.appointment().appointmentId() == 2).findFirst();
    var overnightBlock =
        blocks.stream().filter(block -> block.appointment().appointmentId() == 3).findFirst();
    assertEquals(2, firstBlock.orElseThrow().laneCount());
    assertEquals(2, secondBlock.orElseThrow().laneCount());
    assertEquals(0, overnightBlock.orElseThrow().startMinute());
    assertEquals(30, overnightBlock.orElseThrow().endMinute());
  }

  @Test
  void clipsTimeOffAtDayBoundariesAndPreservesDuration() {
    LocalDate day = LocalDate.of(2026, 9, 7);
    DoctorTimeOff overnight =
        new DoctorTimeOff(1, 7, day.minusDays(1).atTime(23, 30), day.atTime(0, 30));
    DoctorTimeOff oneHour = new DoctorTimeOff(2, 7, day.atTime(9, 0), day.atTime(10, 0));
    DoctorTimeOff adjacent =
        new DoctorTimeOff(3, 7, day.plusDays(1).atStartOfDay(), day.plusDays(1).atTime(0, 30));

    var blocks =
        CalendarCalculations.timeOffBlocksForDay(day, List.of(overnight, oneHour, adjacent));

    assertEquals(2, blocks.size());
    assertEquals(0, blocks.get(0).startMinute());
    assertEquals(30, blocks.get(0).endMinute());
    assertEquals(60, blocks.get(1).endMinute() - blocks.get(1).startMinute());
  }

  @Test
  void choosesCurrentEventWorkingAndEmptyInitialScrollTargets() {
    LocalDate monday = LocalDate.of(2026, 9, 7);
    DoctorCalendarWeek emptyMonday =
        new DoctorCalendarWeek(1, monday, settings(), List.of(), List.of());
    assertEquals(
        9 * 60,
        CalendarCalculations.initialScrollMinute(
            List.of(monday), emptyMonday, monday.atTime(10, 0)));

    CalendarAppointment appointment = appointment(1, 9, 0, 9, 30, AppointmentStatus.ACCEPTED);
    DoctorTimeOff earlierTimeOff =
        new DoctorTimeOff(2, 1, monday.atTime(8, 0), monday.atTime(8, 30));
    DoctorCalendarWeek eventDay =
        new DoctorCalendarWeek(
            1, monday, settings(), List.of(appointment), List.of(earlierTimeOff));
    assertEquals(
        7 * 60 + 30,
        CalendarCalculations.initialScrollMinute(
            List.of(monday), eventDay, monday.plusDays(1).atTime(10, 0)));

    assertEquals(
        8 * 60,
        CalendarCalculations.initialScrollMinute(
            List.of(monday), emptyMonday, monday.plusDays(1).atTime(10, 0)));
    LocalDate sunday = monday.plusDays(6);
    DoctorCalendarWeek emptySunday =
        new DoctorCalendarWeek(1, sunday, settings(), List.of(), List.of());
    assertEquals(
        0,
        CalendarCalculations.initialScrollMinute(
            List.of(sunday), emptySunday, monday.atTime(10, 0)));
  }

  private DoctorCalendarSettings settings() {
    EnumMap<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    intervals.put(
        DayOfWeek.MONDAY,
        List.of(new WorkingInterval(8 * 60, 12 * 60), new WorkingInterval(13 * 60, 18 * 60)));
    return new DoctorCalendarSettings(1, DayOfWeek.MONDAY, intervals);
  }

  private CalendarAppointment appointment(
      long id,
      int startHour,
      int startMinute,
      int endHour,
      int endMinute,
      AppointmentStatus status) {
    LocalDate day = LocalDate.of(2026, 9, 7);
    return new CalendarAppointment(
        id,
        id,
        "P%06d - Patient %d".formatted(id, id),
        day.atTime(startHour, startMinute),
        day.atTime(endHour, endMinute),
        status);
  }
}
