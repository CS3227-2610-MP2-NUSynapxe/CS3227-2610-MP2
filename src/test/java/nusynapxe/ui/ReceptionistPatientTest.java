package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Locale;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Sex;
import org.junit.jupiter.api.Test;

final class ReceptionistPatientTest extends ReceptionistViewTestSupport {
  @Test
  void receptionistSearchesEditsDeactivatesAndRejectsDuplicateIdentity() throws SQLException {
    loginAsReceptionist();
    verifyThat("#reception-patient-directory-view", isVisible());
    assertFalse(lookup("#reception-patient-tabs").tryQuery().isPresent());
    assertFalse(lookup("#reception-patient-register-tab").tryQuery().isPresent());
    assertFalse(lookup("#reception-patient-manage-tab").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-billing").tryQuery().isPresent());
    assertFalse(lookup("#reception-patient-billing").tryQuery().isPresent());
    assertFalse(lookup("#reception-refresh").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-id").tryQuery().isPresent());
    assertEquals(
        List.of("Name", "Date of birth", "Phone", "Email", "Status", "Actions"),
        patientTable().getColumns().stream().map(column -> column.getText()).toList());
    assertCompactPatientSelectors("reception-register");
    assertEquals(2, combo("#reception-register-sex").getItems().size());
    assertEquals(Sex.MALE, combo("#reception-register-sex").getItems().get(0));
    assertEquals("", textField("#reception-register-age").getPromptText());
    verifyThat("#reception-register-phone-plus", hasText("+"));
    assertEquals(Locale.getISOCountries().length, countryCombo().getItems().size());
    assertEquals("SG", countryCombo().getItems().get(0).code());

    fire("#reception-patient-open-register");
    verifyThat("#reception-patient-register-view", isVisible());
    selectCombo("#reception-register-identity-type", IdentityType.FIN);
    assertEquals("SG", countryCombo().getValue().code());
    assertTrue(countryCombo().isDisabled());
    selectCombo("#reception-register-identity-type", IdentityType.PASSPORT);
    assertFalse(countryCombo().isDisabled());
    selectCombo("#reception-register-issuing-country", country("GB"));
    assertEquals("44", text("#reception-register-phone-country-code"));
    selectCombo("#reception-register-sex", Sex.FEMALE);
    setText("#reception-register-identity-number", " abforeign9 ");
    setText("#reception-register-first-name", "Foreign");
    setText("#reception-register-last-name", "Patient");
    setDate("#reception-register-date-of-birth", LocalDate.of(1991, 2, 3));
    assertEquals(Month.FEBRUARY, combo("#reception-register-date-of-birth-month").getValue());
    assertEquals(1991, combo("#reception-register-date-of-birth-year").getValue());
    selectCombo("#reception-register-date-of-birth-month", Month.MARCH);
    assertEquals(LocalDate.of(1991, 3, 3), date("#reception-register-date-of-birth"));
    selectCombo("#reception-register-date-of-birth-month", Month.FEBRUARY);
    assertFalse(text("#reception-register-age").isBlank());
    setText("#reception-register-phone-number", "2071234567");
    setText("#reception-register-email", "foreign@example.test");
    setText("#reception-register-address", "Address");
    setText("#reception-register-height", "172");
    setText("#reception-register-weight", "68.2");
    fire("#reception-patient-register");
    verifyThat("#reception-feedback", hasText("Patient registered"));
    verifyThat("#reception-patient-directory-view", isVisible());
    assertTrue(lookup("#reception-patient-search").tryQuery().isPresent());
    assertFalse(lookup("#reception-patient-id").tryQuery().isPresent());
    assertFalse(lookup("#reception-patient-update").tryQuery().isPresent());
    setText("#reception-patient-search", "p000001");
    fire("#reception-patient-search-submit");
    assertEquals(1, patientTable().getItems().size());
    Patient editedPatient = patientTable().getItems().get(0);
    preparePatientTable();
    fire("#reception-patient-view-" + editedPatient.id());
    waitForNode("#reception-patient-view");
    assertFalse(lookup("#reception-patient-details-window").tryQuery().isPresent());
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
    fire("#reception-patient-edit");
    waitForNode("#reception-patient-edit-view");
    assertCompactPatientSelectors("reception-patient");
    assertFalse(lookup("#reception-patient-id").tryQuery().isPresent());
    assertEquals("ABFOREIGN9", text("#reception-patient-identity-number"));
    setText("#reception-patient-phone-number", "not-digits");
    fire("#reception-patient-update");
    assertTrue(
        lookup("#reception-patient-edit-feedback")
            .queryAs(javafx.scene.control.Label.class)
            .getText()
            .contains("Phone number"));
    assertEquals(1, patientTable().getItems().size());
    setText("#reception-patient-phone-country-code", "33");
    setText("#reception-patient-phone-number", "123456789");
    fire("#reception-patient-update");
    verifyThat("#reception-feedback", hasText("Patient changes saved"));
    verifyThat("#reception-patient-view", isVisible());
    fire("#reception-patient-view-back");

    fire("#reception-patient-open-register");
    verifyThat("#reception-patient-register-view", isVisible());
    selectCombo("#reception-register-identity-type", IdentityType.PASSPORT);
    selectCombo("#reception-register-issuing-country", country("GB"));
    selectCombo("#reception-register-sex", Sex.FEMALE);
    setText("#reception-register-identity-number", "ABFOREIGN9");
    setText("#reception-register-first-name", "Duplicate");
    setText("#reception-register-last-name", "Patient");
    setDate("#reception-register-date-of-birth", LocalDate.of(1991, 2, 3));
    setText("#reception-register-phone-number", "9999999");
    setText("#reception-register-email", "duplicate@example.test");
    setText("#reception-register-address", "Address");
    fire("#reception-patient-register");
    verifyThat(
        "#reception-feedback", hasText("A patient with this identity document already exists"));
    verifyThat("#reception-patient-register-view", isVisible());
    assertEquals("ABFOREIGN9", text("#reception-register-identity-number"));
    assertEquals(1, services.patientService().listAdministrative(receptionistSession()).size());
    assertEquals(
        "+33123456789",
        services.patientService().getAdministrative(receptionistSession(), 1).phone());
    fire("#reception-patient-register-cancel");
    verifyThat("#reception-patient-directory-view", isVisible());
    setText("#reception-patient-search", "does-not-exist");
    fire("#reception-patient-search-submit");
    assertTrue(patientTable().getItems().isEmpty());
    fire("#reception-patient-search-clear");
    assertEquals(1, patientTable().getItems().size());
    Patient activePatient = patientTable().getItems().get(0);
    preparePatientTable();
    fire("#reception-patient-view-" + activePatient.id());
    waitForNode("#reception-patient-view");
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient deactivated"));
    verifyThat("#reception-patient-deactivate", hasText("Activate patient"));
    assertFalse(services.patientService().getAdministrative(receptionistSession(), 1).active());
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient activated"));
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
    assertTrue(services.patientService().getAdministrative(receptionistSession(), 1).active());
  }

  @Test
  void patientTableRowsOpenInEditPageAndSupportDeletion() throws SQLException {
    loginAsReceptionist();
    fire("#reception-patient-open-register");
    selectCombo("#reception-register-identity-type", IdentityType.NRIC);
    selectCombo("#reception-register-sex", Sex.MALE);
    setText("#reception-register-first-name", "John");
    setText("#reception-register-last-name", "Doe");
    setText("#reception-register-identity-number", "S9876543A");
    setDate("#reception-register-date-of-birth", LocalDate.of(1985, 5, 15));
    setText("#reception-register-phone-number", "6565656565");
    setText("#reception-register-email", "john.doe@example.test");
    setText("#reception-register-address", "123 Main Street");
    fire("#reception-patient-register");
    verifyThat("#reception-feedback", hasText("Patient registered"));
    verifyThat("#reception-patient-directory-view", isVisible());
    assertEquals(1, patientTable().getItems().size());
    Patient registeredPatient = patientTable().getItems().get(0);
    preparePatientTable();
    assertTrue(lookup("#reception-patient-view-" + registeredPatient.id()).tryQuery().isPresent());
    fire("#reception-patient-view-" + registeredPatient.id());
    waitForNode("#reception-patient-view");
    assertFalse(lookup("#reception-patient-details-window").tryQuery().isPresent());
    fire("#reception-patient-edit");
    waitForNode("#reception-patient-edit-view");
    assertFalse(lookup("#reception-patient-id").tryQuery().isPresent());
    setText("#reception-patient-phone-number", "draft-only");
    fire("#reception-patient-edit-cancel");
    verifyThat("#reception-patient-view", isVisible());
    assertEquals(
        "+656565656565",
        services
            .patientService()
            .getAdministrative(receptionistSession(), registeredPatient.id())
            .phone());
    Thread cancelThread = new Thread(() -> fire("#reception-patient-delete"));
    cancelThread.start();
    waitForNode("#reception-patient-delete-confirm-window");
    fire("#reception-patient-delete-cancel");
    join(cancelThread);
    verifyThat("#reception-patient-view", isVisible());
    Thread deleteThread = new Thread(() -> fire("#reception-patient-delete"));
    deleteThread.start();
    waitForNode("#reception-patient-delete-confirm-window");
    fire("#reception-patient-delete-confirm");
    join(deleteThread);
    verifyThat("#reception-feedback", hasText("Patient deleted"));
    assertTrue(patientTable().getItems().isEmpty());
  }

  @Test
  void patientEditPageDisplaysAndEditsPatientInformation() throws SQLException {
    loginAsReceptionist();
    fire("#reception-patient-open-register");
    selectCombo("#reception-register-identity-type", IdentityType.NRIC);
    selectCombo("#reception-register-sex", Sex.FEMALE);
    setText("#reception-register-identity-number", "T1234567B");
    setText("#reception-register-first-name", "Jane");
    setText("#reception-register-last-name", "Smith");
    setDate("#reception-register-date-of-birth", LocalDate.of(1992, 8, 20));
    setText("#reception-register-phone-number", "8888888");
    setText("#reception-register-email", "jane.smith@example.test");
    setText("#reception-register-address", "456 Oak Avenue");
    setText("#reception-register-height", "165");
    setText("#reception-register-weight", "62.5");
    fire("#reception-patient-register");
    verifyThat("#reception-patient-directory-view", isVisible());
    Patient editedPatient = patientTable().getItems().get(0);
    preparePatientTable();
    fire("#reception-patient-view-" + editedPatient.id());
    waitForNode("#reception-patient-view");
    fire("#reception-patient-edit");
    waitForNode("#reception-patient-edit-view");
    assertEquals("T1234567B", text("#reception-patient-identity-number"));
    assertEquals("Jane", text("#reception-patient-first-name"));
    assertEquals("Smith", text("#reception-patient-last-name"));
    assertTrue(text("#reception-patient-height").startsWith("165"));
    assertEquals("62.5", text("#reception-patient-weight"));
    setText("#reception-patient-phone-number", "9876543");
    setText("#reception-patient-email", "jane.updated@example.test");
    fire("#reception-patient-update");
    verifyThat("#reception-feedback", hasText("Patient changes saved"));
    verifyThat("#reception-patient-view", isVisible());
    assertEquals(
        "+659876543",
        services.patientService().getAdministrative(receptionistSession(), 1).phone());
    assertEquals(
        "jane.updated@example.test",
        services.patientService().getAdministrative(receptionistSession(), 1).email());
  }

  @Test
  void patientStatusTogglingWorksCorrectly() throws SQLException {
    loginAsReceptionist();
    fire("#reception-patient-open-register");
    selectCombo("#reception-register-identity-type", IdentityType.NRIC);
    selectCombo("#reception-register-sex", Sex.MALE);
    setText("#reception-register-first-name", "Active");
    setText("#reception-register-last-name", "Patient");
    setText("#reception-register-identity-number", "S5555555E");
    setDate("#reception-register-date-of-birth", LocalDate.of(1980, 12, 25));
    setText("#reception-register-phone-number", "1111111");
    setText("#reception-register-email", "active@example.test");
    setText("#reception-register-address", "789 Pine Road");
    fire("#reception-patient-register");
    verifyThat("#reception-patient-directory-view", isVisible());
    Patient activePatient = patientTable().getItems().get(0);
    preparePatientTable();
    fire("#reception-patient-view-" + activePatient.id());
    waitForNode("#reception-patient-view");
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
    assertTrue(services.patientService().getAdministrative(receptionistSession(), 1).active());
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient deactivated"));
    verifyThat("#reception-patient-deactivate", hasText("Activate patient"));
    assertFalse(services.patientService().getAdministrative(receptionistSession(), 1).active());
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient activated"));
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
    assertTrue(services.patientService().getAdministrative(receptionistSession(), 1).active());
  }
}
