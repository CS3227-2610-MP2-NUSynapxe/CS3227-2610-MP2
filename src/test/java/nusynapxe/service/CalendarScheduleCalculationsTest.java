package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleGroup;
import org.junit.jupiter.api.Test;

class CalendarScheduleCalculationsTest {

  private static final ZoneId CLINIC_ZONE = CalendarService.CLINIC_ZONE;

  @Test
  void usesSingaporeDateAndMovesBySevenDays() {
    Clock fixedClock = clockAt(LocalDateTime.of(2026, 9, 3, 0, 15));

    assertEquals(LocalDate.of(2026, 9, 3), CalendarScheduleCalculations.today(fixedClock));
    assertEquals(
        LocalDate.of(2026, 8, 27),
        CalendarScheduleCalculations.moveAnchor(LocalDate.of(2026, 9, 3), -1));
    assertEquals(
        LocalDate.of(2026, 9, 10),
        CalendarScheduleCalculations.moveAnchor(LocalDate.of(2026, 9, 3), 1));
    assertEquals(
        LocalDate.of(2027, 1, 1),
        CalendarScheduleCalculations.moveAnchor(LocalDate.of(2026, 12, 25), 1));
    assertEquals(
        LocalDate.of(2025, 12, 31),
        CalendarScheduleCalculations.moveAnchor(LocalDate.of(2026, 1, 7), -1));
  }

  @Test
  void groupsInDeterministicStartAndIdOrder() {
    LocalDate firstDate = LocalDate.of(2026, 9, 3);
    CalendarAppointment later = appointment(9, firstDate.atTime(10, 0));
    CalendarAppointment earlier = appointment(7, firstDate.atTime(9, 0));
    CalendarAppointment sameStartHigherId = appointment(12, firstDate.atTime(9, 0));
    CalendarAppointment nextDate = appointment(13, firstDate.plusDays(1).atTime(8, 0));

    List<CalendarScheduleGroup> groups =
        CalendarScheduleCalculations.groupByDate(
            List.of(nextDate, later, sameStartHigherId, earlier));

    assertEquals(2, groups.size());
    assertEquals(firstDate, groups.get(0).date());
    assertEquals(List.of(earlier, sameStartHigherId, later), groups.get(0).appointments());
    assertEquals(nextDate, groups.get(1).appointments().get(0));
  }

  @Test
  void formatsCrossMidnightAndElapsedAppointmentsClearly() {
    CalendarAppointment crossMidnight =
        new CalendarAppointment(
            7,
            11,
            "P000011 - Test Patient",
            LocalDateTime.of(2026, 9, 3, 23, 30),
            LocalDateTime.of(2026, 9, 4, 0, 30),
            AppointmentStatus.ACCEPTED);

    assertEquals(
        "23:30 – 4 Sep 00:30", CalendarScheduleCalculations.formatTimeRange(crossMidnight));
    assertTrue(
        CalendarScheduleCalculations.isElapsed(crossMidnight, LocalDateTime.of(2026, 9, 4, 0, 30)));
    assertFalse(
        CalendarScheduleCalculations.isElapsed(crossMidnight, LocalDateTime.of(2026, 9, 4, 0, 29)));
    assertEquals(
        "Thursday, September 3, 2026",
        CalendarScheduleCalculations.formatGroupDate(LocalDate.of(2026, 9, 3)));
  }

  private static Clock clockAt(LocalDateTime dateTime) {
    return Clock.fixed(dateTime.atZone(CLINIC_ZONE).toInstant(), CLINIC_ZONE);
  }

  private static CalendarAppointment appointment(long id, LocalDateTime startsAt) {
    return new CalendarAppointment(
        id,
        11,
        "P000011 - Test Patient",
        startsAt,
        startsAt.plusMinutes(30),
        AppointmentStatus.PENDING);
  }
}
