package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.event.EventType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

/** Shared TestFX fixture for Receptionist workflow tests. */
abstract class ReceptionistViewTestSupport extends ApplicationTest {
  @TempDir protected Path temporaryDirectory;
  protected SqliteDatabase database;
  protected ClinicServices services;
  protected Account doctor;
  protected Account receptionist;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("receptionist-ui.db"));
    database.open();
    services = ClinicServices.forDatabase(database);
    Account admin =
        services.accountService().createInitialAdmin("admin", "Admin", "secure-pass".toCharArray());
    Session adminSession = new Session(admin.id(), admin.username(), Role.SYSTEM_ADMIN);
    doctor =
        services
            .accountService()
            .createStaff(
                adminSession, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray());
    receptionist =
        services
            .accountService()
            .createStaff(
                adminSession,
                "reception",
                "Reception",
                Role.RECEPTIONIST,
                "reception-pass".toCharArray());
    new ApplicationRouter(stage, database).showInitial();
    stage.show();
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    if (database != null) {
      database.close();
    }
  }

  protected void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextField.class).setText(value));
  }

  @SuppressWarnings("unchecked")
  protected void setDate(String selector, LocalDate value) {
    interact(
        () -> {
          lookup(selector + "-day").queryAs(ComboBox.class).setValue(value.getDayOfMonth());
          lookup(selector + "-month").queryAs(ComboBox.class).setValue(value.getMonth());
          lookup(selector + "-year").queryAs(ComboBox.class).setValue(value.getYear());
        });
  }

  protected void setDatePicker(String selector, LocalDate value) {
    interact(() -> lookup(selector).queryAs(DatePicker.class).setValue(value));
  }

  protected String text(String selector) {
    return lookup(selector).queryAs(TextField.class).getText();
  }

  protected TextField textField(String selector) {
    return lookup(selector).queryAs(TextField.class);
  }

  protected String textLabel(String selector) {
    return lookup(selector).queryAs(javafx.scene.control.Label.class).getText();
  }

  @SuppressWarnings("unchecked")
  protected TableView<AppointmentListRow> appointmentList() {
    return lookup("#reception-appointment-list").queryAs(TableView.class);
  }

  @SuppressWarnings("unchecked")
  protected LocalDate date(String selector) {
    ComboBox<Integer> dayCombo = lookup(selector + "-day").queryAs(ComboBox.class);
    ComboBox<Month> monthCombo = lookup(selector + "-month").queryAs(ComboBox.class);
    ComboBox<Integer> yearCombo = lookup(selector + "-year").queryAs(ComboBox.class);
    if (dayCombo.getValue() == null
        || monthCombo.getValue() == null
        || yearCombo.getValue() == null) {
      return null;
    }
    return LocalDate.of(yearCombo.getValue(), monthCombo.getValue(), dayCombo.getValue());
  }

  @SuppressWarnings("unchecked")
  protected <T> void selectCombo(String selector, T value) {
    interact(() -> lookup(selector).queryAs(ComboBox.class).setValue(value));
  }

  @SuppressWarnings("unchecked")
  protected <T> ComboBox<T> combo(String selector) {
    return lookup(selector).queryAs(ComboBox.class);
  }

  protected ComboBox<CountryOption> countryCombo() {
    return combo("#reception-register-issuing-country");
  }

  protected static CountryOption country(String code) {
    return CountryOption.fromCode(code).orElseThrow();
  }

  protected void selectWorkspaceTab(int index) {
    interact(() -> workspaceTabs().getSelectionModel().select(index));
  }

  protected TabPane workspaceTabs() {
    return lookup("#reception-workspace-tabs").queryAs(TabPane.class);
  }

  protected void layoutWorkspace() {
    interact(
        () -> {
          Pane workspace = lookup("#receptionist-workspace").queryAs(Pane.class);
          workspace.applyCss();
          workspace.layout();
        });
  }

  protected void assertCompactDatePickerBounds(DatePicker picker) {
    double width = picker.getBoundsInParent().getWidth();
    assertTrue(width >= 132.0 && width <= 180.0, "Unexpected rendered date-picker width: " + width);
  }

  @SuppressWarnings("unchecked")
  protected void selectFirstAppointment(String selector) {
    interact(() -> lookup(selector).queryAs(TableView.class).getSelectionModel().selectFirst());
  }

  @SuppressWarnings("unchecked")
  protected TableView<Patient> patientTable() {
    return lookup("#reception-patient-table").queryAs(TableView.class);
  }

  protected void preparePatientTable() {
    interact(
        () -> {
          TableView<Patient> table = patientTable();
          table.scrollTo(0);
          table.applyCss();
          table.layout();
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  protected void assertCompactPatientSelectors(String formPrefix) {
    assertTrue(
        lookup("#" + formPrefix + "-identity-type")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-issuing-country")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-date-of-birth-day")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-date-of-birth-month")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-date-of-birth-year")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-sex")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
  }

  protected void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  protected void loginAsReceptionist() {
    setText("#login-username", "reception");
    setText("#login-password", "reception-pass");
    fire("#login-submit");
    waitForNode("#receptionist-workspace");
  }

  protected Session receptionistSession() {
    return new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
  }

  protected void waitForNode(String selector) {
    try {
      WaitForAsyncUtils.waitFor(
          60, TimeUnit.SECONDS, () -> lookup(selector).tryQuery().isPresent());
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for " + selector, exception);
    }
  }

  protected static MouseEvent primaryClick() {
    return mouseEvent(MouseEvent.MOUSE_CLICKED);
  }

  protected static MouseEvent mouseEvent(EventType<MouseEvent> eventType) {
    return new MouseEvent(
        eventType,
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

  protected void join(Thread thread) {
    try {
      thread.join(60_000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for patient dialog", exception);
    }
    assertFalse(thread.isAlive(), "Patient dialog action did not finish");
  }
}
