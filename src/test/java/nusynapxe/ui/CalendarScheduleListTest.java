package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class CalendarScheduleListTest extends ApplicationTest {
  private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 3);
  private static final LocalDate EMPTY_ANCHOR = LocalDate.of(2030, 1, 1);
  private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Singapore");

  private CalendarScheduleList schedule;
  private AtomicBoolean failNextPage;
  private AtomicInteger loadCalls;
  private AtomicInteger selectedAppointmentId;

  @Override
  public void start(Stage stage) {
    failNextPage = new AtomicBoolean();
    loadCalls = new AtomicInteger();
    selectedAppointmentId = new AtomicInteger();
    List<CalendarAppointment> firstAppointments = appointments(25);
    CalendarAppointment last = firstAppointments.get(firstAppointments.size() - 1);
    CalendarSchedulePage firstPage =
        new CalendarSchedulePage(
            firstAppointments,
            new CalendarScheduleCursor(last.startsAt(), last.appointmentId()),
            true);
    CalendarSchedulePage terminalPage =
        new CalendarSchedulePage(List.of(appointment(26, 19, 50)), null, false);
    CalendarSchedulePageLoader loader =
        (anchor, cursor, pageSize) -> loadPage(anchor, cursor, firstPage, terminalPage);
    schedule =
        new CalendarScheduleList(
            loader,
            ANCHOR,
            Clock.fixed(
                LocalDateTime.of(2026, 9, 3, 12, 0).atZone(CLINIC_ZONE).toInstant(), CLINIC_ZONE),
            appointment -> selectedAppointmentId.set((int) appointment.appointmentId()));
    Scene scene = new Scene(schedule, 900, 650);
    UiComponents.applyStylesheet(scene);
    stage.setScene(scene);
    stage.show();
  }

  @Test
  void failedLaterPageRetainsRowsAndRetryReachesEnd() {
    ListView<?> list = scheduleList();
    assertEquals(26, list.getItems().size());
    assertTrue(
        lookup("#doctor-calendar-schedule-date-" + ANCHOR)
            .query()
            .getStyleClass()
            .contains("calendar-schedule-today"));
    assertTrue(
        lookup("#doctor-calendar-schedule-appointment-1")
            .query()
            .getStyleClass()
            .contains("calendar-schedule-elapsed"));
    failNextPage.set(true);
    scrollToEnd(list);
    verifyThat("#doctor-calendar-schedule-error", isVisible());
    verifyThat("#doctor-calendar-schedule-retry", isVisible());
    assertEquals(
        "Schedule loading error",
        lookup("#doctor-calendar-schedule-error").query().getAccessibleText());
    assertEquals(
        "Retry loading schedule appointments",
        lookup("#doctor-calendar-schedule-retry").queryAs(Button.class).getAccessibleText());
    assertEquals(26, list.getItems().size());

    fire("#doctor-calendar-schedule-retry");
    verifyThat("#doctor-calendar-schedule-end", isVisible());
    assertEquals(
        "End of future appointments",
        lookup("#doctor-calendar-schedule-end").query().getAccessibleText());
    assertEquals(27, list.getItems().size());
    int callsAfterEnd = loadCalls.get();
    scrollToEnd(list);
    assertEquals(callsAfterEnd, loadCalls.get());
  }

  @Test
  void emptyAnchorShowsEmptyStateWithoutEndMarker() {
    interact(() -> schedule.reset(EMPTY_ANCHOR));
    WaitForAsyncUtils.waitForFxEvents();

    verifyThat("#doctor-calendar-schedule-empty", isVisible());
    assertTrue(scheduleList().getItems().isEmpty());
    assertFalse(lookup("#doctor-calendar-schedule-error").query().isVisible());
    assertFalse(lookup("#doctor-calendar-schedule-end").query().isVisible());
  }

  @Test
  void selectingAnAppointmentRowInvokesTheOwnerCallback() {
    interact(
        () -> {
          ListView<?> list = scheduleList();
          list.getSelectionModel().select(1);
          list.getOnMouseClicked()
              .handle(
                  new MouseEvent(
                      MouseEvent.MOUSE_CLICKED,
                      5,
                      5,
                      5,
                      5,
                      MouseButton.PRIMARY,
                      1,
                      false,
                      false,
                      false,
                      false,
                      true,
                      false,
                      false,
                      false,
                      false,
                      false,
                      null));
        });
    assertEquals(1, selectedAppointmentId.get());
  }

  @Test
  void disposeStopsFurtherLoadsAndClearsRows() {
    int callsBeforeDispose = loadCalls.get();
    interact(schedule::dispose);
    assertTrue(scheduleList().getItems().isEmpty());
    interact(
        () -> {
          schedule.updateCurrentTime(LocalDateTime.of(2026, 9, 3, 13, 0));
          schedule.reset(ANCHOR);
        });
    assertEquals(callsBeforeDispose, loadCalls.get());
    assertTrue(scheduleList().getItems().isEmpty());
  }

  private CalendarSchedulePage loadPage(
      LocalDate anchor,
      CalendarScheduleCursor cursor,
      CalendarSchedulePage firstPage,
      CalendarSchedulePage terminalPage)
      throws SQLException {
    loadCalls.incrementAndGet();
    if (EMPTY_ANCHOR.equals(anchor)) {
      return new CalendarSchedulePage(List.of(), null, false);
    }
    if (cursor == null) {
      return firstPage;
    }
    if (failNextPage.getAndSet(false)) {
      throw new SQLException("temporary schedule read failure");
    }
    return terminalPage;
  }

  private static List<CalendarAppointment> appointments(int count) {
    List<CalendarAppointment> appointments = new ArrayList<>();
    for (int index = 1; index <= count; index++) {
      appointments.add(appointment(index, 9, (index - 1) * 25));
    }
    return appointments;
  }

  private static CalendarAppointment appointment(long id, int hour, int minuteOffset) {
    LocalDateTime startsAt = ANCHOR.atTime(hour, 0).plusMinutes(minuteOffset);
    return new CalendarAppointment(
        id, 11, "Test Patient", startsAt, startsAt.plusMinutes(20), AppointmentStatus.PENDING);
  }

  @SuppressWarnings("unchecked")
  private ListView<?> scheduleList() {
    return lookup("#doctor-calendar-schedule-list").queryAs(ListView.class);
  }

  private void scrollToEnd(ListView<?> list) {
    interact(
        () -> {
          list.applyCss();
          list.layout();
          list.scrollTo(list.getItems().size() - 1);
        });
    waitForFxEvents();
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
    waitForFxEvents();
  }

  private void waitForFxEvents() {
    try {
      WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> true);
      WaitForAsyncUtils.waitForFxEvents();
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for JavaFX events", exception);
    }
  }
}
