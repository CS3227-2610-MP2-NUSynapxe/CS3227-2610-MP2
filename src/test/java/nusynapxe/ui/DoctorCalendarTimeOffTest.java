package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.service.CalendarService;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** Verifies calendar time-off creation, validation, rendering, and removal. */
final class DoctorCalendarTimeOffTest extends DoctorCalendarViewTestSupport {
  @Test
  void doctorBlocksTimeFromCalendarAndRefreshKeepsTheSelectedRange() throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    LocalDate selectedDate = today().plusDays(2);
    interact(
        () -> {
          lookup("#doctor-calendar-from").queryAs(DatePicker.class).setValue(selectedDate);
          lookup("#doctor-calendar-to").queryAs(DatePicker.class).setValue(selectedDate);
        });
    fire("#doctor-calendar-refresh");
    assertEquals(
        selectedDate, lookup("#doctor-calendar-from").queryAs(DatePicker.class).getValue());

    fire("#doctor-calendar-block-time");
    waitForNode("#doctor-calendar-time-off-dialog-content");
    assertEquals(
        selectedDate,
        lookup("#doctor-calendar-time-off-dialog-date").queryAs(DatePicker.class).getValue());
    assertTrue(
        lookup("#doctor-calendar-time-off-dialog-date")
            .queryAs(DatePicker.class)
            .getStyleClass()
            .contains("compact-date-picker"));
    selectCombo("#doctor-calendar-time-off-dialog-start-hour", "10");
    selectCombo("#doctor-calendar-time-off-dialog-start-minute", "00");
    selectCombo("#doctor-calendar-time-off-dialog-end-hour", "11");
    selectCombo("#doctor-calendar-time-off-dialog-end-minute", "00");
    fire("#doctor-calendar-time-off-dialog-submit");

