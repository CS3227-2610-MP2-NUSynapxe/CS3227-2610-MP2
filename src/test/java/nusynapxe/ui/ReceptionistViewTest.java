package nusynapxe.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class ReceptionistViewTest extends ApplicationTest {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;
  private ClinicServices services;
  private Account doctor;
  private Account receptionist;

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

  @Test
  void receptionistBooksChecksInChecksOutAndViewsRevenue() throws SQLException {
    loginAsReceptionist();
    verifyThat("#receptionist-workspace", isVisible());
    assertEquals(4, workspaceTabs().getTabs().size());
    assertFalse(lookup("#reception-refresh").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-id").tryQuery().isPresent());

    selectCombo("#reception-register-sex", Sex.FEMALE);
    setText("#reception-register-first-name", "Pat");
    setText("#reception-register-last-name", "Lee");
    setText("#reception-register-identity-number", "S1234567D");
    setDate("#reception-register-date-of-birth", LocalDate.of(1990, 1, 1));
    setText("#reception-register-phone-number", "5550100");
    setText("#reception-register-email", "pat@example.test");
    setText("#reception-register-address", "Address");
    fire("#reception-patient-register");
    verifyThat("#reception-feedback", hasText("Patient registered"));
    selectWorkspaceTab(1);
    verifyThat("#reception-book", isVisible());

    LocalDateTime start = LocalDateTime.now().minusMinutes(5).withSecond(0).withNano(0);
    LocalDateTime end = start.plusMinutes(30);
    setText("#reception-start", start.format(DATE_TIME_FORMAT));
    setText("#reception-end", end.format(DATE_TIME_FORMAT));
    fire("#reception-book");
    verifyThat("#reception-feedback", hasText("Appointment booked and awaiting Doctor acceptance"));

    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    List<Appointment> bookedAppointments =
        services.appointmentService().allAppointments(receptionistSession);
    assertThat(bookedAppointments, hasSize(1));
    Appointment appointment = bookedAppointments.get(0);
    services.appointmentService().accept(doctorSession, appointment.id());

    selectWorkspaceTab(0);
    selectWorkspaceTab(1);
    selectFirstAppointment("#reception-appointment-list");
    fire("#reception-check-in");
    verifyThat("#reception-feedback", hasText("Patient checked in"));
    services.appointmentService().complete(doctorSession, appointment.id());

    selectWorkspaceTab(2);
    selectFirstAppointment("#reception-checkout-appointment-list");
    setText("#reception-charge", "45.00");
    fire("#reception-checkout");
    verifyThat("#reception-feedback", hasText("Checkout completed"));

    selectWorkspaceTab(3);
    setText("#reception-revenue-date", LocalDate.now().toString());
    fire("#reception-revenue-submit");
    verifyThat("#reception-revenue", hasText("1 successful payment(s), total 45.00"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  @Test
  void receptionistSearchesEditsDeactivatesAndRejectsDuplicateIdentity() throws SQLException {
    loginAsReceptionist();
    assertTrue(lookup("#reception-patient-tabs").tryQuery().isPresent());
    assertTrue(lookup("#reception-patient-register-tab").tryQuery().isPresent());
    assertTrue(lookup("#reception-patient-manage-tab").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-billing").tryQuery().isPresent());
    assertFalse(lookup("#reception-patient-billing").tryQuery().isPresent());
    assertFalse(lookup("#reception-refresh").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-id").tryQuery().isPresent());
    assertEquals(2, combo("#reception-register-sex").getItems().size());
    assertEquals(Locale.getISOCountries().length, countryCombo().getItems().size());
    assertEquals("SG", countryCombo().getItems().get(0).code());

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
    assertFalse(text("#reception-register-age").isBlank());
    setText("#reception-register-phone-number", "2071234567");
    setText("#reception-register-email", "foreign@example.test");
    setText("#reception-register-address", "Address");
    setText("#reception-register-height", "172");
    setText("#reception-register-weight", "68.2");
    fire("#reception-patient-register");

    verifyThat("#reception-feedback", hasText("Patient registered"));
    assertEquals("", text("#reception-register-identity-number"));

    selectPatientManagementTab();
    assertTrue(lookup("#reception-patient-id").tryQuery().isPresent());
    assertTrue(lookup("#reception-patient-search").tryQuery().isPresent());
    assertTrue(lookup("#reception-patient-update").tryQuery().isPresent());
    assertTrue(lookup("#reception-patient-deactivate").tryQuery().isPresent());

    setText("#reception-patient-search", "p000001");
    fire("#reception-patient-search-submit");
    assertEquals(1, patientList().getItems().size());
    selectFirstPatient();
    assertEquals("ABFOREIGN9", text("#reception-patient-identity-number"));
    setText("#reception-patient-phone-country-code", "33");
    setText("#reception-patient-phone-number", "123456789");
    fire("#reception-patient-update");
    verifyThat("#reception-feedback", hasText("Patient changes saved"));

    selectRegistrationTab();
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
    assertEquals(1, services.patientService().listAdministrative(receptionistSession()).size());
    assertEquals(
        "+33123456789",
        services.patientService().getAdministrative(receptionistSession(), 1).phone());

    selectPatientManagementTab();
    setText("#reception-patient-search", "does-not-exist");
    fire("#reception-patient-search-submit");
    assertTrue(patientList().getItems().isEmpty());
    fire("#reception-patient-search-clear");
    assertEquals(1, patientList().getItems().size());
    selectFirstPatient();
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient deactivated"));
    assertFalse(services.patientService().getAdministrative(receptionistSession(), 1).active());
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextField.class).setText(value));
  }

  private void setDate(String selector, LocalDate value) {
    interact(() -> lookup(selector).queryAs(DatePicker.class).setValue(value));
  }

  private String text(String selector) {
    return lookup(selector).queryAs(TextField.class).getText();
  }

  @SuppressWarnings("unchecked")
  private <T> void selectCombo(String selector, T value) {
    interact(() -> lookup(selector).queryAs(ComboBox.class).setValue(value));
  }

  @SuppressWarnings("unchecked")
  private <T> ComboBox<T> combo(String selector) {
    return lookup(selector).queryAs(ComboBox.class);
  }

  private ComboBox<CountryOption> countryCombo() {
    return combo("#reception-register-issuing-country");
  }

  private static CountryOption country(String code) {
    return CountryOption.fromCode(code).orElseThrow();
  }

  private void selectRegistrationTab() {
    interact(
        () ->
            lookup("#reception-patient-tabs").queryAs(TabPane.class).getSelectionModel().select(0));
  }

  private void selectWorkspaceTab(int index) {
    interact(() -> workspaceTabs().getSelectionModel().select(index));
  }

  private TabPane workspaceTabs() {
    return lookup("#reception-workspace-tabs").queryAs(TabPane.class);
  }

  private void selectPatientManagementTab() {
    interact(
        () ->
            lookup("#reception-patient-tabs").queryAs(TabPane.class).getSelectionModel().select(1));
  }

  private void selectFirstPatient() {
    interact(() -> patientList().getSelectionModel().selectFirst());
  }

  @SuppressWarnings("unchecked")
  private void selectFirstAppointment(String selector) {
    interact(() -> lookup(selector).queryAs(ListView.class).getSelectionModel().selectFirst());
  }

  @SuppressWarnings("unchecked")
  private ListView<Patient> patientList() {
    return lookup("#reception-patient-list").queryAs(ListView.class);
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  private void loginAsReceptionist() {
    setText("#login-username", "reception");
    setText("#login-password", "reception-pass");
    fire("#login-submit");
    waitForNode("#receptionist-workspace");
  }

  private Session receptionistSession() {
    return new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
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
