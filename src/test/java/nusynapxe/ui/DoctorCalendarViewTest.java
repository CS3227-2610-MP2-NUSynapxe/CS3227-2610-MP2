package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
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
      appointments.create(patient.id(), doctor.id(), start, start.plusMinutes(30), statuses[index]);
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
    assertEquals(6, lookup(".calendar-appointment-status").queryAll().size());
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
    assertEquals(45, schedule.getItems().size());
    assertTrue(lookup("#doctor-calendar-schedule-date-" + today()).tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-schedule-appointment-1").tryQuery().isPresent());
    assertTrue(lookup(".calendar-schedule-status").tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-schedule-date-" + today())
            .query()
            .getStyleClass()
            .contains("calendar-schedule-today"));
    assertEquals("Chronological future appointments", schedule.getAccessibleText());
    for (int index = 1; index <= AppointmentStatus.values().length; index++) {
      int rowIndex = index;
      interact(() -> schedule.scrollTo(rowIndex));
      WaitForAsyncUtils.waitForFxEvents();
      waitForNode("#doctor-calendar-schedule-status-" + index);
      assertEquals(
          UiComponents.humanizeStatus(AppointmentStatus.values()[index - 1].name()),
          lookup("#doctor-calendar-schedule-status-" + index).queryAs(Label.class).getText());
    }
    assertTrue(
        lookup("#doctor-calendar-schedule-appointment-6")
            .query()
            .getStyleClass()
            .contains("calendar-schedule-cancelled"));

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
    assertEquals(45, scheduleList().getItems().size());
    fire("#doctor-calendar-today");
    assertEquals(45, scheduleList().getItems().size());

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
