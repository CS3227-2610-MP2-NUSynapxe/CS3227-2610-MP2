package nusynapxe.ui;

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
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.PaymentRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class DoctorViewTest extends ApplicationTest {
  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;
  private ClinicServices services;
  private Account doctor;
  private Account receptionist;
  private LocalDate appointmentDate;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("doctor-ui.db"));
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
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "", "555-0100", "", ""));
    Patient geometryPatient =
        new PatientRepository(database)
            .create(new Patient(0, "Alex", "Tan", "", "555-0101", "", ""));
    LocalDateTime start =
        LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusMinutes(10).withSecond(0).withNano(0);
    AppointmentRepository appointments = new AppointmentRepository(database);
    appointments.create(
        patient.id(), doctor.id(), start, start.plusMinutes(30), AppointmentStatus.CHECKED_IN);
    LocalDateTime oneHourStart = start.getHour() < 21 ? start.plusHours(2) : start.minusHours(2);
    appointments.create(
        geometryPatient.id(),
        doctor.id(),
        oneHourStart,
        oneHourStart.plusHours(1),
        AppointmentStatus.ACCEPTED);
    appointmentDate = start.toLocalDate();
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
  void doctorOpensAssignedConsultationAddsPrescriptionAndCompletesVisit() {
    setText("#login-username", "doctor");
    setText("#login-password", "doctor-pass");
    fire("#login-submit");
    waitForNode("#doctor-workspace");

    verifyThat("#doctor-workspace", isVisible());
    verifyThat("#doctor-master-detail", isVisible());
    assertTrue(lookup("#doctor-detail-scroll").tryQuery().isPresent());
    assertTrue(lookup("#doctor-no-selection").tryQuery().isPresent());
    verifyThat("#doctor-accept", isVisible());
    verifyThat("#doctor-dashboard-day-calendar", isVisible());
    assertTrue(lookup("#doctor-appointment-list").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-timeoff-submit").tryQuery().isEmpty());
    DatePicker dashboardDate = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);
    assertEquals(appointmentDate, dashboardDate.getValue());
    assertTrue(dashboardDate.getStyleClass().contains("compact-date-picker"));
    assertEquals(
        "Dashboard", lookup("#doctor-schedule-card .page-title").queryAs(Label.class).getText());
    Label dashboardHelp = lookup("#doctor-schedule-card .supporting-text").queryAs(Label.class);
    assertTrue(dashboardHelp.isWrapText());
    assertEquals("Select an appointment to open its clinical context.", dashboardHelp.getText());
    assertEquals(
        12.0, BorderPane.getMargin(lookup("#doctor-dashboard-time-grid").query()).getTop(), 0.1);
    selectDashboardAppointment(1);
    verifyThat("#doctor-selected-appointment", isVisible());

    setText("#doctor-diagnosis", "Seasonal allergies");
    setText("#doctor-consultation-notes", "Discussed symptoms and treatment options");
    setText("#doctor-follow-up", "Review in two weeks");
    fire("#doctor-consultation-save");
    verifyThat("#doctor-feedback", hasText("Consultation saved"));

    setText("#doctor-medication", "Cetirizine");
    setText("#doctor-dosage", "10 mg");
    setText("#doctor-frequency", "Once daily");
    setText("#doctor-duration", "14 days");
    setText("#doctor-instructions", "Take in the evening");
    fire("#doctor-prescription-submit");
    verifyThat("#doctor-feedback", hasText("Prescription added"));

    fire("#doctor-complete");
    verifyThat("#doctor-feedback", hasText("Appointment marked completed"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  @Test
  void doctorCanCheckInAnAcceptedAppointmentFromTheDashboard() throws SQLException {
    new AppointmentRepository(database).updateStatus(1, AppointmentStatus.ACCEPTED);
    loginAsDoctor();
    selectDashboardAppointment(1);
    assertFalse(lookup("#doctor-check-in").queryAs(Button.class).isDisable());
    fire("#doctor-check-in");
    verifyThat("#doctor-feedback", hasText("Patient checked in"));
    assertEquals(AppointmentStatus.CHECKED_IN, services.appointmentService().get(1).status());
  }

  @Test
  void dashboardNavigatesDaysAndRefreshesAVisibleSelection() {
    loginAsDoctor();
    DatePicker date = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);
    assertEquals(appointmentDate, date.getValue());
    selectDashboardAppointment(1);
    fire("#doctor-dashboard-refresh");
    verifyThat("#doctor-selected-appointment", isVisible());

    fire("#doctor-dashboard-next");
    assertEquals(appointmentDate.plusDays(1), date.getValue());
    assertTrue(lookup("#doctor-no-selection").query().isVisible());
    interact(
        () -> {
          date.setValue(appointmentDate.plusDays(5));
          date.getOnAction().handle(new javafx.event.ActionEvent());
        });
    assertTrue(lookup("#doctor-calendar-empty").tryQuery().isPresent());
    fire("#doctor-dashboard-today");
    assertEquals(appointmentDate, date.getValue());
  }

  @Test
  void dashboardAndPatientDirectoryUseTheApprovedCompactPageLayout() {
    loginAsDoctor();
    DatePicker dashboardDate = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);
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
  void compactDashboardKeepsProportionalAppointmentGeometryAndNoInlineActions() {
    loginAsDoctor();
    var thirtyMinutes =
        lookup("#doctor-calendar-appointment-1-" + appointmentDate).query().getBoundsInParent();
    var oneHour =
        lookup("#doctor-calendar-appointment-2-" + appointmentDate).query().getBoundsInParent();
    assertEquals(thirtyMinutes.getHeight() * 2 + 4, oneHour.getHeight(), 0.1);
    assertTrue(lookup("#doctor-calendar-accept-1").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-calendar-decline-1").tryQuery().isEmpty());
    assertTrue(
        lookup("#doctor-dashboard-time-grid")
            .query()
            .getStyleClass()
            .contains("calendar-time-grid-compact"));
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
            .queryAs(javafx.scene.control.Label.class)
            .getText()
            .contains("deactivate the patient instead"));
    fire("#doctor-patient-delete-blocked-close");
    join(deleteThread);
    verifyThat("#doctor-patient-view", isVisible());
  }

  private void selectDashboardAppointment(long id) {
    interact(
        () ->
            lookup("#doctor-calendar-appointment-" + id + "-" + appointmentDate)
                .query()
                .getOnMouseClicked()
                .handle(primaryClick()));
    WaitForAsyncUtils.waitForFxEvents();
  }

  private static MouseEvent primaryClick() {
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

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  @SuppressWarnings("unchecked")
  private <T> void selectCombo(String selector, T value) {
    interact(() -> lookup(selector).queryAs(ComboBox.class).setValue(value));
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

  @SuppressWarnings("unchecked")
  private TableView<Patient> patientTable() {
    return lookup("#doctor-patient-table").queryAs(TableView.class);
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

  private String tableValue(int columnIndex, Patient patient) {
    return (String)
        patientTable().getColumns().get(columnIndex).getCellObservableValue(patient).getValue();
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

  private void loginAsDoctor() {
    setText("#login-username", "doctor");
    setText("#login-password", "doctor-pass");
    fire("#login-submit");
    waitForNode("#doctor-workspace");
  }

  private Session doctorSession() {
    return new Session(doctor.id(), doctor.username(), Role.DOCTOR);
  }

  private void addPatientHistory() throws SQLException {
    ClinicalRecord clinicalRecord =
        new ClinicalRecordRepository(database)
            .save(
                new ClinicalRecord(
                    0,
                    1,
                    1,
                    doctor.id(),
                    "Existing diagnosis",
                    "Existing notes",
                    "Existing follow-up"));
    new ClinicalRecordRepository(database)
        .addPrescription(
            new Prescription(
                0,
                clinicalRecord.id(),
                "Existing medicine",
                "10 mg",
                "Daily",
                "7 days",
                "Take with food"));
    services.appointmentService().complete(doctorSession(), 1);
    new PaymentRepository(database)
        .createCheckout(
            new Payment(
                0,
                1,
                1,
                receptionist.id(),
                2500,
                PaymentMethod.CARD,
                PaymentStatus.SUCCESSFUL,
                LocalDateTime.of(2026, 9, 2, 10, 0)));
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

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
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
