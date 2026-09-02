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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
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

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Singapore"));
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
}
