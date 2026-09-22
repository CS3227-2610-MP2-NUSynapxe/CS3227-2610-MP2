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
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.CalendarService;
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
    assertEquals(6, workspaceTabs().getTabs().size());
    verifyThat("#reception-navigation-title", hasText("Navigation"));
    verifyThat("#reception-nav-directory", hasText("Directory"));
    verifyThat("#reception-nav-appointments", hasText("Appointments"));
    verifyThat("#reception-nav-calendar", hasText("Calendar"));
    fire("#reception-nav-calendar");
    verifyThat("#reception-calendar-page", isVisible());
    assertTrue(lookup("#reception-calendar-doctor").tryQuery().isPresent());
    fire("#reception-nav-directory");
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

    LocalDateTime start =
        LocalDateTime.now(CalendarService.CLINIC_ZONE)
            .minusHours(1)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    LocalDateTime end = start.plusMinutes(30);
    setDatePicker("#reception-appointment-date", start.toLocalDate());
    selectCombo("#reception-start-hour", String.format("%02d", start.getHour()));
    selectCombo("#reception-start-minute", String.format("%02d", start.getMinute()));
    selectCombo("#reception-end-hour", String.format("%02d", end.getHour()));
    selectCombo("#reception-end-minute", String.format("%02d", end.getMinute()));
    fire("#reception-book");
    verifyThat("#reception-feedback", hasText("Appointment booked and awaiting Doctor acceptance"));
    assertTrue(textLabel("#reception-schedule-summary").contains("Pending: 1"));
    selectCombo("#reception-schedule-status", "Pending");
    assertEquals(1, appointmentList().getItems().size());
    selectCombo("#reception-schedule-status", "All statuses");

    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    List<Appointment> bookedAppointments =
        services.appointmentService().allAppointments(receptionistSession);
    assertThat(bookedAppointments, hasSize(1));
    Appointment appointment = bookedAppointments.get(0);
    services.appointmentService().accept(doctorSession, appointment.id());

    selectWorkspaceTab(3);
    assertTrue(lookup("#reception-check-in-queue-list").tryQuery().isPresent());
    assertTrue(lookup("#reception-check-in-queue-summary").tryQuery().isPresent());
    selectFirstAppointment("#reception-check-in-queue-list");
    services.appointmentService().checkIn(receptionistSession, appointment.id());
    assertEquals(
        AppointmentStatus.CHECKED_IN, services.appointmentService().get(appointment.id()).status());
    services.appointmentService().complete(doctorSession, appointment.id());

    selectWorkspaceTab(4);
    selectFirstAppointment("#reception-checkout-appointment-list");
    setText("#reception-charge", "45.00");
    fire("#reception-checkout");
    verifyThat("#reception-feedback", hasText("Checkout completed"));

    selectWorkspaceTab(5);
    setText("#reception-revenue-date", LocalDate.now().toString());
    fire("#reception-revenue-submit");
    verifyThat("#reception-revenue", hasText("1 successful payment(s), total 45.00"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  @Test
  void doctorAndPatientSearchFieldsShowKeyboardSelectableSuggestions() {
    loginAsReceptionist();
    selectWorkspaceTab(1);

    String[] selectors = {
      "#reception-start-hour",
      "#reception-start-minute",
      "#reception-end-hour",
      "#reception-end-minute",
      "#reception-schedule-status",
      "#reception-check-in-queue-status",
      "#reception-revenue-report-method"
    };
    for (String selector : selectors) {
      assertTrue(
          combo(selector).getStyleClass().contains("compact-selector"),
          "Expected compact selector style for " + selector);
    }

    TextField doctorSearch = textField("#reception-doctor");
    interact(() -> doctorSearch.clear());
    clickOn(doctorSearch);
    interact(() -> doctorSearch.setText(doctor.displayName()));
    SearchSuggestionField<?> doctorSuggestions =
        (SearchSuggestionField<?>) doctorSearch.getParent();
    assertFalse(doctorSuggestions.suggestionList().getItems().isEmpty());
    interact(
        () ->
            doctorSearch.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.DOWN,
                    false,
                    false,
                    false,
                    false)));
    interact(
        () ->
            doctorSearch.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.ENTER,
                    false,
                    false,
                    false,
                    false)));
    interact(() -> combo("#reception-start-hour").requestFocus());
    WaitForAsyncUtils.waitForFxEvents();
    assertTrue(
        doctorSearch.getText().contains(doctor.displayName()),
        () -> "Doctor search text after keyboard selection: " + doctorSearch.getText());
    assertTrue(lookup("#reception-appointment-patient").query() instanceof TextField);

    assertEquals(
        GridPane.getRowIndex(lookup("#reception-appointment-date").query().getParent()),
        GridPane.getRowIndex(lookup("#reception-start").query().getParent()));
    assertEquals(
        GridPane.getRowIndex(lookup("#reception-appointment-date").query().getParent()),
        GridPane.getRowIndex(lookup("#reception-end").query().getParent()));
    layoutWorkspace();
    Node appointmentDate = lookup("#reception-appointment-date").query();
    Node appointmentStart = lookup("#reception-start").query();
    Node appointmentEnd = lookup("#reception-end").query();
    assertCompactDatePickerBounds((DatePicker) appointmentDate);
    assertEquals(
        appointmentStart.getBoundsInParent().getWidth(),
        appointmentEnd.getBoundsInParent().getWidth(),
        2.0);
  }

  @Test
  void everyReceptionDatePickerUsesTheSharedMinimalStyle() {
    loginAsReceptionist();
    String[] selectors = {
      "#reception-schedule-date",
      "#reception-appointment-date",
      "#reception-calendar-from",
      "#reception-calendar-to",
      "#reception-check-in-queue-date",
      "#reception-checkout-date",
      "#reception-receipt-date",
      "#reception-revenue-report-from",
      "#reception-revenue-report-to"
    };
    for (String selector : selectors) {
      assertTrue(
          lookup(selector)
              .queryAs(DatePicker.class)
              .getStyleClass()
              .contains("compact-date-picker"),
          "Expected minimal date-picker style for " + selector);
    }
  }

  @Test
  void everyReceptionDatePickerUsesTheSharedCompactWidth() {
    loginAsReceptionist();
    layoutWorkspace();
    String[] selectors = {
      "#reception-schedule-date",
      "#reception-appointment-date",
      "#reception-calendar-from",
      "#reception-calendar-to",
      "#reception-check-in-queue-date",
      "#reception-checkout-date",
      "#reception-receipt-date",
      "#reception-revenue-report-from",
      "#reception-revenue-report-to"
    };
    for (String selector : selectors) {
      DatePicker picker = lookup(selector).queryAs(DatePicker.class);
      picker.applyCss();
      picker.layout();
      assertEquals(150.0, picker.getPrefWidth(), 0.1, "Unexpected width for " + selector);
      assertTrue(picker.getMaxWidth() <= 170.0, "Unexpected max width for " + selector);
    }
  }

  @Test
  void checkoutAndReceiptFiltersShowSelectedDatesAtCompactWidths() {
    loginAsReceptionist();
    selectWorkspaceTab(4);
    DatePicker checkoutDate = lookup("#reception-checkout-date").queryAs(DatePicker.class);
    interact(() -> checkoutDate.setValue(LocalDate.of(2026, Month.SEPTEMBER, 6)));
    WaitForAsyncUtils.waitForFxEvents();
    layoutWorkspace();
    assertFalse(checkoutDate.getEditor().getText().isBlank());
    assertCompactDatePickerBounds(checkoutDate);
    assertTrue(
        lookup("#reception-checkout-ready-tab").queryAs(VBox.class).getPadding().getTop() >= 20);

    interact(
        () ->
            lookup("#reception-checkout-tabs")
                .queryAs(TabPane.class)
                .getSelectionModel()
                .select(1));
    DatePicker receiptDate = lookup("#reception-receipt-date").queryAs(DatePicker.class);
    interact(() -> receiptDate.setValue(LocalDate.of(2026, Month.SEPTEMBER, 6)));
    WaitForAsyncUtils.waitForFxEvents();
    layoutWorkspace();
    assertFalse(receiptDate.getEditor().getText().isBlank());
    assertCompactDatePickerBounds(receiptDate);
    assertTrue(lookup("#reception-receipts-tab").queryAs(VBox.class).getPadding().getTop() >= 20);
  }

  @Test
  void patientBookingSearchSupportsMouseSelection() throws SQLException {
    new PatientRepository(database)
        .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    loginAsReceptionist();
    selectWorkspaceTab(1);

    TextField patientSearch = textField("#reception-appointment-patient");
    interact(patientSearch::clear);
    interact(patientSearch::requestFocus);
    interact(() -> patientSearch.setText("Pat Lee"));
    WaitForAsyncUtils.waitForFxEvents();
    SearchSuggestionField<?> patientSuggestions =
        (SearchSuggestionField<?>) patientSearch.getParent();
    interact(
        () ->
            patientSearch.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.DOWN,
                    false,
                    false,
                    false,
                    false)));
    interact(
        () -> {
          ListView<?> suggestions = patientSuggestions.suggestionList();
          suggestions.getSelectionModel().selectFirst();
          suggestions.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED));
          suggestions.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED));
        });

    assertTrue(
        patientSearch.getText().contains("Pat Lee"), "Mouse selection did not update the editor");
  }

  @Test
  void calendarSlotOpensBookingPopupAndRefreshesAfterSave() throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    LocalDate selectedDate = LocalDate.now(java.time.ZoneId.of("Asia/Singapore"));
    var timeOff =
        services
            .appointmentService()
            .blockTimeOff(
                new Session(doctor.id(), doctor.username(), Role.DOCTOR),
                selectedDate.atTime(15, 0),
                selectedDate.atTime(16, 0));
    loginAsReceptionist();
    fire("#reception-nav-calendar");
    DatePicker from = lookup("#reception-calendar-from").queryAs(DatePicker.class);
    DatePicker to = lookup("#reception-calendar-to").queryAs(DatePicker.class);
    TextField calendarDoctor = textField("#reception-calendar-doctor");
    verifyThat("#reception-calendar-doctor-field", isVisible());
    clickOn(calendarDoctor);
    SearchSuggestionField<?> calendarDoctorSuggestions =
        (SearchSuggestionField<?>) calendarDoctor.getParent();
    interact(
        () ->
            calendarDoctor.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.DOWN,
                    false,
                    false,
                    false,
                    false)));
    assertFalse(calendarDoctorSuggestions.suggestionList().getItems().isEmpty());
    interact(
        () ->
            calendarDoctor.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.ENTER,
                    false,
                    false,
                    false,
                    false)));
    assertFalse(from.isShowWeekNumbers());
    assertFalse(to.isShowWeekNumbers());
    assertTrue(lookup("#reception-calendar-week-picker").tryQuery().isEmpty());
    interact(
        () -> {
          from.setValue(selectedDate);
          to.setValue(selectedDate.plusDays(2));
          to.getOnAction().handle(new javafx.event.ActionEvent());
        });
    WaitForAsyncUtils.waitForFxEvents();
    assertTrue(lookup("#doctor-calendar-day-column-" + selectedDate).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-day-column-" + selectedDate.plusDays(1)).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-day-column-" + selectedDate.plusDays(2)).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-day-column-" + selectedDate.plusDays(3)).tryQuery().isEmpty());
    var timeOffNode =
        lookup("#doctor-calendar-time-off-" + timeOff.id() + "-" + selectedDate).query();
    assertFalse(timeOffNode.isFocusTraversable());
    interact(() -> timeOffNode.getOnMouseClicked().handle(primaryClick()));
    assertTrue(lookup("#doctor-calendar-time-off-details").tryQuery().isEmpty());

    interact(
        () ->
            lookup("#doctor-calendar-events-" + selectedDate)
                .queryAs(Pane.class)
                .getOnMouseClicked()
                .handle(
                    new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        20,
                        20 * 100 + 4,
                        20,
                        20 * 100 + 4,
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
                        null)));
    waitForNode("#reception-calendar-appointment-dialog-content");
    verifyThat("#reception-calendar-appointment-dialog-scroll", isVisible());
    Stage dialog =
        (Stage)
            lookup("#reception-calendar-appointment-dialog-submit")
                .queryAs(Button.class)
                .getScene()
                .getWindow();
    assertEquals("Book appointment", dialog.getTitle());
    fire("#reception-calendar-appointment-dialog-submit");

    Appointment created =
        services.appointmentService().allAppointments(receptionistSession()).stream()
            .filter(appointment -> appointment.patientId() == patient.id())
            .findFirst()
            .orElseThrow();
    assertEquals(AppointmentStatus.PENDING, created.status());
    assertTrue(
        lookup("#doctor-calendar-appointment-" + created.id() + "-" + selectedDate)
            .tryQuery()
            .isPresent());
  }

  @Test
  void receptionistScheduleArrowsMoveByWholeWeeks() throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    LocalDate targetDate = LocalDate.now(CalendarService.CLINIC_ZONE).plusDays(5);
    services
        .appointmentService()
        .book(
            receptionistSession(),
            patient.id(),
            doctor.id(),
            targetDate.atTime(9, 0),
            targetDate.atTime(9, 30));

    loginAsReceptionist();
    fire("#reception-nav-calendar");
    selectCombo("#reception-calendar-view-mode", "Schedule");
    waitForNode("#reception-calendar-schedule-list");
    waitForNode("#doctor-calendar-schedule-date-" + targetDate);

    fire("#reception-calendar-next");
    WaitForAsyncUtils.waitForFxEvents();

    assertTrue(
        lookup("#doctor-calendar-schedule-date-" + targetDate).tryQuery().isEmpty(),
        "Receptionist Schedule should advance by seven days");
  }

  @Test
  void receptionistCanRescheduleADeclinedAppointmentFromTheDashboard() throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Singapore")).plusDays(1);
    Appointment appointment =
        services
            .appointmentService()
            .book(
                receptionistSession,
                patient.id(),
                doctor.id(),
                date.atTime(9, 0),
                date.atTime(9, 30));
    services.appointmentService().decline(doctorSession, appointment.id());

    loginAsReceptionist();
    selectWorkspaceTab(1);
    selectCombo("#reception-schedule-status", "Declined");
    assertEquals(1, appointmentList().getItems().size());
    selectFirstAppointment("#reception-appointment-list");
    fire("#reception-reschedule");
    waitForNode("#reception-reschedule-dialog-content");
    LocalDate replacementDate = date.plusDays(1);
    interact(
        () ->
            lookup("#reception-reschedule-dialog-date")
                .queryAs(DatePicker.class)
                .setValue(replacementDate));
    selectCombo("#reception-reschedule-dialog-start-hour", "10");
    selectCombo("#reception-reschedule-dialog-start-minute", "00");
    selectCombo("#reception-reschedule-dialog-end-hour", "10");
    selectCombo("#reception-reschedule-dialog-end-minute", "30");
    fire("#reception-reschedule-dialog-submit");

    assertEquals(
        AppointmentStatus.PENDING, services.appointmentService().get(appointment.id()).status());
    selectCombo("#reception-schedule-status", "All statuses");
    assertEquals(AppointmentStatus.PENDING, appointmentList().getItems().get(0).status());
  }

  @Test
  void revenueReportSupportsFiltersAndEmptyState() {
    loginAsReceptionist();
    selectWorkspaceTab(5);
    assertTrue(lookup("#reception-revenue-report-patient").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-report-doctor").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-report-method").tryQuery().isPresent());
    assertTrue(combo("#reception-revenue-report-method").getItems().contains("All methods"));
    assertEquals("All methods", combo("#reception-revenue-report-method").getValue());
    assertTrue(lookup("#reception-revenue-export-csv").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-export-json").tryQuery().isPresent());

    setDatePicker("#reception-revenue-report-from", LocalDate.of(2030, 1, 1));
    setDatePicker("#reception-revenue-report-to", LocalDate.of(2030, 1, 1));
    setText("#reception-revenue-report-patient", "does-not-exist");
    fire("#reception-revenue-report");
    assertTrue(textLabel("#reception-revenue-report-summary").startsWith("Successful payments: 0"));
    assertTrue(
        lookup("#reception-revenue-report-list").queryAs(TableView.class).getItems().isEmpty());
    assertTrue(
        lookup("#reception-revenue-report-list-empty")
            .query()
            .getStyleClass()
            .contains("table-empty-row"));
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
    assertTrue(csv.contains("summary,1,1,45.00"));
    assertTrue(csv.contains("paymentMethod,CARD,45.00"));
    assertTrue(csv.contains("doctor,Dr. Ada,45.00"));

    String json = ReceptionistView.reportJson(report);
    assertTrue(json.contains("\"successfulPaymentCount\":1"));
    assertTrue(json.contains("\"receiptCount\":1"));
    assertTrue(json.contains("\"paymentMethods\":{\"CARD\":\"45.00\"}"));
    assertTrue(json.contains("\"doctors\":{\"Dr. Ada\":\"45.00\"}"));
    assertTrue(json.contains("\"patientName\":\"Pat Lee\""));
    assertTrue(json.contains("\"method\":\"CARD\""));
  }

  @Test
  void revenueReportExportsEscapeDynamicText() {
    Receipt receipt =
        new Receipt(
            1,
            2,
            3,
            4,
            "Pat, \"Lee\"\nNorth",
            "Dr. \"Ada\"\\Clinic\tEast\u0001",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            7,
            LocalDateTime.of(2026, 9, 1, 12, 30));
    RevenueReport report = new RevenueReport(List.of(receipt));

    String csv = ReceptionistView.reportCsv(report);
    assertTrue(csv.contains("\"Pat, \"\"Lee\"\"\nNorth\""));
    assertTrue(csv.contains("\"Dr. \"\"Ada\"\"\\Clinic\tEast\u0001\""));
    assertTrue(csv.contains("doctor,\"Dr. \"\"Ada\"\"\\Clinic\tEast\u0001\",45.00"));

    String json = ReceptionistView.reportJson(report);
    assertTrue(json.contains("\"patientName\":\"Pat, \\\"Lee\\\"\\nNorth\""));
    assertTrue(json.contains("\"doctor\":\"Dr. \\\"Ada\\\"\\\\Clinic\\tEast\\u0001\""));
    assertTrue(
        json.contains("\"doctors\":{\"Dr. \\\"Ada\\\"\\\\Clinic\\tEast\\u0001\":\"45.00\"}"));
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
    verifyThat("#reception-patient-view", isVisible());

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
    fire("#reception-patient-view-" + activePatient.id());
    waitForNode("#reception-patient-view");

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
  private TableView<Appointment> appointmentList() {
    return lookup("#reception-appointment-list").queryAs(TableView.class);
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

  private void layoutWorkspace() {
    interact(
        () -> {
          Pane workspace = lookup("#receptionist-workspace").queryAs(Pane.class);
          workspace.applyCss();
          workspace.layout();
        });
  }

  private void assertCompactDatePickerBounds(DatePicker picker) {
    double width = picker.getBoundsInParent().getWidth();
    assertTrue(width >= 132.0 && width <= 180.0, "Unexpected rendered date-picker width: " + width);
  }

  @SuppressWarnings("unchecked")
  private void selectFirstAppointment(String selector) {
    interact(() -> lookup(selector).queryAs(TableView.class).getSelectionModel().selectFirst());
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

  private static MouseEvent primaryClick() {
    return mouseEvent(MouseEvent.MOUSE_CLICKED);
  }

  private static MouseEvent mouseEvent(EventType<MouseEvent> eventType) {
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
