package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import java.sql.SQLException;
import java.time.LocalDate;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Sex;
import org.junit.jupiter.api.Test;

/** Verifies read-only clinical history and patient-registration integration. */
final class DoctorClinicalHistoryTest extends DoctorViewTestSupport {
  @Test
  void completedSelectionKeepsClinicalHistoryReadOnly() throws SQLException {
    addPatientHistory();

    loginAsDoctor();
    selectDashboardAppointment(1);

    Label header = lookup("#doctor-selected-appointment").queryAs(Label.class);
    assertTrue(header.getText().contains("Checked Out"), header.getText());
    assertTrue(header.getStyleClass().contains("status-checked-out"));
    assertTrue(lookup("#doctor-clinical-read-only").tryQuery().isPresent());
    assertTrue(lookup("#doctor-consultation-card").query().isVisible());
    assertTrue(lookup("#doctor-prescription-card").query().isVisible());
    assertFalse(lookup("#doctor-diagnosis").queryAs(TextInputControl.class).isEditable());
    assertFalse(lookup("#doctor-consultation-notes").queryAs(TextInputControl.class).isEditable());
    assertFalse(lookup("#doctor-follow-up").queryAs(TextInputControl.class).isEditable());
    assertFalse(
        lookup("#doctor-consultation-save").queryAs(javafx.scene.control.Button.class).isVisible());
    assertFalse(
        lookup("#doctor-prescription-submit")
            .queryAs(javafx.scene.control.Button.class)
            .isVisible());
    assertTrue(lookup("#doctor-complete").tryQuery().isEmpty());
  }

  @Test
  void doctorOpensReadOnlyClinicalHistoryFromPatientsAndDashboard() throws SQLException {
    addPatientHistory();

    loginAsDoctor();
    selectDashboardAppointment(1);
    verifyThat("#doctor-view-patient-history", isVisible());
    fire("#doctor-view-patient-history");

    verifyThat("#doctor-patients-page", isVisible());
    verifyThat("#doctor-clinical-history-view", isVisible());
    assertEquals(1, lookup("#doctor-history-patient").queryAll().size());
    assertEquals(1, lookup("#doctor-history-patient-field").queryAll().size());
    verifyThat("#doctor-history-list", isVisible());
    assertEquals(
        1,
        lookup("#doctor-history-list")
            .queryAs(javafx.scene.control.ListView.class)
            .getItems()
            .size());
    assertTrue(
        lookup("#doctor-history-list").queryAs(javafx.scene.control.ListView.class).getHeight()
            >= 180.0,
        "Consultation history list should have enough height to show multiple records");
    verifyThat("#doctor-history-diagnosis", isVisible());
    assertFalse(lookup("#doctor-history-diagnosis").queryAs(TextInputControl.class).isEditable());
    assertTrue(
        lookup("#doctor-history-diagnosis").queryAs(TextInputControl.class).getHeight() >= 72.0,
        "Diagnosis field should show several lines of text");
    assertFalse(
        lookup("#doctor-history-consultation-notes").queryAs(TextInputControl.class).isEditable());
    assertTrue(
        lookup("#doctor-history-consultation-notes").queryAs(TextInputControl.class).getHeight()
            >= 72.0,
        "Consultation notes field should show several lines of text");
    assertFalse(lookup("#doctor-history-follow-up").queryAs(TextInputControl.class).isEditable());
    assertTrue(
        lookup("#doctor-history-follow-up").queryAs(TextInputControl.class).getHeight() >= 72.0,
        "Follow-up notes field should show several lines of text");
    assertEquals(
        1,
        lookup("#doctor-history-prescription-list")
            .queryAs(javafx.scene.control.ListView.class)
            .getItems()
            .size());
    assertTrue(
        lookup("#doctor-history-prescription-list")
                .queryAs(javafx.scene.control.ListView.class)
                .getHeight()
            >= 96.0,
        "Prescription list should have enough height to show its rows");
    assertTrue(lookup("#doctor-history-save").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-history-edit").tryQuery().isEmpty());

    fire("#doctor-nav-patients");
    verifyThat("#doctor-patient-directory-view", isVisible());
    fire("#doctor-patient-clinical-history");
    verifyThat("#doctor-clinical-history-view", isVisible());
  }

  @Test
  void clinicalHistoryShowsAnExplicitEmptyStateForPatientsWithoutCompletedConsultations()
      throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-patients");
    fire("#doctor-patient-clinical-history");

    SearchSuggestionField<Patient> selector = historySelector();
    Patient patientWithoutHistory = services.patientService().getAdministrative(doctorSession(), 2);
    interact(() -> selector.select(patientWithoutHistory));
    fire("#doctor-history-load");

    assertEquals(
        "No completed consultations found for this patient.",
        lookup("#doctor-history-state").queryAs(Label.class).getText());
    assertTrue(
        lookup("#doctor-history-list")
            .queryAs(javafx.scene.control.ListView.class)
            .getItems()
            .isEmpty());
    verifyThat("#doctor-history-detail-empty", isVisible());
  }

  @Test
  void patientRegistrationRefreshesClinicalHistorySelector() {
    loginAsDoctor();
    fire("#doctor-nav-patients");
    fire("#doctor-patient-open-register");
    selectCombo("#doctor-register-identity-type", IdentityType.NRIC);
    selectCombo("#doctor-register-sex", Sex.FEMALE);
    setText("#doctor-register-identity-number", "S7654321A");
    setText("#doctor-register-first-name", "History");
    setText("#doctor-register-last-name", "Refresh");
    setDate("#doctor-register-date-of-birth", LocalDate.of(1991, 2, 3));
    setText("#doctor-register-phone-number", "5550111");
    setText("#doctor-register-email", "history.refresh@example.test");
    setText("#doctor-register-address", "History address");
    fire("#doctor-patient-register");
    verifyThat(
        "#doctor-feedback",
        org.testfx.matcher.control.LabeledMatchers.hasText("Patient registered"));

    fire("#doctor-patient-clinical-history");
    verifyThat("#doctor-clinical-history-view", isVisible());
    assertTrue(
        historySelector().getItems().stream()
            .anyMatch(patient -> patient.email().equals("history.refresh@example.test")));
  }
}
