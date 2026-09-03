package nusynapxe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarScheduleDomainTest {

  @Test
  void cursorAndPageValidateStableBoundedPagingValues() {
    LocalDateTime startsAt = LocalDateTime.of(2026, 9, 3, 9, 0);
    CalendarAppointment appointment = appointment(7, startsAt);
    CalendarScheduleCursor cursor = new CalendarScheduleCursor(startsAt, 7);

    CalendarSchedulePage page = new CalendarSchedulePage(List.of(appointment), cursor, true);

    assertEquals(List.of(appointment), page.appointments());
    assertEquals(cursor, page.nextCursor());
    CalendarSchedulePage.validatePageSize(CalendarSchedulePage.DEFAULT_PAGE_SIZE);

    CalendarSchedulePage terminalPage = new CalendarSchedulePage(List.of(), null, false);
    assertTrue(terminalPage.appointments().isEmpty());
    assertFalse(terminalPage.hasMore());
  }

  @Test
  void cursorAndPageRejectInvalidValues() {
    LocalDateTime startsAt = LocalDateTime.of(2026, 9, 3, 9, 0);
    CalendarAppointment appointment = appointment(7, startsAt);

    assertThrows(NullPointerException.class, () -> new CalendarScheduleCursor(null, 7));
    assertThrows(IllegalArgumentException.class, () -> new CalendarScheduleCursor(startsAt, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new CalendarSchedulePage(List.of(), null, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CalendarSchedulePage(List.of(appointment), null, true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CalendarSchedulePage(
                List.of(appointment),
                new CalendarScheduleCursor(startsAt.plusMinutes(1), 7),
                true));
    assertThrows(IllegalArgumentException.class, () -> CalendarSchedulePage.validatePageSize(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> CalendarSchedulePage.validatePageSize(CalendarSchedulePage.MAX_PAGE_SIZE + 1));
  }

  @Test
  void scheduleGroupCopiesAppointmentsAndRejectsMixedDates() {
    LocalDate date = LocalDate.of(2026, 9, 3);
    CalendarAppointment appointment = appointment(7, date.atTime(9, 0));
    CalendarScheduleGroup group = new CalendarScheduleGroup(date, List.of(appointment));

    assertEquals(List.of(appointment), group.appointments());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CalendarScheduleGroup(
                date, List.of(appointment(8, date.plusDays(1).atTime(9, 0)))));
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
