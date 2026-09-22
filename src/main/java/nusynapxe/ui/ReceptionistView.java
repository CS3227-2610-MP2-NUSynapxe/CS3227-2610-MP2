package nusynapxe.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueReport;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the Receptionist scheduling, patient, checkout, and revenue workspace. */
public final class ReceptionistView {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_LABEL = "Date";
  private static final String DOCTOR_LABEL = "Doctor";
  private static final String QUEUE_WAITING = "Waiting";
  private static final String QUEUE_CHECKED_IN = "Checked in";
  private static final String QUEUE_ALL = "All";
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String ALL_METHODS = "All methods";
  private static final String ALL_STATUSES = "All statuses";
  private static final String PATIENT_NAME_ID = "Name, NRIC/FIN, phone, or email";
  private static final String PATIENT_LABEL = "Patient";
  private static final String SUBTAB_CONTENT_STYLE = "subtab-content";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final int JSON_CONTROL_CHARACTER_LIMIT = 0x20;
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

  private ReceptionistView() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the Receptionist workspace.
   *
   * @param services application services used by the workspace
   * @param session authenticated Receptionist session
   * @param onLogout callback invoked when the Receptionist logs out
   * @return root node for the Receptionist workspace
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    return create(services, session, onLogout, ClinicClock.system());
  }

  static Parent create(ClinicServices services, Session session, Runnable onLogout, Clock clock) {
    Clock clinicClock = ClinicClock.withClinicZone(clock);
    SearchSuggestionField<Account> doctor =
        doctorSelector("reception-doctor", "Search doctors by name or username");
    AppointmentDialog.TimeFields startsAt = AppointmentDialog.timeSelector("reception-start");
    AppointmentDialog.TimeFields endsAt = AppointmentDialog.timeSelector("reception-end");
    Button book = button("Book appointment", "reception-book");
    Button reschedule = button("Reschedule selected", "reception-reschedule");
    Button cancel = button("Cancel selected", "reception-cancel");
    TableView<AppointmentListRow> appointmentList = appointmentTable("reception-appointment-list");
    DatePicker scheduleDate = UiComponents.compactDatePicker();
    scheduleDate.setId("reception-schedule-date");
    scheduleDate.setPromptText("Any date");
    SearchSuggestionField<Account> scheduleDoctor =
        doctorSelector("reception-schedule-doctor", ALL_DOCTORS);
    ComboBox<String> scheduleStatus = UiComponents.compactSelector();
    scheduleStatus.getItems().add(ALL_STATUSES);
    for (AppointmentStatus status : AppointmentStatus.values()) {
      scheduleStatus.getItems().add(displayStatus(status));
    }
    scheduleStatus.setId("reception-schedule-status");
    scheduleStatus.getSelectionModel().select(ALL_STATUSES);
    TextField schedulePatient = field("reception-schedule-patient", "Patient name or ID");
    Label scheduleSummary = new Label();
    scheduleSummary.setId("reception-schedule-summary");
    TableView<AppointmentListRow> queueList = appointmentTable("reception-check-in-queue-list");
    DatePicker queueDate = UiComponents.compactDatePicker(LocalDate.now(clinicClock));
    queueDate.setId("reception-check-in-queue-date");
    SearchSuggestionField<Account> queueDoctor =
        doctorSelector("reception-check-in-queue-doctor", ALL_DOCTORS);
    TextField queuePatient = field("reception-check-in-queue-patient", PATIENT_NAME_ID);
    ComboBox<String> queueStatus = UiComponents.compactSelector();
    queueStatus.setItems(
        FXCollections.observableArrayList(QUEUE_WAITING, QUEUE_CHECKED_IN, QUEUE_ALL));
    queueStatus.setId("reception-check-in-queue-status");
    queueStatus.getSelectionModel().select(QUEUE_ALL);
    Label queueSummary = new Label();
    queueSummary.setId("reception-check-in-queue-summary");
    Button queueSearch = button("Search", "reception-check-in-queue-search");
    DatePicker appointmentDate = UiComponents.compactDatePicker(LocalDate.now(clinicClock));
    appointmentDate.setId("reception-appointment-date");
    SearchSuggestionField<Patient> appointmentPatient =
        PatientDirectoryView.patientSearchField("reception-appointment-patient");
    Button checkIn = button("Check in selected", "reception-check-in");
    TableView<AppointmentListRow> checkoutAppointmentList =
        appointmentTable("reception-checkout-appointment-list");
    boolean[] checkoutMouseSelection = {false};
    boolean[] checkoutTabActive = {false};
    TextField checkoutPatient = field("reception-checkout-patient", PATIENT_NAME_ID);
    DatePicker checkoutDate = UiComponents.compactDatePicker();
    checkoutDate.setId("reception-checkout-date");
    SearchSuggestionField<Account> checkoutDoctor =
        doctorSelector("reception-checkout-doctor", ALL_DOCTORS);
    Button checkoutSearch = button("Search checkout", "reception-checkout-search");
    TextField receiptPatient = field("reception-receipt-patient", PATIENT_NAME_ID);
    DatePicker receiptDate = UiComponents.compactDatePicker();
    receiptDate.setId("reception-receipt-date");
    SearchSuggestionField<Account> receiptDoctor =
        doctorSelector("reception-receipt-doctor", ALL_DOCTORS);
    TableView<Receipt> receiptHistoryList = receiptTable("reception-receipt-history-list");
    Button receiptSearch = button("Search receipts", "reception-receipt-search");
    Label receiptPreview = new Label();
    receiptPreview.setId("reception-receipt-preview");
    DatePicker reportFromDate = UiComponents.compactDatePicker(LocalDate.now(clinicClock));
    reportFromDate.setId("reception-revenue-report-from");
    DatePicker reportToDate = UiComponents.compactDatePicker(LocalDate.now(clinicClock));
    reportToDate.setId("reception-revenue-report-to");
    TextField reportPatient = field("reception-revenue-report-patient", "Patient name or ID");
    SearchSuggestionField<Account> reportDoctor =
        doctorSelector("reception-revenue-report-doctor", ALL_DOCTORS);
    ComboBox<String> reportMethod = UiComponents.compactSelector();
    reportMethod.getItems().add(ALL_METHODS);
    for (PaymentMethod method : PaymentMethod.values()) {
      reportMethod.getItems().add(displayStatus(method));
    }
    reportMethod.setId("reception-revenue-report-method");
    reportMethod.getSelectionModel().select(ALL_METHODS);
    Button reportButton = button("Generate report", "reception-revenue-report");
    Label reportSummary = new Label();
    reportSummary.setId("reception-revenue-report-summary");
    TableView<Receipt> reportRows = receiptTable("reception-revenue-report-list");
    RevenueReport[] currentReport = {new RevenueReport(List.of())};
    Button exportCsv = button("Export CSV", "reception-revenue-export-csv");
    Button exportJson = button("Export JSON", "reception-revenue-export-json");
    TextField legacyRevenueDate = field("reception-revenue-date", "yyyy-MM-dd");
    Button legacyRevenueButton = button("Show revenue", "reception-revenue-submit");
    Label legacyRevenue = new Label();
    legacyRevenue.setId("reception-revenue");
    Label feedback = UiComponents.feedback("reception-feedback");
    SelectionState selection = new SelectionState();
    PatientDirectoryView patientDirectory =
        PatientDirectoryView.create(
            services,
            session,
            "reception",
            feedback,
            patientId ->
                PatientDirectoryView.refreshAppointmentPatients(
                    services, session, appointmentPatient, feedback, patientId),
            null,
            clinicClock);
    appointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              Appointment appointment = selected == null ? null : selected.appointment();
              selection.appointmentId = appointment == null ? 0 : appointment.id();
              boolean editable =
                  appointment != null
                      && (appointment.status() == AppointmentStatus.PENDING
                          || appointment.status() == AppointmentStatus.ACCEPTED
                          || appointment.status() == AppointmentStatus.DECLINED);
              reschedule.setDisable(!editable);
              cancel.setDisable(!editable);
              checkIn.setDisable(
                  appointment == null || appointment.status() != AppointmentStatus.ACCEPTED);
            });
    queueList.setOnMouseClicked(
        event -> {
          AppointmentListRow selectedRow = queueList.getSelectionModel().getSelectedItem();
          Appointment selected = selectedRow == null ? null : selectedRow.appointment();
          if (selected != null) {
            showCheckInDetailsDialog(
                services,
                session,
                selected.id(),
                feedback,
                clinicClock,
                () ->
                    refreshQueue(
                        services,
                        session,
                        queueList,
                        feedback,
                        queueDate.getValue(),
                        queueDoctor.getValue() == null ? null : queueDoctor.getValue().id(),
                        queuePatient.getText(),
                        queueStatus.getValue(),
                        queueSummary));
          }
        });
    queueDate
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshQueue(
                    services,
                    session,
                    queueList,
                    feedback,
                    queueDate.getValue(),
                    queueDoctor.getValue() == null ? null : queueDoctor.getValue().id(),
                    queuePatient.getText(),
                    queueStatus.getValue(),
                    queueSummary));
    queueDoctor
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshQueue(
                    services,
                    session,
                    queueList,
                    feedback,
                    queueDate.getValue(),
                    selected == null ? null : selected.id(),
                    queuePatient.getText(),
                    queueStatus.getValue(),
                    queueSummary));
    queueStatus
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshQueue(
                    services,
                    session,
                    queueList,
                    feedback,
                    queueDate.getValue(),
                    queueDoctor.getValue() == null ? null : queueDoctor.getValue().id(),
                    queuePatient.getText(),
                    selected,
                    queueSummary));
    queuePatient
        .textProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshQueue(
                    services,
                    session,
                    queueList,
                    feedback,
                    queueDate.getValue(),
                    queueDoctor.getValue() == null ? null : queueDoctor.getValue().id(),
                    selected,
                    queueStatus.getValue(),
                    queueSummary));
    queueSearch.setOnAction(
        event ->
            refreshQueue(
                services,
                session,
                queueList,
                feedback,
                queueDate.getValue(),
                queueDoctor.getValue() == null ? null : queueDoctor.getValue().id(),
                queuePatient.getText(),
                queueStatus.getValue(),
                queueSummary));
    scheduleDate
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshSchedule(
                    services,
                    session,
                    appointmentList,
                    selection,
                    feedback,
                    scheduleDate.getValue(),
                    scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                    schedulePatient.getText(),
                    scheduleStatus.getValue(),
                    scheduleSummary));
    scheduleDoctor
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshSchedule(
                    services,
                    session,
                    appointmentList,
                    selection,
                    feedback,
                    scheduleDate.getValue(),
                    selected == null ? null : selected.id(),
                    schedulePatient.getText(),
                    scheduleStatus.getValue(),
                    scheduleSummary));
    scheduleStatus
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshSchedule(
                    services,
                    session,
                    appointmentList,
                    selection,
                    feedback,
                    scheduleDate.getValue(),
                    scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                    schedulePatient.getText(),
                    selected,
                    scheduleSummary));
    schedulePatient
        .textProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshSchedule(
                    services,
                    session,
                    appointmentList,
                    selection,
                    feedback,
                    scheduleDate.getValue(),
                    scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                    selected,
                    scheduleStatus.getValue(),
                    scheduleSummary));
    checkoutAppointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              Appointment appointment = selected == null ? null : selected.appointment();
              selection.appointmentId = appointment == null ? 0 : appointment.id();
              if (appointment != null && checkoutTabActive[0] && !checkoutMouseSelection[0]) {
                showCheckoutDetailsDialog(
                    services,
                    session,
                    appointment.id(),
                    feedback,
                    receiptPreview,
                    () -> {
                      refreshCheckoutReady(
                          services,
                          session,
                          checkoutAppointmentList,
                          feedback,
                          checkoutPatient.getText(),
                          checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                          checkoutDate.getValue());
                      refreshReceiptHistory(
                          services,
                          session,
                          receiptHistoryList,
                          receiptPreview,
                          receiptPatient.getText(),
                          receiptDoctor.getValue() == null ? null : receiptDoctor.getValue().id(),
                          receiptDate.getValue(),
                          feedback);
                    });
              }
            });
    checkoutAppointmentList.setOnMousePressed(event -> checkoutMouseSelection[0] = true);
    checkoutAppointmentList.setOnMouseClicked(
        event -> {
          AppointmentListRow selectedRow =
              checkoutAppointmentList.getSelectionModel().getSelectedItem();
          Appointment selected = selectedRow == null ? null : selectedRow.appointment();
          if (selected != null) {
            showCheckoutDetailsDialog(
                services,
                session,
                selected.id(),
                feedback,
                receiptPreview,
                () -> {
                  refreshCheckoutReady(
                      services,
                      session,
                      checkoutAppointmentList,
                      feedback,
                      checkoutPatient.getText(),
                      checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                      checkoutDate.getValue());
                  refreshReceiptHistory(
                      services,
                      session,
                      receiptHistoryList,
                      receiptPreview,
                      receiptPatient.getText(),
                      receiptDoctor.getValue() == null ? null : receiptDoctor.getValue().id(),
                      receiptDate.getValue(),
                      feedback);
                });
          }
          checkoutMouseSelection[0] = false;
        });
    receiptHistoryList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected != null) {
                receiptPreview.setText(formatReceipt(selected));
              }
            });
    receiptSearch.setOnAction(
        event ->
            refreshReceiptHistory(
                services,
                session,
                receiptHistoryList,
                receiptPreview,
                receiptPatient.getText(),
                receiptDoctor.getValue() == null ? null : receiptDoctor.getValue().id(),
                receiptDate.getValue(),
                feedback));
    checkoutSearch.setOnAction(
        event ->
            refreshCheckoutReady(
                services,
                session,
                checkoutAppointmentList,
                feedback,
                checkoutPatient.getText(),
                checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                checkoutDate.getValue()));
    checkoutPatient
        .textProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshReceiptHistory(
                    services,
                    session,
                    receiptHistoryList,
                    receiptPreview,
                    selected,
                    checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                    checkoutDate.getValue(),
                    feedback));
    checkoutDoctor
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshReceiptHistory(
                    services,
                    session,
                    receiptHistoryList,
                    receiptPreview,
                    checkoutPatient.getText(),
                    selected == null ? null : selected.id(),
                    checkoutDate.getValue(),
                    feedback));
    checkoutDate
        .valueProperty()
        .addListener(
            (observable, previous, selected) ->
                refreshReceiptHistory(
                    services,
                    session,
                    receiptHistoryList,
                    receiptPreview,
                    checkoutPatient.getText(),
                    checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                    selected,
                    feedback));

    book.setOnAction(
        event -> {
          try {
            if (appointmentPatient.getValue() == null || doctor.getValue() == null) {
              throw new ValidationException("Select a patient and Doctor first");
            }
            Appointment appointment =
                services
                    .appointmentService()
                    .book(
                        session,
                        appointmentPatient.getValue().id(),
                        doctor.getValue().id(),
                        parseScheduleDateTime(appointmentDate, startsAt, "Start time"),
                        parseScheduleDateTime(appointmentDate, endsAt, "End time"));
            selection.appointmentId = appointment.id();
            UiComponents.showMessage(feedback, "Appointment booked and awaiting Doctor acceptance");
            refreshSchedule(
                services,
                session,
                appointmentList,
                selection,
                feedback,
                scheduleDate.getValue(),
                scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                schedulePatient.getText(),
                scheduleStatus.getValue(),
                scheduleSummary);
            refreshCheckoutReady(
                services,
                session,
                checkoutAppointmentList,
                feedback,
                checkoutPatient.getText(),
                checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                checkoutDate.getValue());
          } catch (ValidationException
              | AuthorizationException
              | IllegalArgumentException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          } catch (SQLException exception) {
            UiComponents.showError(feedback, "Appointment booking is temporarily unavailable");
          }
        });

    reschedule.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            showRescheduleDialog(
                services,
                session,
                selection.appointmentId,
                feedback,
                () ->
                    refreshSchedule(
                        services,
                        session,
                        appointmentList,
                        selection,
                        feedback,
                        scheduleDate.getValue(),
                        scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                        schedulePatient.getText(),
                        scheduleStatus.getValue(),
                        scheduleSummary));
          } catch (ValidationException | AuthorizationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });

    cancel.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services.appointmentService().cancel(session, selection.appointmentId);
            UiComponents.showMessage(feedback, "Appointment cancelled");
            refreshSchedule(
                services,
                session,
                appointmentList,
                selection,
                feedback,
                scheduleDate.getValue(),
                scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                schedulePatient.getText(),
                scheduleStatus.getValue(),
                scheduleSummary);
            refreshCheckoutReady(
                services,
                session,
                checkoutAppointmentList,
                feedback,
                checkoutPatient.getText(),
                checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                checkoutDate.getValue());
          } catch (ValidationException | AuthorizationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          } catch (SQLException exception) {
            UiComponents.showError(feedback, "Appointment cancellation is temporarily unavailable");
          }
        });

    checkIn.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services.appointmentService().checkIn(session, selection.appointmentId);
            UiComponents.showMessage(feedback, "Patient checked in");
            refreshSchedule(
                services,
                session,
                appointmentList,
                selection,
                feedback,
                scheduleDate.getValue(),
                scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                schedulePatient.getText(),
                scheduleStatus.getValue(),
                scheduleSummary);
            refreshCheckoutReady(
                services,
                session,
                checkoutAppointmentList,
                feedback,
                checkoutPatient.getText(),
                checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                checkoutDate.getValue());
          } catch (ValidationException | AuthorizationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          } catch (SQLException exception) {
            UiComponents.showError(feedback, "Check-in is temporarily unavailable");
          }
        });

    reportButton.setOnAction(
        event -> {
          try {
            LocalDate from = reportFromDate.getValue();
            LocalDate to = reportToDate.getValue();
            RevenueReport report =
                services
                    .billingService()
                    .revenueReport(
                        session,
                        from,
                        to,
                        reportPatient.getText(),
                        reportDoctor.getValue() == null ? null : reportDoctor.getValue().id(),
                        selectedPaymentMethod(reportMethod.getValue()));
            String summary = formatRevenueSummary(report);
            currentReport[0] = report;
            reportRows.setItems(FXCollections.observableArrayList(report.receipts()));
            reportSummary.setText(summary);
            UiComponents.showMessage(feedback, "Revenue report generated");
          } catch (ValidationException | AuthorizationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          } catch (ArithmeticException exception) {
            UiComponents.showError(feedback, "Revenue total exceeds the supported range");
          } catch (SQLException exception) {
            UiComponents.showError(feedback, "Revenue report is temporarily unavailable");
          }
        });
    legacyRevenueButton.setOnAction(
        event -> {
          try {
            RevenueSummary summary =
                services
                    .billingService()
                    .dailyRevenue(session, LocalDate.parse(legacyRevenueDate.getText()));
            legacyRevenue.setText(
                summary.transactionCount()
                    + " successful payment(s), total "
                    + formatMinor(summary.totalMinor()));
          } catch (ValidationException | AuthorizationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          } catch (SQLException | DateTimeParseException exception) {
            UiComponents.showError(feedback, "Revenue is temporarily unavailable");
          }
        });

    exportCsv.setOnAction(
        event ->
            exportReport(currentReport[0], reportRows.getScene().getWindow(), false, feedback));
    exportJson.setOnAction(
        event -> exportReport(currentReport[0], reportRows.getScene().getWindow(), true, feedback));

    Button logout = button("Log out", "logout-button");
    logout.setOnAction(event -> onLogout.run());
    HBox header =
        UiComponents.workspaceHeader("RECEPTIONIST workspace", session.username(), logout);

    Parent patientDirectoryPage = patientDirectory.view();
    VBox patientContent = new VBox(12, patientDirectoryPage);
    VBox.setVgrow(patientDirectoryPage, Priority.ALWAYS);
    GridPane appointmentForm = new GridPane();
    appointmentForm.getStyleClass().add("appointment-form-grid");
    appointmentForm.setHgap(16);
    appointmentForm.setVgap(14);
    addUniformColumns(appointmentForm, 3, 180, 240, 240);
    appointmentForm.add(UiComponents.fieldGroup(PATIENT_LABEL, appointmentPatient), 0, 0, 3, 1);
    appointmentForm.add(UiComponents.fieldGroup(DOCTOR_LABEL, doctor), 0, 1, 3, 1);
    VBox appointmentDateField = UiComponents.fieldGroup(DATE_LABEL, appointmentDate);
    VBox appointmentStartField = UiComponents.fieldGroup("Starts", startsAt.view());
    VBox appointmentEndField = UiComponents.fieldGroup("Ends", endsAt.view());
    appointmentDateField.getStyleClass().add("appointment-interval-field");
    appointmentStartField.getStyleClass().add("appointment-interval-field");
    appointmentEndField.getStyleClass().add("appointment-interval-field");
    appointmentForm.add(appointmentDateField, 0, 2);
    appointmentForm.add(appointmentStartField, 1, 2);
    appointmentForm.add(appointmentEndField, 2, 2);
    appointmentForm.add(book, 2, 3);
    GridPane scheduleFilters = new GridPane();
    scheduleFilters.getStyleClass().add("uniform-filter-grid");
    scheduleFilters.setHgap(12);
    scheduleFilters.setVgap(10);
    addUniformColumns(scheduleFilters, 4, 170, 220, 220);
    scheduleFilters.add(UiComponents.fieldGroup(DATE_LABEL, scheduleDate), 0, 0);
    scheduleFilters.add(UiComponents.fieldGroup(DOCTOR_LABEL, scheduleDoctor), 1, 0);
    scheduleFilters.add(UiComponents.fieldGroup("Status", scheduleStatus), 2, 0);
    scheduleFilters.add(UiComponents.fieldGroup(PATIENT_LABEL, schedulePatient), 3, 0);
    VBox bookingContent =
        UiComponents.card(
            "reception-booking-card",
            UiComponents.sectionHeading("Book appointment"),
            appointmentForm);
    VBox appointmentManageContent =
        UiComponents.card(
            "reception-appointment-results-card",
            scheduleFilters,
            scheduleSummary,
            appointmentList,
            UiComponents.actionBar(reschedule, cancel));
    VBox bookingTabContent = new VBox(bookingContent);
    bookingTabContent.getStyleClass().add(SUBTAB_CONTENT_STYLE);
    bookingTabContent.setPadding(new Insets(22, 0, 0, 0));
    VBox manageTabContent = new VBox(appointmentManageContent);
    manageTabContent.getStyleClass().add(SUBTAB_CONTENT_STYLE);
    manageTabContent.setPadding(new Insets(22, 0, 0, 0));
    Tab bookingTab = new Tab("Book appointment", bookingTabContent);
    bookingTab.setClosable(false);
    Tab manageAppointmentsTab = new Tab("Search and manage appointments", manageTabContent);
    manageAppointmentsTab.setClosable(false);
    TabPane appointmentTabs = new TabPane(bookingTab, manageAppointmentsTab);
    appointmentTabs.setId("reception-appointment-tabs");
    appointmentTabs.getStyleClass().add("sub-navigation");
    VBox appointmentContent =
        new VBox(
            12,
            UiComponents.pageTitle("Appointments"),
            UiComponents.supportingText("Book, search, reschedule, and cancel appointments."),
            appointmentTabs);
    GridPane queueFilters = filterGrid("reception-check-in-filter-grid");
    addUniformColumns(queueFilters, 4, 170, 220, 220);
    queueFilters.add(UiComponents.fieldGroup(DATE_LABEL, queueDate), 0, 0);
    queueFilters.add(UiComponents.fieldGroup(DOCTOR_LABEL, queueDoctor), 1, 0);
    queueFilters.add(UiComponents.fieldGroup("Status", queueStatus), 2, 0);
    queueFilters.add(UiComponents.fieldGroup(PATIENT_LABEL, queuePatient), 3, 0);
    queueFilters.add(queueSearch, 4, 0);
    alignFilterAction(queueSearch);
    VBox queueContent =
        new VBox(
            12,
            UiComponents.pageTitle("Check in"),
            UiComponents.supportingText(
                "Find arriving patients and review appointments already checked in."),
            UiComponents.card(
                "reception-check-in-card",
                UiComponents.sectionHeading("Check-in appointments"),
                queueFilters,
                queueSummary,
                queueList));
    GridPane checkoutFilters = filterGrid("reception-checkout-filter-grid");
    checkoutFilters.getStyleClass().add("spacious-filter-grid");
    addUniformColumns(checkoutFilters, 3, 180, 240, 240);
    checkoutFilters.add(UiComponents.fieldGroup(PATIENT_LABEL, checkoutPatient), 0, 0);
    checkoutFilters.add(UiComponents.fieldGroup(DOCTOR_LABEL, checkoutDoctor), 1, 0);
    checkoutFilters.add(UiComponents.fieldGroup(DATE_LABEL, checkoutDate), 2, 0);
    checkoutFilters.add(checkoutSearch, 3, 0);
    alignFilterAction(checkoutSearch);
    GridPane receiptFilters = filterGrid("reception-receipt-filter-grid");
    receiptFilters.getStyleClass().add("spacious-filter-grid");
    addUniformColumns(receiptFilters, 3, 180, 240, 240);
    receiptFilters.add(UiComponents.fieldGroup(PATIENT_LABEL, receiptPatient), 0, 0);
    receiptFilters.add(UiComponents.fieldGroup(DOCTOR_LABEL, receiptDoctor), 1, 0);
    receiptFilters.add(UiComponents.fieldGroup(DATE_LABEL, receiptDate), 2, 0);
    receiptFilters.add(receiptSearch, 3, 0);
    alignFilterAction(receiptSearch);
    VBox readyForCheckoutCard =
        UiComponents.card(
            "reception-checkout-ready-card",
            UiComponents.sectionHeading("Checkout appointments"),
            checkoutFilters,
            checkoutAppointmentList);
    VBox readyForCheckoutContent = new VBox(readyForCheckoutCard);
    readyForCheckoutContent.getStyleClass().add(SUBTAB_CONTENT_STYLE);
    readyForCheckoutContent.setPadding(new Insets(22, 0, 0, 0));
    readyForCheckoutContent.setId("reception-checkout-ready-tab");
    VBox receiptHistoryCard =
        UiComponents.card(
            "reception-receipts-card",
            UiComponents.sectionHeading("Issued receipts"),
            receiptFilters,
            receiptHistoryList,
            receiptPreview);
    VBox receiptHistoryContent = new VBox(receiptHistoryCard);
    receiptHistoryContent.getStyleClass().add(SUBTAB_CONTENT_STYLE);
    receiptHistoryContent.setPadding(new Insets(22, 0, 0, 0));
    receiptHistoryContent.setId("reception-receipts-tab");
    Tab readyForCheckoutTab = new Tab("Checkout", readyForCheckoutContent);
    readyForCheckoutTab.setClosable(false);
    Tab receiptHistoryTab = new Tab("Receipts", receiptHistoryContent);
    receiptHistoryTab.setClosable(false);
    TabPane checkoutTabs = new TabPane(readyForCheckoutTab, receiptHistoryTab);
    checkoutTabs.setId("reception-checkout-tabs");
    checkoutTabs.getStyleClass().add("sub-navigation");
    VBox checkoutContent =
        new VBox(
            12,
            UiComponents.pageTitle("Checkout and receipts"),
            UiComponents.supportingText(
                "Complete payments for finished visits or review previously issued receipts."),
            checkoutTabs);
    FlowPane reportDates = new FlowPane();
    reportDates.setId("reception-revenue-filter-grid");
    reportDates.getStyleClass().add("uniform-filter-grid");
    reportDates.getStyleClass().add("single-line-filter-grid");
    reportDates.setHgap(12);
    reportDates.setVgap(10);
    reportDates.setRowValignment(VPos.BOTTOM);
    reportDates
        .getChildren()
        .addAll(
            UiComponents.fieldGroup("From", reportFromDate),
            UiComponents.fieldGroup("To", reportToDate),
            UiComponents.fieldGroup(PATIENT_LABEL, reportPatient),
            UiComponents.fieldGroup(DOCTOR_LABEL, reportDoctor),
            UiComponents.fieldGroup("Payment method", reportMethod),
            reportButton);
    alignFilterAction(reportButton);
    VBox revenueCard =
        UiComponents.card(
            "reception-revenue-card",
            UiComponents.sectionHeading("Revenue results"),
            reportDates,
            new HBox(8, exportCsv, exportJson),
            reportSummary,
            reportRows);
    VBox revenueContent =
        new VBox(
            12,
            UiComponents.pageTitle("Revenue Reports"),
            UiComponents.supportingText(
                "Filter successful payments and export the resulting report."),
            revenueCard);
    VBox legacyRevenueCompatibility =
        new VBox(legacyRevenueDate, legacyRevenueButton, legacyRevenue);
    legacyRevenueCompatibility.setOpacity(0);
    legacyRevenueCompatibility.setManaged(false);
    revenueContent.getChildren().add(legacyRevenueCompatibility);
    Tab patientFeature = featureTab("Directory", patientContent);
    Tab appointmentFeature = featureTab("Appointments", appointmentContent);
    ReceptionistCalendarView[] calendarHolder = new ReceptionistCalendarView[1];
    calendarHolder[0] =
        new ReceptionistCalendarView(
            services,
            session,
            feedback,
            (selectedDoctor, start) ->
                AppointmentDialog.showReceptionistCreate(
                    services,
                    session,
                    selectedDoctor.id(),
                    start,
                    feedback,
                    () -> {
                      calendarHolder[0].refresh();
                      refreshSchedule(
                          services,
                          session,
                          appointmentList,
                          selection,
                          feedback,
                          scheduleDate.getValue(),
                          scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                          schedulePatient.getText(),
                          scheduleStatus.getValue(),
                          scheduleSummary);
                    }),
            selectedAppointment ->
                AppointmentDialog.showReceptionistEdit(
                    services,
                    session,
                    selectedAppointment.appointmentId(),
                    feedback,
                    () -> {
                      calendarHolder[0].refresh();
                      refreshSchedule(
                          services,
                          session,
                          appointmentList,
                          selection,
                          feedback,
                          scheduleDate.getValue(),
                          scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
                          schedulePatient.getText(),
                          scheduleStatus.getValue(),
                          scheduleSummary);
                    }));
    ReceptionistCalendarView receptionistCalendar = calendarHolder[0];
    Tab calendarFeature = new Tab("Calendar", receptionistCalendar.view());
    calendarFeature.setClosable(false);
    Tab queueFeature = featureTab("Check in", queueContent);
    Tab checkoutFeature = featureTab("Checkout", checkoutContent);
    Tab revenueFeature = featureTab("Revenue Reports", revenueContent);
    TabPane workspaceTabs =
        new TabPane(
            patientFeature,
            appointmentFeature,
            calendarFeature,
            queueFeature,
            checkoutFeature,
            revenueFeature);
    workspaceTabs.setId("reception-workspace-tabs");
    workspaceTabs.getStyleClass().add("hidden-tab-headers");
    Button directoryNavigation = navigationButton("Directory", "reception-nav-directory");
    Button appointmentsNavigation = navigationButton("Appointments", "reception-nav-appointments");
    Button calendarNavigation = navigationButton("Calendar", "reception-nav-calendar");
    Button checkInNavigation = navigationButton("Check in", "reception-nav-check-in");
    Button checkoutNavigation = navigationButton("Checkout", "reception-nav-checkout");
    Button revenueNavigation = navigationButton("Revenue Reports", "reception-nav-revenue-reports");
    List<Button> navigationButtons =
        List.of(
            directoryNavigation,
            appointmentsNavigation,
            calendarNavigation,
            checkInNavigation,
            checkoutNavigation,
            revenueNavigation);
    directoryNavigation.getStyleClass().add("active-navigation");
    Label navigationTitle = new Label("Navigation");
    navigationTitle.setId("reception-navigation-title");
    navigationTitle.getStyleClass().add("navigation-title");
    navigationTitle.setMaxWidth(Double.MAX_VALUE);
    VBox navigation =
        new VBox(
            0,
            navigationTitle,
            directoryNavigation,
            appointmentsNavigation,
            calendarNavigation,
            checkInNavigation,
            checkoutNavigation,
            revenueNavigation);
    navigation.setId("reception-navigation");
    directoryNavigation.setOnAction(
        event -> workspaceTabs.getSelectionModel().select(patientFeature));
    appointmentsNavigation.setOnAction(
        event -> workspaceTabs.getSelectionModel().select(appointmentFeature));
    calendarNavigation.setOnAction(
        event -> workspaceTabs.getSelectionModel().select(calendarFeature));
    checkInNavigation.setOnAction(event -> workspaceTabs.getSelectionModel().select(queueFeature));
    checkoutNavigation.setOnAction(
        event -> workspaceTabs.getSelectionModel().select(checkoutFeature));
    revenueNavigation.setOnAction(
        event -> workspaceTabs.getSelectionModel().select(revenueFeature));
    workspaceTabs
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              for (Button navigationButton : navigationButtons) {
                navigationButton.getStyleClass().remove("active-navigation");
              }
              int selectedIndex = workspaceTabs.getSelectionModel().getSelectedIndex();
              if (selectedIndex >= 0 && selectedIndex < navigationButtons.size()) {
                navigationButtons.get(selectedIndex).getStyleClass().add("active-navigation");
              }
              checkoutTabActive[0] = selected == checkoutFeature;
              if (selected == patientFeature) {
                patientDirectory.refresh();
              } else if (selected == appointmentFeature) {
                refreshDoctors(services, session, doctor, feedback);
                PatientDirectoryView.refreshAppointmentPatients(
                    services,
                    session,
                    appointmentPatient,
                    feedback,
                    patientDirectory.selectedPatientId());
                refreshDoctors(services, session, scheduleDoctor, feedback);
                scheduleDoctor.clearSelection();
                refreshSchedule(
                    services,
                    session,
                    appointmentList,
                    selection,
                    feedback,
                    scheduleDate.getValue(),
                    null,
                    schedulePatient.getText(),
                    scheduleStatus.getValue(),
                    scheduleSummary);
              } else if (selected == calendarFeature) {
                receptionistCalendar.refreshDoctors();
                receptionistCalendar.refresh();
              } else if (selected == queueFeature) {
                refreshDoctors(services, session, queueDoctor, feedback);
                queueDoctor.clearSelection();
                refreshQueue(
                    services,
                    session,
                    queueList,
                    feedback,
                    queueDate.getValue(),
                    null,
                    queuePatient.getText(),
                    queueStatus.getValue(),
                    queueSummary);
              } else if (selected == checkoutFeature) {
                refreshDoctors(services, session, checkoutDoctor, feedback);
                checkoutDoctor.clearSelection();
                refreshDoctors(services, session, receiptDoctor, feedback);
                receiptDoctor.clearSelection();
                refreshCheckoutReady(
                    services,
                    session,
                    checkoutAppointmentList,
                    feedback,
                    checkoutPatient.getText(),
                    checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
                    checkoutDate.getValue());
                refreshReceiptHistory(
                    services,
                    session,
                    receiptHistoryList,
                    receiptPreview,
                    receiptPatient.getText(),
                    receiptDoctor.getValue() == null ? null : receiptDoctor.getValue().id(),
                    receiptDate.getValue(),
                    feedback);
              } else if (selected == revenueFeature) {
                refreshDoctors(services, session, reportDoctor, feedback);
                reportDoctor.clearSelection();
              }
            });
    BorderPane root = new BorderPane(workspaceTabs);
    root.setId("receptionist-workspace");
    root.getStyleClass().add("workspace-shell");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setLeft(navigation);
    BorderPane.setMargin(navigation, new Insets(0, 16, 0, 0));
    refreshDoctors(services, session, doctor, feedback);
    refreshDoctors(services, session, reportDoctor, feedback);
    patientDirectory.refresh();
    PatientDirectoryView.refreshAppointmentPatients(
        services, session, appointmentPatient, feedback, 0);
    refreshSchedule(
        services,
        session,
        appointmentList,
        selection,
        feedback,
        null,
        null,
        "",
        null,
        scheduleSummary);
    refreshDoctors(services, session, queueDoctor, feedback);
    queueDoctor.clearSelection();
    refreshQueue(
        services,
        session,
        queueList,
        feedback,
        queueDate.getValue(),
        null,
        queuePatient.getText(),
        queueStatus.getValue(),
        queueSummary);
    refreshDoctors(services, session, checkoutDoctor, feedback);
    checkoutDoctor.clearSelection();
    refreshDoctors(services, session, receiptDoctor, feedback);
    receiptDoctor.clearSelection();
    refreshCheckoutReady(
        services,
        session,
        checkoutAppointmentList,
        feedback,
        checkoutPatient.getText(),
        checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
        checkoutDate.getValue());
    return UiComponents.notificationOverlay(root, feedback);
  }

  private static Tab featureTab(String title, VBox content) {
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    Tab tab = new Tab(title, scroll);
    tab.setClosable(false);
    return tab;
  }

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
  }

  private static Button navigationButton(String text, String id) {
    Button button = new Button(text);
    button.setId(id);
    button.setMaxWidth(Double.MAX_VALUE);
    return button;
  }

  private static Button button(String label, String id) {
    Button button = new Button(label);
    button.setId(id);
    return button;
  }

  private static TableView<AppointmentListRow> appointmentTable(String id) {
    TableView<AppointmentListRow> table = new TableView<>();
    table.setId(id);
    table.setPrefHeight(360);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPlaceholder(
        UiComponents.emptyState(id + "-empty", "No appointments match these filters."));
    TableColumn<AppointmentListRow, String> date =
        textColumn(DATE_LABEL, row -> row.appointment().startsAt().toLocalDate().toString());
    TableColumn<AppointmentListRow, String> time =
        textColumn(
            "Time",
            row ->
                row.appointment().startsAt().toLocalTime()
                    + " – "
                    + row.appointment().endsAt().toLocalTime());
    TableColumn<AppointmentListRow, String> patient =
        textColumn("Patient", AppointmentListRow::patientDisplayName);
    TableColumn<AppointmentListRow, String> doctor =
        textColumn(DOCTOR_LABEL, AppointmentListRow::doctorDisplayName);
    TableColumn<AppointmentListRow, AppointmentStatus> status = statusColumn("Status");
    table.getColumns().add(date);
    table.getColumns().add(time);
    table.getColumns().add(patient);
    table.getColumns().add(doctor);
    table.getColumns().add(status);
    return table;
  }

  private static TableColumn<AppointmentListRow, AppointmentStatus> statusColumn(String title) {
    TableColumn<AppointmentListRow, AppointmentStatus> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyObjectWrapper<>(data.getValue().appointment().status()));
    column.setCellFactory(
        tableColumn ->
            new TableCell<>() {
              @Override
              protected void updateItem(AppointmentStatus item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.statusBadge(item.name()));
              }
            });
    return column;
  }

  private static TableView<Receipt> receiptTable(String id) {
    TableView<Receipt> table = new TableView<>();
    table.setId(id);
    table.setPrefHeight(360);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    Label empty = UiComponents.emptyState(id + "-empty", "No receipts match these filters.");
    empty.getStyleClass().add("table-empty-row");
    empty.setPrefHeight(52);
    empty.setMaxHeight(52);
    table.setPlaceholder(empty);
    table
        .getColumns()
        .addAll(
            List.of(
                textColumn(
                    "Receipt",
                    receipt ->
                        receipt.receiptDate() + "-" + "%04d".formatted(receipt.sequenceNumber())),
                textColumn(
                    "Date and time", receipt -> receipt.recordedAt().format(DATE_TIME_FORMAT)),
                textColumn("Patient", Receipt::patientName),
                textColumn(DOCTOR_LABEL, Receipt::doctorName),
                textColumn("Amount", receipt -> formatMinor(receipt.amountMinor())),
                textColumn("Method", receipt -> displayStatus(receipt.method()))));
    return table;
  }

  private static <T> TableColumn<T, String> textColumn(
      String title, Function<T, String> valueProvider) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyStringWrapper(valueOrEmpty(valueProvider.apply(data.getValue()))));
    return column;
  }

  private static String displayStatus(Object value) {
    return value == null
        ? ""
        : value
                .toString()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .substring(0, 1)
                .toUpperCase(Locale.ROOT)
            + value.toString().toLowerCase(Locale.ROOT).replace('_', ' ').substring(1);
  }

  private static GridPane filterGrid(String id) {
    GridPane grid = new GridPane();
    grid.setId(id);
    grid.getStyleClass().add("uniform-filter-grid");
    grid.setHgap(12);
    grid.setVgap(10);
    return grid;
  }

  private static void addUniformColumns(
      GridPane grid, int count, double minimumWidth, double preferredWidth, double maximumWidth) {
    for (int index = 0; index < count; index++) {
      ColumnConstraints column = new ColumnConstraints(minimumWidth, preferredWidth, maximumWidth);
      column.setHgrow(Priority.ALWAYS);
      column.setFillWidth(true);
      grid.getColumnConstraints().add(column);
    }
  }

  private static void alignFilterAction(Button button) {
    button.setMinHeight(38);
    button.setPrefHeight(38);
    GridPane.setValignment(button, VPos.BOTTOM);
  }

  private static AppointmentStatus selectedAppointmentStatus(String value) {
    if (value == null || ALL_STATUSES.equals(value)) {
      return null;
    }
    return AppointmentStatus.valueOf(value.toUpperCase(Locale.ROOT).replace(' ', '_'));
  }

  private static PaymentMethod selectedPaymentMethod(String value) {
    if (value == null || ALL_METHODS.equals(value)) {
      return null;
    }
    return PaymentMethod.valueOf(value.toUpperCase(Locale.ROOT).replace(' ', '_'));
  }

  private static SearchSuggestionField<Account> doctorSelector(String id, String prompt) {
    return new SearchSuggestionField<>(
        id,
        prompt,
        ReceptionistView::doctorLabel,
        doctor -> doctor.displayName() + " " + doctor.username());
  }

  private static String doctorLabel(Account account) {
    return account == null ? "" : account.displayName() + " (" + account.username() + ")";
  }

  private static void refreshDoctors(
      ClinicServices services,
      Session session,
      SearchSuggestionField<Account> doctor,
      Label feedback) {
    try {
      doctor.setItems(services.accountService().listDoctors(session));
      if (!doctor.getItems().isEmpty()) {
        doctor.select(doctor.getItems().getFirst());
      }
    } catch (SQLException exception) {
      UiComponents.showError(feedback, "Doctors are temporarily unavailable");
    }
  }

  private static void refreshCheckoutReady(
      ClinicServices services,
      Session session,
      TableView<AppointmentListRow> list,
      Label feedback,
      String patientQuery,
      Long doctorId,
      LocalDate date) {
    try {
      List<AppointmentListRow> appointments =
          services
              .appointmentService()
              .searchAppointmentRows(
                  session, date, doctorId, patientQuery, AppointmentStatus.COMPLETED);
      list.setItems(FXCollections.observableArrayList(appointments));
    } catch (SQLException exception) {
      UiComponents.showError(feedback, "Checkout appointments are temporarily unavailable");
    }
  }

  private static void refreshReceiptHistory(
      ClinicServices services,
      Session session,
      TableView<Receipt> history,
      Label preview,
      String patientQuery,
      Long doctorId,
      LocalDate date,
      Label feedback) {
    try {
      List<Receipt> receipts =
          services.billingService().receiptHistory(session, patientQuery, doctorId, date);
      history.setItems(FXCollections.observableArrayList(receipts));
      preview.setText("");
    } catch (SQLException | ValidationException | AuthorizationException exception) {
      UiComponents.showError(feedback, "Receipt history is temporarily unavailable");
    }
  }

  private static String formatReceiptRow(Receipt receipt) {
    return String.format(
        Locale.ROOT,
        "Receipt %s-%04d | Date: %s | Patient: %s (%s) | Doctor: %s | Amount: %s | Method: %s",
        receipt.receiptDate(),
        receipt.sequenceNumber(),
        receipt.recordedAt().format(DATE_TIME_FORMAT),
        receipt.patientName(),
        receipt.patientId(),
        receipt.doctorName(),
        formatMinor(receipt.amountMinor()),
        receipt.method());
  }

  private static String formatReceipt(Receipt receipt) {
    return formatReceiptRow(receipt) + "\nRecorded: " + receipt.recordedAt() + "\nStatus: PAID";
  }

  private static void exportReport(
      RevenueReport report, javafx.stage.Window owner, boolean json, Label feedback) {
    FileChooser chooser = new FileChooser();
    chooser.setInitialFileName(json ? "revenue-report.json" : "revenue-report.csv");
    javafx.stage.FileChooser.ExtensionFilter filter =
        new javafx.stage.FileChooser.ExtensionFilter(
            json ? "JSON files" : "CSV files", json ? "*.json" : "*.csv");
    chooser.getExtensionFilters().add(filter);
    java.io.File target = chooser.showSaveDialog(owner);
    if (target == null) {
      return;
    }
    try {
      String content = json ? reportJson(report) : reportCsv(report);
      Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
      UiComponents.showMessage(feedback, "Revenue report exported");
    } catch (java.io.IOException exception) {
      UiComponents.showError(feedback, "Revenue report export failed");
    } catch (ArithmeticException exception) {
      UiComponents.showError(feedback, "Revenue total exceeds the supported range");
    }
  }

  static String reportCsv(RevenueReport report) {
    StringBuilder csv =
        new StringBuilder("receipt,dateTime,patientId,patientName,doctor,amount,method\n");
    for (Receipt receipt : report.receipts()) {
      csv.append(receipt.sequenceNumber())
          .append(',')
          .append(receipt.recordedAt())
          .append(',')
          .append(receipt.patientId())
          .append(',')
          .append(csvField(receipt.patientName()))
          .append(',')
          .append(csvField(receipt.doctorName()))
          .append(',')
          .append(formatMinor(receipt.amountMinor()))
          .append(',')
          .append(csvField(receipt.method()))
          .append('\n');
    }
    csv.append('\n')
        .append("summary,successfulPayments,receiptCount,total\n")
        .append("summary,")
        .append(report.receiptCount())
        .append(',')
        .append(report.receiptCount())
        .append(',')
        .append(formatMinor(report.totalMinor()))
        .append('\n');
    appendCsvTotals(csv, "paymentMethod", report.byMethod());
    appendCsvTotals(csv, "doctor", report.byDoctor());
    return csv.toString();
  }

  static String reportJson(RevenueReport report) {
    StringBuilder json =
        new StringBuilder("{\"successfulPaymentCount\":")
            .append(report.receiptCount())
            .append(",\"receiptCount\":")
            .append(report.receiptCount())
            .append(",\"total\":")
            .append(jsonString(formatMinor(report.totalMinor())))
            .append(",\"paymentMethods\":")
            .append(jsonTotals(report.byMethod()))
            .append(",\"doctors\":")
            .append(jsonTotals(report.byDoctor()))
            .append(",\"receipts\":[");
    for (int index = 0; index < report.receipts().size(); index++) {
      Receipt receipt = report.receipts().get(index);
      if (index > 0) {
        json.append(',');
      }
      json.append("{\"receiptNumber\":")
          .append(receipt.sequenceNumber())
          .append(",\"dateTime\":")
          .append(jsonString(receipt.recordedAt()))
          .append(",\"patientId\":")
          .append(receipt.patientId())
          .append(",\"patientName\":")
          .append(jsonString(receipt.patientName()))
          .append(",\"doctor\":")
          .append(jsonString(receipt.doctorName()))
          .append(",\"amount\":")
          .append(jsonString(formatMinor(receipt.amountMinor())))
          .append(",\"method\":")
          .append(jsonString(receipt.method()))
          .append('}');
    }
    return json.append("]}").toString();
  }

  private static void appendCsvTotals(StringBuilder csv, String category, Map<?, Long> totals) {
    totals.entrySet().stream()
        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
        .forEach(
            entry ->
                csv.append(category)
                    .append(',')
                    .append(csvField(entry.getKey()))
                    .append(',')
                    .append(formatMinor(entry.getValue()))
                    .append('\n'));
  }

  private static String jsonTotals(Map<?, Long> totals) {
    java.util.StringJoiner json = new java.util.StringJoiner(",", "{", "}");
    totals.entrySet().stream()
        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
        .forEach(
            entry ->
                json.add(
                    jsonString(entry.getKey()) + ":" + jsonString(formatMinor(entry.getValue()))));
    return json.toString();
  }

  private static String csvField(Object value) {
    String text = String.valueOf(value);
    if (text.indexOf(',') < 0
        && text.indexOf('"') < 0
        && text.indexOf('\r') < 0
        && text.indexOf('\n') < 0) {
      return text;
    }
    return '"' + text.replace("\"", "\"\"") + '"';
  }

  private static String jsonString(Object value) {
    String text = String.valueOf(value);
    StringBuilder escaped = new StringBuilder(text.length() + 2).append('"');
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < JSON_CONTROL_CHARACTER_LIMIT) {
            escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.append('"').toString();
  }

  private static void refreshSchedule(
      ClinicServices services,
      Session session,
      TableView<AppointmentListRow> appointmentList,
      SelectionState selection,
      Label feedback,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      String status,
      Label summary) {
    try {
      List<AppointmentListRow> appointments =
          services
              .appointmentService()
              .searchAppointmentRows(
                  session, date, doctorId, patientQuery, selectedAppointmentStatus(status));
      appointmentList.setItems(FXCollections.observableArrayList(appointments));
      selectAppointment(appointmentList, selection.appointmentId);
      long pending =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.PENDING)
              .count();
      long accepted =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.ACCEPTED)
              .count();
      long checkedIn =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.CHECKED_IN)
              .count();
      long completed =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.COMPLETED)
              .count();
      long declined =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.DECLINED)
              .count();
      summary.setText(
          appointments.size()
              + " appointment(s) | Pending: "
              + pending
              + " | Accepted: "
              + accepted
              + " | Declined: "
              + declined
              + " | Checked in: "
              + checkedIn
              + " | Completed: "
              + completed);
    } catch (SQLException exception) {
      UiComponents.showError(feedback, "Appointments are temporarily unavailable");
    }
  }

  private static void refreshQueue(
      ClinicServices services,
      Session session,
      TableView<AppointmentListRow> queue,
      Label feedback,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      String status,
      Label summary) {
    try {
      List<AppointmentListRow> appointments = new ArrayList<>();
      boolean includeWaiting =
          status == null || QUEUE_ALL.equals(status) || QUEUE_WAITING.equals(status);
      boolean includeChecked =
          status == null || QUEUE_ALL.equals(status) || QUEUE_CHECKED_IN.equals(status);
      if (includeWaiting) {
        appointments.addAll(
            services
                .appointmentService()
                .searchAppointmentRows(
                    session, date, doctorId, patientQuery, AppointmentStatus.ACCEPTED));
      }
      if (includeChecked) {
        appointments.addAll(
            services
                .appointmentService()
                .searchAppointmentRows(
                    session, date, doctorId, patientQuery, AppointmentStatus.CHECKED_IN));
      }
      appointments.sort(Comparator.comparing(row -> row.appointment().startsAt()));
      queue.setItems(FXCollections.observableArrayList(appointments));
      long waiting =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.ACCEPTED)
              .count();
      long checkedIn =
          appointments.stream()
              .filter(a -> a.appointment().status() == AppointmentStatus.CHECKED_IN)
              .count();
      summary.setText(
          "Waiting: "
              + waiting
              + " | Checked in: "
              + checkedIn
              + " | Total: "
              + appointments.size());
    } catch (SQLException exception) {
      UiComponents.showError(feedback, "Check-in queue is temporarily unavailable");
    }
  }

  private static void showRescheduleDialog(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated) {
    AppointmentDialog.showReceptionistEdit(
        services, session, appointmentId, workspaceFeedback, onUpdated);
  }

  private static void showCheckInDetailsDialog(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Clock clock,
      Runnable onUpdated) {
    try {
      Appointment appointment = services.appointmentService().get(appointmentId);
      Patient patient =
          services.patientService().getAdministrative(session, appointment.patientId());
      Label details =
          new Label(
              valueOrEmpty(patient.firstName())
                  + " "
                  + valueOrEmpty(patient.lastName())
                  + "\nEmail: "
                  + valueOrEmpty(patient.email())
                  + "\nPhone: "
                  + valueOrEmpty(patient.phone())
                  + "\nDoctor: "
                  + doctorDisplayName(services, session, appointment.doctorId())
                  + "\nScheduled: "
                  + appointment.startsAt().format(DATE_TIME_FORMAT)
                  + " - "
                  + appointment.endsAt().toLocalTime()
                  + "\nStatus: "
                  + appointment.status());
      details.setId("reception-check-in-details");
      Label feedback = new Label();
      feedback.setId("reception-check-in-feedback");
      Button checkIn = button("Check in patient", "reception-check-in-submit");
      boolean eligible =
          appointment.status() == AppointmentStatus.ACCEPTED
              && !LocalDateTime.now(ClinicClock.withClinicZone(clock))
                  .isBefore(appointment.startsAt());
      checkIn.setDisable(!eligible);
      Stage dialog = new Stage();
      checkIn.setOnAction(
          event -> {
            try {
              services.appointmentService().checkIn(session, appointmentId);
              UiComponents.showMessage(workspaceFeedback, "Patient checked in");
              onUpdated.run();
              dialog.close();
            } catch (ValidationException | AuthorizationException exception) {
              UiComponents.showError(feedback, exception.getMessage());
            } catch (SQLException exception) {
              UiComponents.showError(feedback, "Check-in is temporarily unavailable");
            }
          });
      VBox content = new VBox(12, new Label("Appointment details"), details, checkIn, feedback);
      content.setPadding(new Insets(18));
      dialog.initOwner(workspaceFeedback.getScene().getWindow());
      dialog.initModality(Modality.WINDOW_MODAL);
      dialog.setTitle("Check-in details");
      dialog.setScene(new Scene(content, 500, 300));
      dialog.show();
    } catch (SQLException | ValidationException | AuthorizationException exception) {
      UiComponents.showError(workspaceFeedback, exception.getMessage());
    }
  }

  private static void showCheckoutDetailsDialog(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Label receiptPreview,
      Runnable onUpdated) {
    try {
      Appointment appointment = services.appointmentService().get(appointmentId);
      Patient patient =
          services.patientService().getAdministrative(session, appointment.patientId());
      Label details =
          new Label(
              valueOrEmpty(patient.firstName())
                  + " "
                  + valueOrEmpty(patient.lastName())
                  + "\nEmail: "
                  + valueOrEmpty(patient.email())
                  + "\nPhone: "
                  + valueOrEmpty(patient.phone())
                  + "\nDoctor: "
                  + doctorDisplayName(services, session, appointment.doctorId())
                  + "\nScheduled: "
                  + appointment.startsAt().format(DATE_TIME_FORMAT)
                  + " - "
                  + appointment.endsAt().toLocalTime()
                  + "\nStatus: "
                  + appointment.status());
      details.setId("reception-checkout-details");
      TextField charge = field("reception-charge", "Amount");
      ComboBox<PaymentMethod> method = UiComponents.compactSelector();
      method.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
      method.setId("reception-method");
      method.getSelectionModel().select(PaymentMethod.CASH);
      Button checkout = button("Complete checkout", "reception-checkout");
      Label feedback = new Label();
      feedback.setId("reception-checkout-feedback");
      GridPane paymentForm = new GridPane();
      paymentForm.setHgap(8);
      paymentForm.setVgap(8);
      paymentForm.addRow(0, new Label("Amount"), charge);
      paymentForm.addRow(1, new Label("Method"), method);
      Stage dialog = new Stage();
      checkout.setOnAction(
          event -> {
            try {
              services
                  .billingService()
                  .checkout(
                      session, appointmentId, parseMinor(charge.getText()), method.getValue());
              Receipt receipt =
                  services.billingService().receiptForAppointment(session, appointmentId);
              receiptPreview.setText(formatReceipt(receipt));
              UiComponents.showMessage(workspaceFeedback, "Checkout completed");
              onUpdated.run();
              dialog.close();
            } catch (ValidationException | AuthorizationException exception) {
              UiComponents.showError(feedback, exception.getMessage());
            } catch (SQLException exception) {
              UiComponents.showError(feedback, "Checkout is temporarily unavailable");
            }
          });
      VBox content =
          new VBox(12, new Label("Checkout appointment"), details, paymentForm, checkout, feedback);
      content.setPadding(new Insets(18));
      dialog.initOwner(workspaceFeedback.getScene().getWindow());
      dialog.initModality(Modality.WINDOW_MODAL);
      dialog.setTitle("Checkout details");
      dialog.setScene(new Scene(content, 500, 400));
      dialog.show();
    } catch (SQLException | ValidationException | AuthorizationException exception) {
      UiComponents.showError(workspaceFeedback, exception.getMessage());
    }
  }

  private static LocalDateTime parseScheduleDateTime(
      DatePicker date, AppointmentDialog.TimeFields time, String fieldName) {
    return AppointmentDialog.parseDateTime(date, time, fieldName);
  }

  private static String patientDisplayName(
      ClinicServices services, Session session, long patientId) {
    try {
      Patient details = services.patientService().getAdministrative(session, patientId);
      return (valueOrEmpty(details.firstName()) + " " + valueOrEmpty(details.lastName())).trim();
    } catch (SQLException | ValidationException | AuthorizationException ignored) {
      return "Patient unavailable";
    }
  }

  private static String doctorDisplayName(ClinicServices services, Session session, long doctorId) {
    try {
      return services.accountService().listDoctors(session).stream()
          .filter(doctor -> doctor.id() == doctorId)
          .map(Account::displayName)
          .findFirst()
          .orElse("Doctor unavailable");
    } catch (SQLException | AuthorizationException exception) {
      return "Doctor unavailable";
    }
  }

  private static void selectAppointment(TableView<AppointmentListRow> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).appointment().id() == id) {
        list.getSelectionModel().select(index);
        return;
      }
    }
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private static long parseMinor(String value) {
    if (value == null) {
      throw new ValidationException("Amount must be a positive number with at most two decimals");
    }
    try {
      return new BigDecimal(value.trim())
          .setScale(2, RoundingMode.UNNECESSARY)
          .movePointRight(2)
          .longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new ValidationException(
          "Amount must be a positive number with at most two decimals", exception);
    }
  }

  private static String formatMinor(long amountMinor) {
    return BigDecimal.valueOf(amountMinor, 2).toPlainString();
  }

  private static String formatRevenueSummary(RevenueReport report) {
    return "Successful payments: "
        + report.receiptCount()
        + "    Total: $"
        + formatMinor(report.totalMinor())
        + "\nPayment methods: "
        + formatTotals(report.byMethod())
        + "\nDoctors: "
        + formatTotals(report.byDoctor());
  }

  private static String formatTotals(Map<?, Long> totals) {
    if (totals.isEmpty()) {
      return "None";
    }
    return totals.entrySet().stream()
        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
        .map(entry -> displayStatus(entry.getKey()) + " $" + formatMinor(entry.getValue()))
        .reduce((left, right) -> left + " · " + right)
        .orElse("None");
  }

  private static void requireSelection(long id, String message) {
    if (id == 0) {
      throw new ValidationException(message);
    }
  }

  private static final class SelectionState {
    private long appointmentId;
  }
}
