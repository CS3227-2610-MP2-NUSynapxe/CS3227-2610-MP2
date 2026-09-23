package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** Verifies calendar navigation, view switching, and toolbar layout behavior. */
final class DoctorCalendarNavigationTest extends DoctorCalendarViewTestSupport {
  @Test
  void navigatesCalendarPickerAndSettingsWithBreaks() throws SQLException {
    Session doctorSession = new Session(doctorId, "doctor", Role.DOCTOR);
    DoctorCalendarSettings initialSettings = services.calendarService().getSettings(doctorSession);
    services
        .calendarService()
        .saveSettings(
            doctorSession,
            new DoctorCalendarSettings(
                doctorId, DayOfWeek.MONDAY, initialSettings.workingIntervals()));

    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    verifyThat("#doctor-calendar-page", isVisible());
    assertEquals(7, lookup(".calendar-day-header").queryAll().size());
    assertTrue(lookup("#doctor-calendar-appointment-1-" + today()).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-time-off-" + timeOffId + "-" + today()).tryQuery().isPresent());
    assertEquals(5, lookup(".calendar-appointment-status").queryAll().size());
    assertTrue(
        lookup("#doctor-calendar-current-time-line-" + today()).queryAs(Region.class).isVisible());

    assertFalse(lookup("#doctor-calendar-schedule-date").queryAs(DatePicker.class).isVisible());
    assertFalse(lookup("#doctor-calendar-previous").queryAs(Button.class).isVisible());
    assertFalse(lookup("#doctor-calendar-next").queryAs(Button.class).isVisible());
    DatePicker from = lookup("#doctor-calendar-from").queryAs(DatePicker.class);
    DatePicker to = lookup("#doctor-calendar-to").queryAs(DatePicker.class);
    assertTrue(from.getStyleClass().contains("compact-date-picker"));
    assertTrue(to.getStyleClass().contains("compact-date-picker"));
    assertEquals(today(), from.getValue());
    assertEquals(today().plusDays(6), to.getValue());
    interact(
        () -> {
          from.setValue(today().plusDays(3));
          to.setValue(today().plusDays(3));
          to.getOnAction().handle(new javafx.event.ActionEvent());
        });
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals(1, lookup(".calendar-day-header").queryAll().size());
    fire("#doctor-calendar-today");
    assertEquals(7, lookup(".calendar-day-header").queryAll().size());
    assertEquals(today(), from.getValue());
    assertEquals(today().plusDays(6), to.getValue());

    fire("#doctor-calendar-settings");
    waitForNode("#doctor-calendar-settings-page");
    verifyThat("#doctor-calendar-settings-timezone", isVisible());
    assertTrue(lookup("#doctor-calendar-settings-work-location").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-calendar-settings-preferences").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-calendar-settings-first-day").tryQuery().isEmpty());
    verifyThat("#doctor-calendar-settings-working-hours", isVisible());
    fire("#doctor-calendar-settings-monday-add");
    selectCombo("#doctor-calendar-settings-monday-end-0", "12:00");
    selectCombo("#doctor-calendar-settings-monday-start-1", "13:00");
    selectCombo("#doctor-calendar-settings-monday-end-1", "18:00");
    fire("#doctor-calendar-settings-save");
    waitForNode("#doctor-calendar-page");
    verifyThat("#doctor-calendar-page", isVisible());

    fire("#doctor-calendar-settings");
    waitForNode("#doctor-calendar-settings-page");
    assertTrue(lookup("#doctor-calendar-settings-preferences").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-calendar-settings-first-day").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-calendar-settings-monday-start-1").tryQuery().isPresent());
    assertEquals(
        DayOfWeek.MONDAY, services.calendarService().getSettings(doctorSession).firstDayOfWeek());
    fire("#doctor-calendar-settings-cancel");
    waitForNode("#doctor-calendar-page");
  }

  @Test
  void switchesToAgendaAndNavigatesByOneDayAnchor() throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    ComboBox<String> mode = calendarMode();
    assertEquals("Calendar", mode.getValue());
    assertEquals("Choose Calendar view", mode.getAccessibleText());
    interact(() -> mode.setValue("Agenda"));
    WaitForAsyncUtils.waitForFxEvents();

    waitForNode("#doctor-calendar-schedule-list");
    DatePicker anchor = lookup("#doctor-calendar-schedule-date").queryAs(DatePicker.class);
    assertEquals(today(), anchor.getValue());
    assertEquals("Choose Agenda start date", anchor.getAccessibleText());
    ListView<?> schedule = scheduleList();
    assertEquals(46, schedule.getItems().size());
    assertTrue(lookup("#doctor-calendar-schedule-date-" + today()).tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-schedule-appointment-1").tryQuery().isPresent());
    assertTrue(lookup(".calendar-schedule-status").tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-schedule-date-" + today())
            .query()
            .getStyleClass()
            .contains("calendar-schedule-today"));
    assertEquals("Chronological future appointments", schedule.getAccessibleText());
    List<AppointmentStatus> displayedStatuses =
        List.of(
            AppointmentStatus.PENDING,
            AppointmentStatus.ACCEPTED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CHECKED_OUT);
    List<Integer> displayedAppointmentIds = List.of(1, 2, 4, 5, 6);
    for (int index = 0; index < displayedStatuses.size(); index++) {
      int rowIndex = index + 1;
      int appointmentId = displayedAppointmentIds.get(index);
      interact(() -> schedule.scrollTo(rowIndex));
      WaitForAsyncUtils.waitForFxEvents();
      waitForNode("#doctor-calendar-schedule-status-" + appointmentId);
      assertEquals(
          UiComponents.humanizeStatus(displayedStatuses.get(index).name()),
          lookup("#doctor-calendar-schedule-status-" + appointmentId)
              .queryAs(Label.class)
              .getText());
    }
    assertTrue(
        lookup("#doctor-calendar-schedule-appointment-6")
            .query()
            .getStyleClass()
            .contains("calendar-schedule-status-checked-out"));
    assertFalse(lookup("#doctor-calendar-schedule-appointment-3").tryQuery().isPresent());
    assertFalse(lookup("#doctor-calendar-schedule-appointment-7").tryQuery().isPresent());

    interact(
        () -> {
          schedule.applyCss();
          schedule.layout();
          schedule.scrollTo(schedule.getItems().size() - 1);
        });
    waitForScheduleSize(46);
    verifyThat("#doctor-calendar-schedule-end", isVisible());

    int loadedScheduleEntryCount = scheduleList().getItems().size();
    fire("#doctor-calendar-next");
    assertEquals(today().plusDays(1), anchor.getValue());
    assertEquals(50, scheduleList().getItems().size());
    assertNotEquals(loadedScheduleEntryCount, scheduleList().getItems().size());
    fire("#doctor-calendar-previous");
    assertEquals(today(), anchor.getValue());
    assertEquals(46, scheduleList().getItems().size());
    fire("#doctor-calendar-today");
    assertEquals(today(), anchor.getValue());
    assertEquals(46, scheduleList().getItems().size());

    LocalDate targetDate = today().plusDays(8);
    interact(
        () -> {
          anchor.setValue(targetDate);
          anchor.getOnAction().handle(new javafx.event.ActionEvent());
        });
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals(50, scheduleList().getItems().size());
    assertTrue(lookup("#doctor-calendar-schedule-date-" + targetDate).tryQuery().isPresent());
    fire("#doctor-calendar-today");

    interact(() -> mode.setValue("Calendar"));
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals(7, lookup(".calendar-day-header").queryAll().size());
    assertTrue(lookup("#doctor-calendar-schedule-list").tryQuery().isEmpty());
    assertEquals(
        appointmentsBeforeSchedule, new AppointmentRepository(database).findByDoctor(doctorId));
  }

  @Test
  void refreshRetainsScheduleModeAndAnchor() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    ComboBox<String> mode = calendarMode();
    interact(() -> mode.setValue("Agenda"));
    fire("#doctor-calendar-next");
    DatePicker anchor = lookup("#doctor-calendar-schedule-date").queryAs(DatePicker.class);
    LocalDate anchorDate = anchor.getValue();

    fire("#doctor-calendar-refresh");

    assertEquals("Agenda", mode.getValue());
    assertEquals(anchorDate, anchor.getValue());
    assertTrue(lookup("#doctor-calendar-schedule-list").tryQuery().isPresent());
  }

  @Test
  void calendarToolbarWrapsSchedulingActionsAtNarrowWidths() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    interact(
        () -> {
          Stage stage = (Stage) lookup("#doctor-calendar-toolbar").query().getScene().getWindow();
          stage.setWidth(980);
          lookup("#doctor-calendar-toolbar").query().applyCss();
          lookup("#doctor-calendar-toolbar").queryAs(VBox.class).layout();
        });
    HBox actionGroup = lookup("#doctor-calendar-action-group").queryAs(HBox.class);
    assertEquals("doctor-calendar-toolbar-actions", actionGroup.getParent().getId());
    interact(
        () -> {
          Stage stage = (Stage) lookup("#doctor-calendar-toolbar").query().getScene().getWindow();
          stage.setWidth(1400);
          lookup("#doctor-calendar-toolbar").query().applyCss();
          lookup("#doctor-calendar-toolbar").queryAs(VBox.class).layout();
        });
    assertEquals("doctor-calendar-toolbar-main", actionGroup.getParent().getId());
  }
}
