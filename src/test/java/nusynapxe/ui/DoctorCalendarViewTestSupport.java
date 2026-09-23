package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

/** Shared TestFX fixture for Doctor calendar behavior tests. */
abstract class DoctorCalendarViewTestSupport extends ApplicationTest {
  @TempDir protected Path temporaryDirectory;
  protected SqliteDatabase database;
  protected ClinicServices services;
  protected long doctorId;
  protected long timeOffId;
  protected List<Appointment> appointmentsBeforeSchedule;

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
    timeOffId =
        appointments.createTimeOff(doctor.id(), today.atTime(15, 0), today.atTime(16, 0)).id();
    doctorId = doctor.id();
    appointmentsBeforeSchedule = new AppointmentRepository(database).findByDoctor(doctorId);
    new ApplicationRouter(stage, database).showInitial();
    stage.show();
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    if (lookup("#doctor-workspace").tryQuery().isPresent()) {
      interact(
          () -> {
            Stage stage = (Stage) lookup("#doctor-workspace").query().getScene().getWindow();
            stage.setWidth(1200);
            stage.setHeight(760);
          });
    }
    if (database != null) {
      database.close();
    }
  }

  protected LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Singapore"));
  }

  @SuppressWarnings("unchecked")
  protected ListView<?> scheduleList() {
    return lookup("#doctor-calendar-schedule-list").queryAs(ListView.class);
  }

  @SuppressWarnings("unchecked")
  protected ComboBox<String> calendarMode() {
    return (ComboBox<String>)
        (ComboBox<?>) lookup("#doctor-calendar-view-mode").queryAs(ComboBox.class);
  }

  @SuppressWarnings("unchecked")
  protected <T> void selectCombo(String selector, T value) {
    interact(() -> lookup(selector).queryAs(ComboBox.class).setValue(value));
  }

  protected void loginAsDoctor() {
    setText("#login-username", "doctor");
    setText("#login-password", "doctor-pass");
    fire("#login-submit");
    waitForNode("#doctor-workspace");
  }

  protected void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  protected void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
    WaitForAsyncUtils.waitForFxEvents();
  }

  protected void waitForNode(String selector) {
    try {
      WaitForAsyncUtils.waitFor(
          60, TimeUnit.SECONDS, () -> lookup(selector).tryQuery().isPresent());
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for " + selector, exception);
    }
  }

  protected void openSeededTimeOff() {
    interact(
        () ->
            lookup("#doctor-calendar-time-off-" + timeOffId + "-" + today())
                .query()
                .getOnMouseClicked()
                .handle(primaryClick()));
    waitForNode("#doctor-calendar-time-off-details");
  }

  protected static void join(Thread thread) {
    try {
      thread.join(60_000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for time-off confirmation", exception);
    }
    assertFalse(thread.isAlive(), "Time-off confirmation did not finish");
  }

  protected static MouseEvent primaryClick() {
    return new MouseEvent(
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
        null);
  }

  protected static void assertContained(Node child, Node parent) {
    Bounds childBounds = sceneBounds(child);
    Bounds parentBounds = sceneBounds(parent);
    assertTrue(childBounds.getMinX() >= parentBounds.getMinX() - 0.1);
    assertTrue(childBounds.getMaxX() <= parentBounds.getMaxX() + 0.1);
  }

  protected static void assertFullyContained(Node child, Node parent) {
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

  protected void waitForScheduleSize(int minimumSize) {
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
