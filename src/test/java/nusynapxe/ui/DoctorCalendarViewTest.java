package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class DoctorCalendarViewTest extends ApplicationTest {
  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;
  private ClinicServices services;
  private long doctorId;
  private List<Appointment> appointmentsBeforeSchedule;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("doctor-calendar-ui.db"));
    database.open();
    services = ClinicServices.forDatabase(database);
    Account admin =
        services.accountService().createInitialAdmin("admin", "Admin", "secure-pass".toCharArray());
    Session adminSession = new Session(admin.id(), admin.username(), Role.SYSTEM_ADMIN);
    Account doctor =
        services
            .accountService()
            .createStaff(
                adminSession, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray());
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Grace", "Hopper", "1906-12-09", "555-0100", "", ""));
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Singapore"));
    AppointmentRepository appointments = new AppointmentRepository(database);
    AppointmentStatus[] statuses = AppointmentStatus.values();
    for (int index = 0; index < statuses.length; index++) {
      LocalDateTime start = today.atTime(8 + index, 0);
      int durationMinutes = index == 1 ? 60 : 30;
      appointments.create(
          patient.id(), doctor.id(), start, start.plusMinutes(durationMinutes), statuses[index]);
    }
    for (int index = 0; index < 30; index++) {
      LocalDateTime start = today.plusDays(index + 8).atTime(9, 0);
      appointments.create(
          patient.id(), doctor.id(), start, start.plusMinutes(30), AppointmentStatus.PENDING);
    }
    doctorId = doctor.id();
    appointmentsBeforeSchedule = new AppointmentRepository(database).findByDoctor(doctorId);
    new ApplicationRouter(stage, database).showInitial();
    stage.show();
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void navigatesCalendarPickerAndSettingsWithBreaks() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    verifyThat("#doctor-calendar-page", isVisible());
    assertEquals(7, lookup(".calendar-day-header").queryAll().size());
    assertTrue(lookup("#doctor-calendar-appointment-1-" + today()).tryQuery().isPresent());
    assertEquals(5, lookup(".calendar-appointment-status").queryAll().size());
    assertTrue(
        lookup("#doctor-calendar-current-time-line-" + today()).queryAs(Region.class).isVisible());

    String currentRange = lookup("#doctor-calendar-week-picker").queryAs(Button.class).getText();
    fire("#doctor-calendar-previous");
    String previousRange = lookup("#doctor-calendar-week-picker").queryAs(Button.class).getText();
    assertNotEquals(currentRange, previousRange);
    fire("#doctor-calendar-next");
    assertEquals(
        currentRange, lookup("#doctor-calendar-week-picker").queryAs(Button.class).getText());
    fire("#doctor-calendar-week-picker");
    waitForNode("#doctor-calendar-week-picker-popup");
    verifyThat("#doctor-calendar-picker-month-grid", isVisible());
    verifyThat("#doctor-calendar-picker-year-grid", isVisible());
    fire("#doctor-calendar-picker-next-month");
    fire("#doctor-calendar-picker-close");
    fire("#doctor-calendar-week-picker");
    fire("#doctor-calendar-picker-today");
    assertEquals(
        currentRange, lookup("#doctor-calendar-week-picker").queryAs(Button.class).getText());

    fire("#doctor-calendar-settings");
    waitForNode("#doctor-calendar-settings-page");
    verifyThat("#doctor-calendar-settings-timezone", isVisible());
    assertTrue(lookup("#doctor-calendar-settings-work-location").tryQuery().isEmpty());
    selectCombo("#doctor-calendar-settings-first-day", DayOfWeek.MONDAY);
    fire("#doctor-calendar-settings-monday-add");
    selectCombo("#doctor-calendar-settings-monday-end-0", "12:00");
    selectCombo("#doctor-calendar-settings-monday-start-1", "13:00");
    selectCombo("#doctor-calendar-settings-monday-end-1", "18:00");
    fire("#doctor-calendar-settings-save");
    waitForNode("#doctor-calendar-page");
    verifyThat("#doctor-calendar-page", isVisible());

    fire("#doctor-calendar-settings");
    waitForNode("#doctor-calendar-settings-page");
    assertEquals(
        DayOfWeek.MONDAY,
        lookup("#doctor-calendar-settings-first-day").queryAs(ComboBox.class).getValue());
    assertTrue(lookup("#doctor-calendar-settings-monday-start-1").tryQuery().isPresent());
    fire("#doctor-calendar-settings-cancel");
    waitForNode("#doctor-calendar-page");
  }

  @Test
  void doctorCanDeclineAndCreateAcceptedAppointmentFromCalendar() throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    assertTrue(lookup("#doctor-calendar-accept-1").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-decline-1").tryQuery().isPresent());
    assertTrue(
        lookup(".calendar-appointment-patient").queryAll().stream()
            .map(node -> ((Label) node).getText())
            .allMatch(name -> !name.startsWith("P")));

    fire("#doctor-calendar-decline-1");
    assertFalse(lookup("#doctor-calendar-appointment-1-" + today()).tryQuery().isPresent());
    assertEquals(
        AppointmentStatus.DECLINED,
        new AppointmentRepository(database).findById(1).orElseThrow().status());

    fire("#doctor-calendar-add-appointment");
    waitForNode("#doctor-calendar-appointment-dialog-content");
    LocalDate currentDate = today();
    LocalDate currentWeekStart = CalendarWeek.containing(currentDate, DayOfWeek.SUNDAY).start();
    LocalDate targetDate =
        currentWeekStart.plusDays(currentWeekStart.plusDays(1).equals(currentDate) ? 2 : 1);
    interact(
        () ->
            lookup("#doctor-calendar-appointment-dialog-date")
                .queryAs(DatePicker.class)
                .setValue(targetDate));
    selectCombo("#doctor-calendar-appointment-dialog-start-hour", "10");
    selectCombo("#doctor-calendar-appointment-dialog-start-minute", "00");
    selectCombo("#doctor-calendar-appointment-dialog-end-hour", "10");
    selectCombo("#doctor-calendar-appointment-dialog-end-minute", "30");
    fire("#doctor-calendar-appointment-dialog-submit");

    Appointment created =
        new AppointmentRepository(database)
            .findByDoctor(doctorId).stream()
                .filter(appointment -> appointment.startsAt().toLocalDate().equals(targetDate))
                .findFirst()
                .orElseThrow();
    assertEquals(AppointmentStatus.ACCEPTED, created.status());
    assertTrue(
        lookup("#doctor-calendar-appointment-" + created.id() + "-" + targetDate)
            .tryQuery()
            .isPresent());
  }

  @Test
  void clickingAnEmptyCalendarSlotUsesTheSharedAppointmentForm() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    LocalDate emptyDate = today();
    interact(
        () ->
            lookup("#doctor-calendar-events-" + emptyDate)
                .queryAs(Pane.class)
                .getOnMouseClicked()
                .handle(
                    new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        20,
                        15 * 100 + 4,
                        20,
                        15 * 100 + 4,
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
                        null)));
    waitForNode("#doctor-calendar-appointment-dialog-content");
    assertEquals(
        emptyDate,
        lookup("#doctor-calendar-appointment-dialog-date").queryAs(DatePicker.class).getValue());
    assertEquals(
        "07:30",
        lookup("#doctor-calendar-appointment-dialog-start-hour").queryAs(ComboBox.class).getValue()
            + ":"
            + lookup("#doctor-calendar-appointment-dialog-start-minute")
                .queryAs(ComboBox.class)
                .getValue());
    interact(
        () ->
            ((Stage)
                    lookup("#doctor-calendar-appointment-dialog-cancel")
                        .queryAs(Button.class)
                        .getScene()
                        .getWindow())
                .close());
  }

  @Test
  void selectingAnAppointmentOpensDetailsWithoutTreatingItAsAnInlineDecision() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    interact(
        () ->
            lookup("#doctor-calendar-appointment-2-" + today())
                .query()
                .fireEvent(
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
                        null)));
    waitForNode("#doctor-calendar-appointment-dialog-content");
    assertTrue(lookup("#doctor-calendar-appointment-dialog-status").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-appointment-dialog-submit").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-appointment-dialog-accept").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-appointment-dialog-decline").tryQuery().isPresent());
    interact(
        () ->
            ((Stage)
                    lookup("#doctor-calendar-appointment-dialog-cancel")
                        .queryAs(Button.class)
                        .getScene()
                        .getWindow())
                .hide());
  }

  @Test
  void calendarColumnsTouchAndAppointmentControlsStayInsideTheirDayColumn() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    Node timeHeader = lookup("#doctor-calendar-time-header").query();
    Node firstDayHeader = lookup(".calendar-day-header").queryAll().iterator().next();
    Node lastDayHeader =
        lookup(".calendar-day-header").queryAll().stream()
            .reduce((first, second) -> second)
            .orElseThrow();
    Bounds timeBounds = timeHeader.localToScene(timeHeader.getBoundsInLocal());
    Bounds firstDayBounds = firstDayHeader.localToScene(firstDayHeader.getBoundsInLocal());
    Bounds lastDayBounds = lastDayHeader.localToScene(lastDayHeader.getBoundsInLocal());
    assertEquals(timeBounds.getMaxX(), firstDayBounds.getMinX(), 0.1);
    Node timeAxis = lookup("#doctor-calendar-time-axis").query();
    Node firstDayColumn = lookup(".calendar-day-column").queryAll().iterator().next();
    Bounds timeAxisBounds = timeAxis.localToScene(timeAxis.getBoundsInLocal());
    Bounds firstDayColumnBounds = firstDayColumn.localToScene(firstDayColumn.getBoundsInLocal());
    assertEquals(timeBounds.getMaxX(), timeAxisBounds.getMaxX(), 0.1);
    assertEquals(timeAxisBounds.getMaxX(), firstDayColumnBounds.getMinX(), 0.1);

    Node appointment = lookup("#doctor-calendar-appointment-1-" + today()).query();
    Node oneHourAppointment = lookup("#doctor-calendar-appointment-2-" + today()).query();
    Node dayColumn = lookup("#doctor-calendar-day-column-" + today()).query();
    Node eventSurface = lookup("#doctor-calendar-events-" + today()).query();
    assertContained(appointment, dayColumn);
    Bounds appointmentBounds = appointment.localToScene(appointment.getBoundsInLocal());
    Bounds oneHourAppointmentBounds =
        oneHourAppointment.localToScene(oneHourAppointment.getBoundsInLocal());
    Bounds eventSurfaceBounds = eventSurface.localToScene(eventSurface.getBoundsInLocal());
    assertEquals(eventSurfaceBounds.getMinX(), appointmentBounds.getMinX(), 0.1);
    assertEquals(eventSurfaceBounds.getMaxX(), appointmentBounds.getMaxX(), 0.1);
    assertTrue(appointmentBounds.getHeight() > 32);
    Bounds thirtyMinuteSlot =
        sceneBounds(lookup("#doctor-calendar-period-" + today() + "-16").query());
    Bounds oneHourStartSlot =
        sceneBounds(lookup("#doctor-calendar-period-" + today() + "-18").query());
    Bounds oneHourEndSlot =
        sceneBounds(lookup("#doctor-calendar-period-" + today() + "-19").query());
    assertEquals(thirtyMinuteSlot.getHeight() - 4, appointmentBounds.getHeight(), 0.1);
    assertEquals(thirtyMinuteSlot.getHeight() * 2 - 4, oneHourAppointmentBounds.getHeight(), 0.1);
    assertEquals(oneHourStartSlot.getMinY() + 2, oneHourAppointmentBounds.getMinY(), 0.1);
    assertEquals(oneHourEndSlot.getMaxY() - 2, oneHourAppointmentBounds.getMaxY(), 0.1);
    assertFullyContained(lookup("#doctor-calendar-accept-1").query(), appointment);
    assertFullyContained(lookup("#doctor-calendar-decline-1").query(), appointment);
    assertContained(lookup("#doctor-calendar-accept-1").query(), dayColumn);
    assertContained(lookup("#doctor-calendar-decline-1").query(), dayColumn);

    ScrollPane scroll = lookup("#doctor-calendar-scroll").queryAs(ScrollPane.class);
    Node viewport = scroll.lookup(".viewport");
    Bounds viewportBounds = viewport.localToScene(viewport.getBoundsInLocal());
    if (scroll.getHmax() > 0) {
      interact(() -> scroll.setHvalue(1));
      WaitForAsyncUtils.waitForFxEvents();
      lastDayBounds = lastDayHeader.localToScene(lastDayHeader.getBoundsInLocal());
    }
    assertEquals(viewportBounds.getMaxX(), lastDayBounds.getMaxX(), 0.1);
  }

  private static void assertContained(Node child, Node parent) {
    Bounds childBounds = sceneBounds(child);
    Bounds parentBounds = sceneBounds(parent);
    assertTrue(childBounds.getMinX() >= parentBounds.getMinX() - 0.1);
    assertTrue(childBounds.getMaxX() <= parentBounds.getMaxX() + 0.1);
  }

  private static void assertFullyContained(Node child, Node parent) {
    Bounds childBounds = sceneBounds(child);
    Bounds parentBounds = sceneBounds(parent);
    assertTrue(childBounds.getMinX() >= parentBounds.getMinX() - 0.1);
    assertTrue(childBounds.getMaxX() <= parentBounds.getMaxX() + 0.1);
    assertTrue(childBounds.getMinY() >= parentBounds.getMinY() - 0.1);
    assertTrue(childBounds.getMaxY() <= parentBounds.getMaxY() + 0.1);
  }

  private static Bounds sceneBounds(Node node) {
    return node.localToScene(node.getBoundsInLocal());
  }

  @Test
  void switchesToChronologicalLazyScheduleAndReanchors() throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    ComboBox<String> mode = calendarMode();
    assertEquals("Week", mode.getValue());
    assertEquals("Choose Calendar view", mode.getAccessibleText());
    interact(() -> mode.setValue("Schedule"));
    WaitForAsyncUtils.waitForFxEvents();

    waitForNode("#doctor-calendar-schedule-list");
    assertEquals(
        today().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
        lookup("#doctor-calendar-week-picker").queryAs(Button.class).getText());
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
    assertEquals(50, scheduleList().getItems().size());
    assertNotEquals(loadedScheduleEntryCount, scheduleList().getItems().size());
    fire("#doctor-calendar-previous");
    assertEquals(46, scheduleList().getItems().size());
    fire("#doctor-calendar-today");
    assertEquals(46, scheduleList().getItems().size());

    fire("#doctor-calendar-week-picker");
    waitForNode("#doctor-calendar-week-picker-popup");
    verifyThat("#doctor-calendar-picker-month-grid", isVisible());
    LocalDate targetDate = today().plusDays(8);
    String targetMonth =
        targetDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
    for (int index = 0;
        index < 12
            && !targetMonth.equals(
                lookup("#doctor-calendar-picker-month-label").queryAs(Label.class).getText());
        index++) {
      fire("#doctor-calendar-picker-next-month");
    }
    fire("#doctor-calendar-picker-date-" + targetDate);
    assertEquals(50, scheduleList().getItems().size());
    assertTrue(lookup("#doctor-calendar-schedule-date-" + targetDate).tryQuery().isPresent());
    fire("#doctor-calendar-today");

    interact(() -> mode.setValue("Week"));
    WaitForAsyncUtils.waitForFxEvents();
    assertEquals(7, lookup(".calendar-day-header").queryAll().size());
    assertTrue(lookup("#doctor-calendar-schedule-list").tryQuery().isEmpty());
    assertEquals(
        appointmentsBeforeSchedule, new AppointmentRepository(database).findByDoctor(doctorId));
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Singapore"));
  }

  @SuppressWarnings("unchecked")
  private ListView<?> scheduleList() {
    return lookup("#doctor-calendar-schedule-list").queryAs(ListView.class);
  }

  @SuppressWarnings("unchecked")
  private ComboBox<String> calendarMode() {
    return (ComboBox<String>)
        (ComboBox<?>) lookup("#doctor-calendar-view-mode").queryAs(ComboBox.class);
  }

  @SuppressWarnings("unchecked")
  private <T> void selectCombo(String selector, T value) {
    interact(() -> lookup(selector).queryAs(ComboBox.class).setValue(value));
  }

  private void loginAsDoctor() {
    setText("#login-username", "doctor");
    setText("#login-password", "doctor-pass");
    fire("#login-submit");
    waitForNode("#doctor-workspace");
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void waitForNode(String selector) {
    try {
      WaitForAsyncUtils.waitFor(
          60, TimeUnit.SECONDS, () -> lookup(selector).tryQuery().isPresent());
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for " + selector, exception);
    }
  }

  private void waitForScheduleSize(int minimumSize) {
    try {
      WaitForAsyncUtils.waitFor(
          60,
          TimeUnit.SECONDS,
          () ->
              lookup("#doctor-calendar-schedule-list").tryQuery().isPresent()
                  && scheduleList().getItems().size() >= minimumSize);
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for schedule page append", exception);
    }
  }
}