    var created =
        new AppointmentRepository(database)
            .findTimeOffByDoctor(doctorId).stream()
                .filter(interval -> interval.startsAt().equals(selectedDate.atTime(10, 0)))
                .findFirst()
                .orElseThrow();
    assertTrue(
        lookup("#doctor-calendar-time-off-" + created.id() + "-" + selectedDate)
            .tryQuery()
            .isPresent());
  }

  @Test
  void doctorCanOpenTimeOffDetailsForRemoval() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    interact(
        () ->
            lookup("#doctor-calendar-time-off-" + timeOffId + "-" + today())
                .query()
                .getOnMouseClicked()
                .handle(primaryClick()));

    waitForNode("#doctor-calendar-time-off-details");
    verifyThat("#doctor-calendar-time-off-remove", isVisible());
    fire("#doctor-calendar-time-off-close");
  }

  @Test
  void crossMidnightTimeOffDetailsShowBothDates() throws SQLException {
    LocalDate startDate = today();
    LocalDate endDate = startDate.plusDays(1);
    var crossMidnight =
        new AppointmentRepository(database)
            .createTimeOff(doctorId, startDate.atTime(23, 30), endDate.atTime(0, 30));

    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    interact(
        () ->
            lookup("#doctor-calendar-time-off-" + crossMidnight.id() + "-" + startDate)
                .query()
                .getOnMouseClicked()
                .handle(primaryClick()));

    waitForNode("#doctor-calendar-time-off-details");
    String interval =
        lookup("#doctor-calendar-time-off-details-interval").queryAs(Label.class).getText();
    assertTrue(interval.contains(startDate.toString()));
    assertTrue(interval.contains(endDate.toString()));
    assertTrue(interval.contains("23:30"));
    assertTrue(interval.contains("00:30"));
    fire("#doctor-calendar-time-off-close");
  }

  @Test
  void agendaBlockTimeUsesTheVisibleAnchorDate() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    selectCombo("#doctor-calendar-view-mode", "Agenda");
    fire("#doctor-calendar-next");
    LocalDate anchor =
        lookup("#doctor-calendar-schedule-date").queryAs(DatePicker.class).getValue();

    fire("#doctor-calendar-block-time");
    waitForNode("#doctor-calendar-time-off-dialog-content");
    assertEquals(
        anchor,
        lookup("#doctor-calendar-time-off-dialog-date").queryAs(DatePicker.class).getValue());
    fire("#doctor-calendar-time-off-dialog-cancel");
  }

  @Test
  void invalidTimeOffKeepsDialogInputForCorrection() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    fire("#doctor-calendar-block-time");
    waitForNode("#doctor-calendar-time-off-dialog-content");
    LocalDate chosen = today().plusDays(3);
    interact(
        () ->
            lookup("#doctor-calendar-time-off-dialog-date")
                .queryAs(DatePicker.class)
                .setValue(chosen));
    selectCombo("#doctor-calendar-time-off-dialog-start-hour", "11");
    selectCombo("#doctor-calendar-time-off-dialog-end-hour", "10");

    fire("#doctor-calendar-time-off-dialog-submit");

    assertTrue(lookup("#doctor-calendar-time-off-dialog-content").tryQuery().isPresent());
    assertEquals(
        chosen,
        lookup("#doctor-calendar-time-off-dialog-date").queryAs(DatePicker.class).getValue());
    verifyThat("#doctor-calendar-time-off-dialog-feedback", isVisible());
    fire("#doctor-calendar-time-off-dialog-cancel");
  }

  @Test
  void conflictingTimeOffKeepsDialogAvailableForCorrection() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    fire("#doctor-calendar-block-time");
    waitForNode("#doctor-calendar-time-off-dialog-content");
    interact(
        () ->
            lookup("#doctor-calendar-time-off-dialog-date")
                .queryAs(DatePicker.class)
                .setValue(today()));
    selectCombo("#doctor-calendar-time-off-dialog-start-hour", "15");
    selectCombo("#doctor-calendar-time-off-dialog-start-minute", "00");
    selectCombo("#doctor-calendar-time-off-dialog-end-hour", "15");
    selectCombo("#doctor-calendar-time-off-dialog-end-minute", "30");

    fire("#doctor-calendar-time-off-dialog-submit");

    verifyThat("#doctor-calendar-time-off-dialog-feedback", isVisible());
    assertTrue(lookup("#doctor-calendar-time-off-dialog-content").tryQuery().isPresent());
    fire("#doctor-calendar-time-off-dialog-cancel");
  }

  @Test
  void doctorCancelsThenConfirmsTimeOffRemoval() throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    openSeededTimeOff();

    Thread cancelledRemoval = new Thread(() -> fire("#doctor-calendar-time-off-remove"));
    cancelledRemoval.start();
    waitForNode("#doctor-calendar-time-off-remove-confirmation");
    fire("#doctor-calendar-time-off-remove-cancel");
    join(cancelledRemoval);
    assertTrue(
        new AppointmentRepository(database)
            .findTimeOffByDoctor(doctorId).stream()
                .anyMatch(interval -> interval.id() == timeOffId));

    Thread confirmedRemoval = new Thread(() -> fire("#doctor-calendar-time-off-remove"));
    confirmedRemoval.start();
    waitForNode("#doctor-calendar-time-off-remove-confirmation");
    fire("#doctor-calendar-time-off-remove-confirm");
    join(confirmedRemoval);
    assertTrue(
        new AppointmentRepository(database)
            .findTimeOffByDoctor(doctorId).stream()
                .noneMatch(interval -> interval.id() == timeOffId));
    assertTrue(
        lookup("#doctor-calendar-time-off-" + timeOffId + "-" + today()).tryQuery().isEmpty());
  }

  @Test
  void blockedTimeConsumesClicksWhenNoTimeOffHandlerExists() throws SQLException {
    LocalDate selectedDate = today();
    Session doctorSession = new Session(doctorId, "doctor", Role.DOCTOR);
    DoctorCalendarWeek data =
        services.calendarService().getRange(doctorSession, selectedDate, selectedDate);
    AtomicBoolean emptySlotOpened = new AtomicBoolean();
    CalendarTimeGrid grid =
        new CalendarTimeGrid(
            List.of(selectedDate),
            data,
            Clock.system(CalendarService.CLINIC_ZONE),
            new CalendarTimeGrid.InteractionHandlers(
                null, null, start -> emptySlotOpened.set(true), null));

    interact(
        () -> {
          Stage stage = (Stage) lookup("#login-view").query().getScene().getWindow();
          stage.getScene().setRoot(grid);
          grid.applyCss();
          grid.layout();
          Node blockedTime =
              grid.lookup("#doctor-calendar-time-off-" + timeOffId + "-" + selectedDate);
          blockedTime.fireEvent(primaryClick());
        });

    assertFalse(emptySlotOpened.get());
  }

  @Test
  void lateUsefulTimeUsesTheScrollableContentHeight() throws SQLException {
    LocalDate selectedDate = today().plusDays(20);
    new AppointmentRepository(database)
        .createTimeOff(
            doctorId, selectedDate.atTime(23, 30), selectedDate.plusDays(1).atTime(0, 30));
    Session doctorSession = new Session(doctorId, "doctor", Role.DOCTOR);
    DoctorCalendarWeek data =
        services.calendarService().getRange(doctorSession, selectedDate, selectedDate);
    CalendarTimeGrid timeline =
        new CalendarTimeGrid(
            List.of(selectedDate),
            data,
            Clock.system(CalendarService.CLINIC_ZONE),
            CalendarTimeGrid.InteractionHandlers.none(),
            CalendarTimeGrid.DisplayProfile.COMPACT);

    interact(
        () -> {
          Stage stage = (Stage) lookup("#login-view").query().getScene().getWindow();
          stage.getScene().setRoot(timeline);
          timeline.applyCss();
          timeline.layout();
        });
    ScrollPane scroll = (ScrollPane) timeline.lookup("#doctor-calendar-scroll");
    timeline.scrollToUsefulTime(selectedDate.atTime(23, 59));
    WaitForAsyncUtils.waitForFxEvents();

    assertTrue(scroll.getVvalue() > 0.99, "Late-day content should scroll to the bottom");
  }

  @Test
  void crossMidnightTimeOffUsesClippedTimesInItsLabels() throws SQLException {
    LocalDate startDate = today();
    LocalDate nextDate = startDate.plusDays(1);
    var crossMidnight =
        new AppointmentRepository(database)
            .createTimeOff(doctorId, startDate.atTime(23, 0), nextDate.atTime(1, 0));

    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    Node clippedBlock =
        lookup("#doctor-calendar-time-off-" + crossMidnight.id() + "-" + nextDate).query();
    Label clippedTime = (Label) clippedBlock.lookup(".calendar-time-off-time");

    assertEquals("00:00 – 01:00", clippedTime.getText());
    assertEquals("Blocked time, 00:00 to 01:00", clippedBlock.getAccessibleText());
  }

  @Test
  void invalidCalendarRangeShowsFeedbackInsteadOfOpeningTimeOffDialog() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    interact(() -> lookup("#doctor-calendar-from").queryAs(DatePicker.class).setValue(null));

    fire("#doctor-calendar-block-time");

    verifyThat("#doctor-feedback", isVisible());
    assertTrue(lookup("#doctor-feedback").queryAs(Label.class).getText().contains("Select both"));
    assertTrue(lookup("#doctor-calendar-time-off-dialog-content").tryQuery().isEmpty());
  }

  @Test
  void settingsSaveStaysDisabledWhenInitialLoadFails() {
    Session invalidSession = new Session(doctorId, "doctor", Role.RECEPTIONIST);
    Runnable noOp =
        () -> {
          // No callback is needed for this load-failure test.
        };
    DoctorCalendarSettingsView settings =
        new DoctorCalendarSettingsView(services, invalidSession, noOp, noOp, new Label());

    interact(
        () -> {
          Stage stage = (Stage) lookup("#login-view").query().getScene().getWindow();
          stage.getScene().setRoot(settings.view());
        });

    assertTrue(
        lookup("#doctor-calendar-settings-save").queryAs(Button.class).isDisabled(),
        "Settings must not be saved when the initial snapshot was unavailable");
  }
}
