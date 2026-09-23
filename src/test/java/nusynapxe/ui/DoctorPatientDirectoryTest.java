package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Sex;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** Verifies doctor patient-directory layout, search, registration, and deletion workflows. */
final class DoctorPatientDirectoryTest extends DoctorViewTestSupport {
  @Test
  void dashboardAndPatientDirectoryUseTheApprovedCompactPageLayout() {
    loginAsDoctor();
    javafx.scene.control.DatePicker dashboardDate =
        lookup("#doctor-dashboard-date").queryAs(javafx.scene.control.DatePicker.class);
    assertEquals(150.0, dashboardDate.getPrefWidth(), 0.1);
    assertTrue(dashboardDate.getMaxWidth() <= 170.0);

    fire("#doctor-nav-patients");
    ScrollPane patientsPage = lookup("#doctor-patients-page").queryAs(ScrollPane.class);
    VBox directoryCard = lookup("#doctor-patient-directory-card").queryAs(VBox.class);
    VBox directoryPage = lookup("#doctor-patient-directory").queryAs(VBox.class);
    interact(
        () -> {
          patientsPage.applyCss();
          patientsPage.layout();
          directoryPage.applyCss();
          directoryPage.layout();
        });

    assertTrue(directoryCard.getStyleClass().contains("card"));
    assertEquals(
        "Patient Directory",
        lookup("#doctor-patient-directory-card .page-title").queryAs(Label.class).getText());
    assertTrue(lookup("#doctor-patient-directory .supporting-text").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-patient-directory .section-heading").tryQuery().isEmpty());
    assertTrue(
        lookup("#doctor-patient-search")
                .queryAs(TextInputControl.class)
                .getBoundsInParent()
                .getWidth()
            >= 400.0);
    assertTrue(patientsPage.isFitToHeight());
    assertTrue(
        directoryCard.getHeight() >= patientsPage.getViewportBounds().getHeight() - 1.0,
        "Patient Directory card should fill its page viewport");
  }

  @Test
  void patientDirectoryResultsTableConsumesAvailableCardHeight() {
    loginAsDoctor();
    fire("#doctor-nav-patients");
    TableView<?> table = patientTable();
    interact(
        () -> {
          table.applyCss();
          table.layout();
          lookup("#doctor-patient-directory").query().applyCss();
          lookup("#doctor-patient-directory").queryAs(VBox.class).layout();
        });

    assertEquals(Double.MAX_VALUE, table.getMaxHeight());
    assertTrue(
        table.getHeight() > 304.0,
        "Patient Directory table should grow beyond its former fixed maximum height");
  }

  @Test
  void patientSearchKeepsViewActionForEveryReturnedRow() throws SQLException {
    var repository = new nusynapxe.persistence.PatientRepository(database);
    for (int index = 0; index < 6; index++) {
      repository.create(
          new Patient(
              0,
              "Search Alpha",
              "Patient" + index,
              "",
              "555-02" + String.format("%02d", index),
              "",
              ""));
      repository.create(
          new Patient(
              0,
              "Search Beta",
              "Patient" + index,
              "",
              "555-03" + String.format("%02d", index),
              "",
              ""));
    }

    loginAsDoctor();
    fire("#doctor-nav-patients");
    setText("#doctor-patient-search", "Search Alpha Patient0");
    fire("#doctor-patient-search-submit");

    setText("#doctor-patient-search", "does-not-exist");
    fire("#doctor-patient-search-submit");

    setText("#doctor-patient-search", "Search Beta");
    fire("#doctor-patient-search-submit");

    TableView<Patient> table = patientTable();
    assertEquals(6, table.getItems().size());
    for (int index = 0; index < table.getItems().size(); index++) {
      Patient patient = table.getItems().get(index);
      var actionValue = table.getColumns().getLast().getCellObservableValue(patient);
      assertTrue(actionValue != null, "Actions column should expose a row value");
      assertSame(patient, actionValue.getValue(), "Actions column should retain the row patient");
      int rowIndex = index;
      interact(
          () -> {
            table.scrollTo(rowIndex);
            table.applyCss();
            table.layout();
          });
      WaitForAsyncUtils.waitForFxEvents();
      assertTrue(
          lookup("#doctor-patient-view-" + patient.id()).tryQuery().isPresent(),
          "Search result row " + patient.id() + " should expose a View action");
    }
  }

  @Test
  void doctorCanNavigateToPatientsAndDeleteAnUnusedPatient() throws SQLException {
    loginAsDoctor();
    Button dashboardNavigation = lookup("#doctor-nav-dashboard").queryAs(Button.class);
    var dashboardCalendar = lookup("#doctor-dashboard-day-calendar").query();
    Button patientsNavigation = lookup("#doctor-nav-patients").queryAs(Button.class);
    double navigationWidth = lookup("#doctor-navigation").query().getBoundsInLocal().getWidth();
    assertEquals(navigationWidth, dashboardNavigation.getBoundsInParent().getWidth(), 0.1);
    assertEquals(navigationWidth, patientsNavigation.getBoundsInParent().getWidth(), 0.1);
    assertEquals(0, lookup("#doctor-navigation").queryAs(VBox.class).getSpacing(), 0.0);
    assertTrue(dashboardNavigation.getStyleClass().contains("active-navigation"));
    assertEquals(true, dashboardCalendar.getProperties().get("tickerRunning"));

    fire("#doctor-nav-patients");
    verifyThat("#doctor-patients-page", isVisible());
    verifyThat("#doctor-patient-directory-view", isVisible());
    assertFalse(lookup("#doctor-patient-tabs").tryQuery().isPresent());
    assertFalse(lookup("#doctor-patient-register-tab").tryQuery().isPresent());
    assertFalse(lookup("#doctor-patient-manage-tab").tryQuery().isPresent());
    assertTrue(patientsNavigation.getStyleClass().contains("active-navigation"));
    assertFalse(dashboardNavigation.getStyleClass().contains("active-navigation"));
    assertFalse(lookup("#doctor-master-detail").tryQuery().isPresent());
    assertFalse(lookup("#doctor-consultation-save").tryQuery().isPresent());
    assertEquals(false, dashboardCalendar.getProperties().get("tickerRunning"));
    assertFalse(lookup("#doctor-prescription-submit").tryQuery().isPresent());
    assertEquals(
        List.of("Name", "Date of birth", "Phone", "Email", "Status", "Actions"),
        patientTable().getColumns().stream().map(column -> column.getText()).toList());
    assertCompactPatientSelectors("doctor-register");

    fire("#doctor-patient-open-register");
    verifyThat("#doctor-patient-register-view", isVisible());
    fire("#doctor-patient-register");
    verifyThat("#doctor-feedback", hasText("Identity type is required"));
    verifyThat("#doctor-patient-register-view", isVisible());
    setText("#doctor-register-identity-number", "draft-only");
    fire("#doctor-patient-register-cancel");
    verifyThat("#doctor-patient-directory-view", isVisible());
    assertTrue(
        services.patientService().searchAdministrative(doctorSession(), "draft-only").isEmpty());

    fire("#doctor-patient-open-register");
    selectCombo("#doctor-register-identity-type", IdentityType.NRIC);
    selectCombo("#doctor-register-sex", Sex.FEMALE);
    setText("#doctor-register-identity-number", "S1234567D");
    setText("#doctor-register-first-name", "New");
    setText("#doctor-register-last-name", "Patient");
    setDate("#doctor-register-date-of-birth", LocalDate.of(1990, 1, 1));
    setText("#doctor-register-phone-number", "5550101");
    setText("#doctor-register-email", "new.patient@example.test");
    setText("#doctor-register-address", "New address");
    fire("#doctor-patient-register");
    verifyThat("#doctor-feedback", hasText("Patient registered"));
    verifyThat("#doctor-patient-directory-view", isVisible());

    setText("#doctor-patient-search", "new.patient@example.test");
    fire("#doctor-patient-search-submit");
    assertEquals(1, patientTable().getItems().size());
    Patient registeredPatient = patientTable().getItems().get(0);
    assertEquals("New Patient", tableValue(0, registeredPatient));
    assertEquals("1990-01-01", tableValue(1, registeredPatient));
    assertEquals("+65 5550101", tableValue(2, registeredPatient));
    assertEquals("new.patient@example.test", tableValue(3, registeredPatient));
    assertEquals("Active", tableValue(4, registeredPatient));
    interact(() -> patientTable().getSelectionModel().selectFirst());
    assertFalse(lookup("#doctor-patient-details-window").tryQuery().isPresent());
    assertFalse(lookup("#doctor-patient-edit-view").query().isVisible());
    preparePatientTable();
    assertTrue(lookup("#doctor-patient-view-" + registeredPatient.id()).tryQuery().isPresent());
    fire("#doctor-patient-view-" + registeredPatient.id());
    waitForNode("#doctor-patient-view");
    assertFalse(lookup("#doctor-patient-details-window").tryQuery().isPresent());
    fire("#doctor-patient-edit");
    waitForNode("#doctor-patient-edit-view");
    assertCompactPatientSelectors("doctor-patient");

    setText("#doctor-patient-phone-number", "5550102");
    fire("#doctor-patient-update");
    verifyThat("#doctor-feedback", hasText("Patient changes saved"));
    verifyThat("#doctor-patient-view", isVisible());
    assertFalse(lookup("#doctor-patient-edit-view").query().isVisible());

    Thread cancelThread = new Thread(() -> fire("#doctor-patient-delete"));
    cancelThread.start();
    waitForNode("#doctor-patient-delete-confirm-window");
    fire("#doctor-patient-delete-cancel");
    join(cancelThread);
    verifyThat("#doctor-patient-view", isVisible());

    Thread deleteThread = new Thread(() -> fire("#doctor-patient-delete"));
    deleteThread.start();
    waitForNode("#doctor-patient-delete-confirm-window");
    fire("#doctor-patient-delete-confirm");
    join(deleteThread);
    verifyThat("#doctor-feedback", hasText("Patient deleted"));
    assertTrue(patientTable().getItems().isEmpty());
    assertTrue(
        services
            .patientService()
            .searchAdministrative(doctorSession(), "new.patient@example.test")
            .isEmpty());
    fire("#doctor-nav-dashboard");
    verifyThat("#doctor-master-detail", isVisible());
    assertEquals(true, dashboardCalendar.getProperties().get("tickerRunning"));
  }

  @Test
  void doctorSeesWhyAReferencedPatientCannotBeDeleted() throws SQLException {
    addPatientHistory();
    loginAsDoctor();
    fire("#doctor-nav-patients");
    setText("#doctor-patient-search", "P000001");
    fire("#doctor-patient-search-submit");
    Patient referencedPatient = patientTable().getItems().get(0);
    preparePatientTable();
    fire("#doctor-patient-view-" + referencedPatient.id());
    waitForNode("#doctor-patient-view");

    Thread deleteThread = new Thread(() -> fire("#doctor-patient-delete"));
    deleteThread.start();
    waitForNode("#doctor-patient-delete-blocked-window");
    verifyThat("#doctor-patient-delete-blocked-explanation", isVisible());
    verifyThat("#doctor-patient-delete-blocked-appointments", hasText("Appointments: 1"));
    verifyThat("#doctor-patient-delete-blocked-clinical-records", hasText("Clinical records: 1"));
    verifyThat("#doctor-patient-delete-blocked-prescriptions", hasText("Prescriptions: 1"));
    verifyThat("#doctor-patient-delete-blocked-payments", hasText("Payments: 1"));
    verifyThat("#doctor-patient-delete-blocked-receipts", hasText("Receipts: 1"));
    assertTrue(
        lookup("#doctor-patient-delete-blocked-alternative")
            .queryAs(Label.class)
            .getText()
            .contains("deactivate the patient instead"));
    fire("#doctor-patient-delete-blocked-close");
    join(deleteThread);
    verifyThat("#doctor-patient-view", isVisible());
  }
}
