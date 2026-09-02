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
import java.time.Month;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueReport;
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
    assertEquals(5, workspaceTabs().getTabs().size());
    assertTrue(lookup("#reception-schedule-date").tryQuery().isPresent());
    assertTrue(lookup("#reception-schedule-doctor").tryQuery().isPresent());
    assertTrue(lookup("#reception-schedule-status").tryQuery().isPresent());
    assertTrue(lookup("#reception-schedule-summary").tryQuery().isPresent());
    assertFalse(lookup("#reception-refresh").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-id").tryQuery().isPresent());

    fire("#reception-patient-open-register");
    verifyThat("#reception-patient-register-view", isVisible());
    selectCombo("#reception-register-identity-type", IdentityType.NRIC);
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
    selectCombo("#reception-start-hour", String.format("%02d", start.getHour()));
    selectCombo("#reception-start-minute", "00");
    selectCombo("#reception-end-hour", String.format("%02d", start.getHour()));
    selectCombo("#reception-end-minute", "30");
    fire("#reception-book");
    verifyThat("#reception-feedback", hasText("Appointment booked and awaiting Doctor acceptance"));
    assertTrue(textLabel("#reception-schedule-summary").contains("Pending: 1"));
    selectCombo("#reception-schedule-status", AppointmentStatus.PENDING);
    assertEquals(1, appointmentList().getItems().size());
    interact(() -> combo("#reception-schedule-status").setValue(null));

    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    List<Appointment> bookedAppointments =
        services.appointmentService().allAppointments(receptionistSession);
    assertThat(bookedAppointments, hasSize(1));
    Appointment appointment = bookedAppointments.get(0);
    services.appointmentService().accept(doctorSession, appointment.id());

    selectWorkspaceTab(2);
    assertTrue(lookup("#reception-check-in-queue-list").tryQuery().isPresent());
    assertTrue(lookup("#reception-check-in-queue-summary").tryQuery().isPresent());
    selectFirstAppointment("#reception-check-in-queue-list");
    services.appointmentService().checkIn(receptionistSession, appointment.id());
    assertEquals(
        AppointmentStatus.CHECKED_IN, services.appointmentService().get(appointment.id()).status());
    services.appointmentService().complete(doctorSession, appointment.id());

    selectWorkspaceTab(3);
    selectFirstAppointment("#reception-checkout-appointment-list");
    setText("#reception-charge", "45.00");
    fire("#reception-checkout");
    verifyThat("#reception-feedback", hasText("Checkout completed"));

    selectWorkspaceTab(4);
    setText("#reception-revenue-date", LocalDate.now().toString());
    fire("#reception-revenue-submit");
    verifyThat("#reception-revenue", hasText("1 successful payment(s), total 45.00"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  @Test
  void revenueReportSupportsFiltersAndEmptyState() {
    loginAsReceptionist();
    selectWorkspaceTab(4);
    assertTrue(lookup("#reception-revenue-report-patient").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-report-doctor").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-report-method").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-export-csv").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-export-json").tryQuery().isPresent());

    setDatePicker("#reception-revenue-report-from", LocalDate.of(2030, 1, 1));
    setDatePicker("#reception-revenue-report-to", LocalDate.of(2030, 1, 1));
    setText("#reception-revenue-report-patient", "does-not-exist");
    fire("#reception-revenue-report");
    assertTrue(
        textLabel("#reception-revenue-report-summary").startsWith("0 successful payment(s)"));
    assertTrue(
        lookup("#reception-revenue-report-list").queryAs(ListView.class).getItems().isEmpty());
  }

  @Test
  void revenueReportExportsIncludeReceiptDetails() {
    Receipt receipt =
        new Receipt(
            1,
            2,
            3,
            4,
            "Pat Lee",
            "Dr. Ada",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            7,
            LocalDateTime.of(2026, 9, 1, 12, 30));
    RevenueReport report = new RevenueReport(List.of(receipt));

    String csv = ReceptionistView.reportCsv(report);
    assertTrue(csv.startsWith("receipt,dateTime,patientId,patientName,doctor,amount,method"));
    assertTrue(csv.contains("7,2026-09-01T12:30,4,Pat Lee,Dr. Ada,45.00,CARD"));

    String json = ReceptionistView.reportJson(report);
    assertTrue(json.contains("\"receiptCount\":1"));
    assertTrue(json.contains("\"patientName\":\"Pat Lee\""));
    assertTrue(json.contains("\"method\":\"CARD\""));
  }

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
        List.of("Patient ID", "Name", "Date of birth", "Phone", "Email", "Status", "Actions"),
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
    fire("#reception-patient-edit-" + editedPatient.id());
    waitForNode("#reception-patient-edit-view");
    assertFalse(lookup("#reception-patient-details-window").tryQuery().isPresent());
    assertCompactPatientSelectors("reception-patient");
    assertTrue(lookup("#reception-patient-id").tryQuery().isPresent());
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
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
    verifyThat("#reception-patient-directory-view", isVisible());

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
    fire("#reception-patient-edit-" + activePatient.id());
    waitForNode("#reception-patient-edit-view");
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
    assertTrue(lookup("#reception-patient-edit-" + registeredPatient.id()).tryQuery().isPresent());
    fire("#reception-patient-edit-" + registeredPatient.id());
    waitForNode("#reception-patient-edit-view");
    assertFalse(lookup("#reception-patient-details-window").tryQuery().isPresent());
    assertEquals("P000001", text("#reception-patient-id"));

    setText("#reception-patient-phone-number", "draft-only");
    fire("#reception-patient-edit-cancel");
    verifyThat("#reception-patient-directory-view", isVisible());
    assertEquals(
        "+656565656565",
        services
            .patientService()
            .getAdministrative(receptionistSession(), registeredPatient.id())
            .phone());

    preparePatientTable();
    fire("#reception-patient-edit-" + registeredPatient.id());
    waitForNode("#reception-patient-edit-view");

    Thread cancelThread = new Thread(() -> fire("#reception-patient-delete"));
    cancelThread.start();
    waitForNode("#reception-patient-delete-confirm-window");
    fire("#reception-patient-delete-cancel");
    join(cancelThread);
    verifyThat("#reception-patient-edit-view", isVisible());

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
    fire("#reception-patient-edit-" + editedPatient.id());
    waitForNode("#reception-patient-edit-view");

    // Verify patient information is displayed on the edit page.
    assertEquals("T1234567B", text("#reception-patient-identity-number"));
    assertEquals("Jane", text("#reception-patient-first-name"));
    assertEquals("Smith", text("#reception-patient-last-name"));
    assertTrue(text("#reception-patient-height").startsWith("165"));
    assertEquals("62.5", text("#reception-patient-weight"));

    // Edit patient information
    setText("#reception-patient-phone-number", "9876543");
    setText("#reception-patient-email", "jane.updated@example.test");
    fire("#reception-patient-update");
    verifyThat("#reception-feedback", hasText("Patient changes saved"));
    verifyThat("#reception-patient-directory-view", isVisible());

    // Verify changes persisted
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
    fire("#reception-patient-edit-" + activePatient.id());
    waitForNode("#reception-patient-edit-view");

    // Verify initial status is "Deactivate patient"
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
    assertTrue(services.patientService().getAdministrative(receptionistSession(), 1).active());

    // Deactivate patient
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient deactivated"));
    verifyThat("#reception-patient-deactivate", hasText("Activate patient"));
    assertFalse(services.patientService().getAdministrative(receptionistSession(), 1).active());

    // Reactivate patient
    fire("#reception-patient-deactivate");
    verifyThat("#reception-feedback", hasText("Patient activated"));
    verifyThat("#reception-patient-deactivate", hasText("Deactivate patient"));
    assertTrue(services.patientService().getAdministrative(receptionistSession(), 1).active());
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextField.class).setText(value));
  }

  @SuppressWarnings("unchecked")
  private void setDate(String selector, LocalDate value) {
    interact(
        () -> {
          lookup(selector + "-day").queryAs(ComboBox.class).setValue(value.getDayOfMonth());
          lookup(selector + "-month").queryAs(ComboBox.class).setValue(value.getMonth());
          lookup(selector + "-year").queryAs(ComboBox.class).setValue(value.getYear());
        });
  }

  private void setDatePicker(String selector, LocalDate value) {
    interact(() -> lookup(selector).queryAs(javafx.scene.control.DatePicker.class).setValue(value));
  }

  private String text(String selector) {
    return lookup(selector).queryAs(TextField.class).getText();
  }

  private TextField textField(String selector) {
    return lookup(selector).queryAs(TextField.class);
  }

  private String textLabel(String selector) {
    return lookup(selector).queryAs(javafx.scene.control.Label.class).getText();
  }

  @SuppressWarnings("unchecked")
  private ListView<Appointment> appointmentList() {
    return lookup("#reception-appointment-list").queryAs(ListView.class);
  }

  @SuppressWarnings("unchecked")
  private LocalDate date(String selector) {
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

  private void selectWorkspaceTab(int index) {
    interact(() -> workspaceTabs().getSelectionModel().select(index));
  }

  private TabPane workspaceTabs() {
    return lookup("#reception-workspace-tabs").queryAs(TabPane.class);
  }

  @SuppressWarnings("unchecked")
  private void selectFirstAppointment(String selector) {
    interact(() -> lookup(selector).queryAs(ListView.class).getSelectionModel().selectFirst());
  }

  @SuppressWarnings("unchecked")
  private TableView<Patient> patientTable() {
    return lookup("#reception-patient-table").queryAs(TableView.class);
  }

  private void preparePatientTable() {
    interact(
        () -> {
          TableView<Patient> table = patientTable();
          table.scrollTo(0);
          table.applyCss();
          table.layout();
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void assertCompactPatientSelectors(String formPrefix) {
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

  private void join(Thread thread) {
    try {
      thread.join(60_000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for patient dialog", exception);
    }
    assertFalse(thread.isAlive(), "Patient dialog action did not finish");
  }
}
