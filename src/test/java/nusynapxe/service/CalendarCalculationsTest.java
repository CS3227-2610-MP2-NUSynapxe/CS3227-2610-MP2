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
