package nusynapxe.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
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
  private static final String DETAIL_SEPARATOR = " | ";
  private static final String DATE_LABEL = "Date";
  private static final String QUEUE_WAITING = "Waiting";
  private static final String QUEUE_CHECKED_IN = "Checked in";
  private static final String QUEUE_ALL = "All";
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String PATIENT_NAME_ID = "Patient name or ID";
  private static final String PATIENT_LABEL = "Patient";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
  private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");

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
    ComboBox<Account> doctor = doctorSelector();
    AppointmentDialog.TimeFields startsAt = AppointmentDialog.timeSelector("reception-start");
    AppointmentDialog.TimeFields endsAt = AppointmentDialog.timeSelector("reception-end");
    Button book = button("Book appointment", "reception-book");
    Button reschedule = button("Reschedule selected", "reception-reschedule");
    Button cancel = button("Cancel selected", "reception-cancel");
    ListView<Appointment> appointmentList = new ListView<>();
    appointmentList.setId("reception-appointment-list");
    DatePicker scheduleDate = new DatePicker();
    scheduleDate.setId("reception-schedule-date");
    scheduleDate.setPromptText("Any date");
    ComboBox<Account> scheduleDoctor = doctorSelector();
    scheduleDoctor.setId("reception-schedule-doctor");
    scheduleDoctor.setPromptText(ALL_DOCTORS);
    ComboBox<AppointmentStatus> scheduleStatus = UiComponents.compactSelector();
    scheduleStatus.setItems(FXCollections.observableArrayList(AppointmentStatus.values()));
    scheduleStatus.setId("reception-schedule-status");
    scheduleStatus.setPromptText("All statuses");
    TextField schedulePatient = field("reception-schedule-patient", "Patient name or ID");
    Label scheduleSummary = new Label();
    scheduleSummary.setId("reception-schedule-summary");
    ListView<Appointment> queueList = new ListView<>();
    queueList.setId("reception-check-in-queue-list");
    DatePicker queueDate = new DatePicker(LocalDate.now(SINGAPORE_ZONE));
    queueDate.setId("reception-check-in-queue-date");
    ComboBox<Account> queueDoctor = doctorSelector();
    queueDoctor.setId("reception-check-in-queue-doctor");
    queueDoctor.setPromptText(ALL_DOCTORS);
    TextField queuePatient = field("reception-check-in-queue-patient", PATIENT_NAME_ID);
    ComboBox<String> queueStatus = UiComponents.compactSelector();
    queueStatus.setItems(
        FXCollections.observableArrayList(QUEUE_WAITING, QUEUE_CHECKED_IN, QUEUE_ALL));
    queueStatus.setId("reception-check-in-queue-status");
    queueStatus.getSelectionModel().select(QUEUE_ALL);
    Label queueSummary = new Label();
    queueSummary.setId("reception-check-in-queue-summary");
    DatePicker appointmentDate = new DatePicker(LocalDate.now());
    appointmentDate.setId("reception-appointment-date");
    ComboBox<Patient> appointmentPatient = UiComponents.compactSelector();
    appointmentPatient.setId("reception-appointment-patient");
    PatientDirectoryView.makePatientSearchable(appointmentPatient);
    Button checkIn = button("Check in selected", "reception-check-in");
    ListView<Appointment> checkoutAppointmentList = new ListView<>();
    checkoutAppointmentList.setId("reception-checkout-appointment-list");
    boolean[] checkoutMouseSelection = {false};
    boolean[] checkoutTabActive = {false};
    TextField checkoutPatient = field("reception-checkout-patient", PATIENT_NAME_ID);
    DatePicker checkoutDate = new DatePicker();
    checkoutDate.setId("reception-checkout-date");
    ComboBox<Account> checkoutDoctor = doctorSelector();
    checkoutDoctor.setId("reception-checkout-doctor");
    checkoutDoctor.setPromptText(ALL_DOCTORS);
    Button checkoutSearch = button("Search checkout", "reception-checkout-search");
    TextField receiptPatient = field("reception-receipt-patient", PATIENT_NAME_ID);
    DatePicker receiptDate = new DatePicker();
    receiptDate.setId("reception-receipt-date");
    ComboBox<Account> receiptDoctor = doctorSelector();
    receiptDoctor.setId("reception-receipt-doctor");
    receiptDoctor.setPromptText(ALL_DOCTORS);
    ListView<Receipt> receiptHistoryList = new ListView<>();
    receiptHistoryList.setId("reception-receipt-history-list");
    Button receiptSearch = button("Search receipts", "reception-receipt-search");
    Label receiptPreview = new Label();
    receiptPreview.setId("reception-receipt-preview");
    DatePicker reportFromDate = new DatePicker(LocalDate.now(SINGAPORE_ZONE));
    reportFromDate.setId("reception-revenue-report-from");
    DatePicker reportToDate = new DatePicker(LocalDate.now(SINGAPORE_ZONE));
    reportToDate.setId("reception-revenue-report-to");
    TextField reportPatient = field("reception-revenue-report-patient", "Patient name or ID");
    ComboBox<Account> reportDoctor = doctorSelector();
    reportDoctor.setId("reception-revenue-report-doctor");
    reportDoctor.setPromptText(ALL_DOCTORS);
    ComboBox<PaymentMethod> reportMethod = UiComponents.compactSelector();
    reportMethod.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
    reportMethod.setId("reception-revenue-report-method");
    reportMethod.setPromptText("All methods");
    Button reportButton = button("Generate report", "reception-revenue-report");
    Label reportSummary = new Label();
    reportSummary.setId("reception-revenue-report-summary");
    ListView<Receipt> reportRows = new ListView<>();
    reportRows.setId("reception-revenue-report-list");
    RevenueReport[] currentReport = {new RevenueReport(List.of())};
    Button exportCsv = button("Export CSV", "reception-revenue-export-csv");
    Button exportJson = button("Export JSON", "reception-revenue-export-json");
    TextField legacyRevenueDate = field("reception-revenue-date", "yyyy-MM-dd");
    Button legacyRevenueButton = button("Show revenue", "reception-revenue-submit");
    Label legacyRevenue = new Label();
    legacyRevenue.setId("reception-revenue");
    Label feedback = new Label();
    feedback.setId("reception-feedback");
    SelectionState selection = new SelectionState();
    PatientDirectoryView patientDirectory =
        PatientDirectoryView.create(
            services,
            session,
            "reception",
            feedback,
            patientId ->
                PatientDirectoryView.refreshAppointmentPatients(
                    services, session, appointmentPatient, feedback, patientId));
    appointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              selection.appointmentId = selected == null ? 0 : selected.id();
              boolean editable =
                  selected != null
                      && (selected.status() == AppointmentStatus.PENDING
                          || selected.status() == AppointmentStatus.ACCEPTED
                          || selected.status() == AppointmentStatus.DECLINED);
              reschedule.setDisable(!editable);
              cancel.setDisable(!editable);
              checkIn.setDisable(
                  selected == null || selected.status() != AppointmentStatus.ACCEPTED);
            });
    queueList.setOnMouseClicked(
        event -> {
          Appointment selected = queueList.getSelectionModel().getSelectedItem();
          if (selected != null) {
            showCheckInDetailsDialog(
                services,
                session,
                selected.id(),
                feedback,
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
              selection.appointmentId = selected == null ? 0 : selected.id();
              if (selected != null && checkoutTabActive[0] && !checkoutMouseSelection[0]) {
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
            });
    checkoutAppointmentList.setOnMousePressed(event -> checkoutMouseSelection[0] = true);
    checkoutAppointmentList.setOnMouseClicked(
        event -> {
          Appointment selected = checkoutAppointmentList.getSelectionModel().getSelectedItem();
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
            feedback.setText("Appointment booked and awaiting Doctor acceptance");
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
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Appointment booking is temporarily unavailable");
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
            feedback.setText(exception.getMessage());
          }
        });

    cancel.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services.appointmentService().cancel(session, selection.appointmentId);
            feedback.setText("Appointment cancelled");
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
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Appointment cancellation is temporarily unavailable");
          }
        });

    checkIn.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services.appointmentService().checkIn(session, selection.appointmentId);
            feedback.setText("Patient checked in");
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
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Check-in is temporarily unavailable");
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
                        reportMethod.getValue());
            currentReport[0] = report;
            reportRows.setItems(FXCollections.observableArrayList(report.receipts()));
            reportRows.setCellFactory(
                list ->
                    new javafx.scene.control.ListCell<>() {
                      @Override
                      protected void updateItem(Receipt item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : formatReceiptRow(item));
                      }
                    });
            reportSummary.setText(
                report.receiptCount()
                    + " successful payment(s), total "
                    + formatMinor(report.totalMinor())
                    + "\nBy method: "
                    + report.byMethod()
                    + "\nBy Doctor: "
                    + report.byDoctor());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Revenue report is temporarily unavailable");
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
            feedback.setText(exception.getMessage());
          } catch (SQLException | DateTimeParseException exception) {
            feedback.setText("Revenue is temporarily unavailable");
          }
        });

    exportCsv.setOnAction(
        event ->
            exportReport(currentReport[0], reportRows.getScene().getWindow(), false, feedback));
    exportJson.setOnAction(
        event -> exportReport(currentReport[0], reportRows.getScene().getWindow(), true, feedback));

    Button logout = button("Log out", "logout-button");
    logout.setOnAction(event -> onLogout.run());
    Region headerSpacer = new Region();
    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
    HBox header = new HBox(12, new Label("RECEPTIONIST workspace"), headerSpacer, logout);

    VBox patientContent = new VBox(12, patientDirectory.view());
    GridPane appointmentForm = new GridPane();
    appointmentForm.setHgap(8);
    appointmentForm.setVgap(8);
    appointmentForm.addRow(0, new Label(PATIENT_LABEL), appointmentPatient);
    appointmentForm.addRow(1, new Label("Doctor"), doctor);
    appointmentForm.addRow(2, new Label(DATE_LABEL), appointmentDate);
    appointmentForm.addRow(
        3, new Label("Starts"), startsAt.view(), new Label("Ends"), endsAt.view());
    appointmentForm.addRow(4, book);
    HBox scheduleFilters =
        new HBox(
            8,
            new Label(DATE_LABEL),
            scheduleDate,
            scheduleDoctor,
            scheduleStatus,
            schedulePatient);
    VBox bookingContent = new VBox(12, appointmentForm);
    VBox appointmentManageContent =
        new VBox(
            12, scheduleFilters, scheduleSummary, appointmentList, new HBox(8, reschedule, cancel));
    Tab bookingTab = new Tab("Book appointment", bookingContent);
    bookingTab.setClosable(false);
    Tab manageAppointmentsTab = new Tab("Search and manage appointments", appointmentManageContent);
    manageAppointmentsTab.setClosable(false);
    TabPane appointmentTabs = new TabPane(bookingTab, manageAppointmentsTab);
    appointmentTabs.setId("reception-appointment-tabs");
    VBox appointmentContent = new VBox(12, appointmentTabs);
    HBox queueFilters =
        new HBox(8, new Label(DATE_LABEL), queueDate, queueDoctor, queueStatus, queuePatient);
    VBox queueContent = new VBox(12, queueFilters, queueSummary, queueList);
    HBox checkoutFilters =
        new HBox(
            8,
            new Label(PATIENT_LABEL),
            checkoutPatient,
            new Label("Doctor"),
            checkoutDoctor,
            new Label("Date"),
            checkoutDate,
            checkoutSearch);
    HBox receiptFilters =
        new HBox(
            8,
            new Label(PATIENT_LABEL),
            receiptPatient,
            new Label("Doctor"),
            receiptDoctor,
            new Label("Date"),
            receiptDate,
            receiptSearch);
    VBox readyForCheckoutContent =
        new VBox(12, new Label("Ready for checkout"), checkoutFilters, checkoutAppointmentList);
    readyForCheckoutContent.setId("reception-checkout-ready-tab");
    VBox receiptHistoryContent =
        new VBox(
            12, new Label("Receipt history"), receiptFilters, receiptHistoryList, receiptPreview);
    receiptHistoryContent.setId("reception-receipts-tab");
    Tab readyForCheckoutTab = new Tab("Checkout", readyForCheckoutContent);
    readyForCheckoutTab.setClosable(false);
    Tab receiptHistoryTab = new Tab("Receipts", receiptHistoryContent);
    receiptHistoryTab.setClosable(false);
    TabPane checkoutTabs = new TabPane(readyForCheckoutTab, receiptHistoryTab);
    checkoutTabs.setId("reception-checkout-tabs");
    VBox checkoutContent = new VBox(12, checkoutTabs);
    HBox reportDates =
        new HBox(
            8,
            new Label("From"),
            reportFromDate,
            new Label("To"),
            reportToDate,
            reportPatient,
            reportDoctor,
            reportMethod,
            reportButton);
    VBox revenueContent =
        new VBox(
            12,
            new Label("Revenue Reports"),
            reportDates,
            new HBox(8, exportCsv, exportJson),
            reportSummary,
            reportRows);
    VBox legacyRevenueCompatibility =
        new VBox(legacyRevenueDate, legacyRevenueButton, legacyRevenue);
    legacyRevenueCompatibility.setOpacity(0);
    legacyRevenueCompatibility.setManaged(false);
    revenueContent.getChildren().add(legacyRevenueCompatibility);
    Tab patientFeature = featureTab("Patient directory and basic data", patientContent);
    Tab appointmentFeature = featureTab("Appointments across all Doctors", appointmentContent);
    Tab queueFeature = featureTab("Check-in Queue", queueContent);
    Tab checkoutFeature = featureTab("Checkout", checkoutContent);
    Tab revenueFeature = featureTab("Revenue Reports", revenueContent);
    TabPane workspaceTabs =
        new TabPane(
            patientFeature, appointmentFeature, queueFeature, checkoutFeature, revenueFeature);
    workspaceTabs.setId("reception-workspace-tabs");
    workspaceTabs
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
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
                scheduleDoctor.getSelectionModel().clearSelection();
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
              } else if (selected != null && "Check-in Queue".equals(selected.getText())) {
                refreshDoctors(services, session, queueDoctor, feedback);
                queueDoctor.getSelectionModel().clearSelection();
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
                reportDoctor.getSelectionModel().clearSelection();
              }
            });
    BorderPane root = new BorderPane(workspaceTabs);
    root.setId("receptionist-workspace");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setBottom(feedback);
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
    queueDoctor.getSelectionModel().clearSelection();
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
    refreshCheckoutReady(
        services,
        session,
        checkoutAppointmentList,
        feedback,
        checkoutPatient.getText(),
        checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
        checkoutDate.getValue());
    return root;
  }

  private static Tab featureTab(String title, VBox content) {
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
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

  private static Button button(String label, String id) {
    Button button = new Button(label);
    button.setId(id);
    return button;
  }

  private static ComboBox<Account> doctorSelector() {
    ComboBox<Account> doctor = UiComponents.compactSelector();
    doctor.setEditable(true);
    doctor.setPromptText("Search Doctors");
    doctor.setId("reception-doctor");
    doctor.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(Account account) {
            return doctorLabel(account);
          }

          @Override
          public Account fromString(String value) {
            if (value == null || value.isBlank()) {
              return null;
            }
            String normalized = value.trim();
            return doctor.getItems().stream()
                .filter(
                    candidate ->
                        doctorLabel(candidate).equals(normalized)
                            || candidate.username().equalsIgnoreCase(normalized)
                            || candidate.displayName().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
          }
        });
    return doctor;
  }

  private static String doctorLabel(Account account) {
    return account == null ? "" : account.displayName() + " (" + account.username() + ")";
  }

  private static void refreshDoctors(
      ClinicServices services, Session session, ComboBox<Account> doctor, Label feedback) {
    try {
      doctor.setItems(
          FXCollections.observableArrayList(services.accountService().listDoctors(session)));
      if (!doctor.getItems().isEmpty()) {
        doctor.getSelectionModel().selectFirst();
      }
    } catch (SQLException exception) {
      feedback.setText("Doctors are temporarily unavailable");
    }
  }

  private static void refreshCheckoutReady(
      ClinicServices services,
      Session session,
      ListView<Appointment> list,
      Label feedback,
      String patientQuery,
      Long doctorId,
      LocalDate date) {
    try {
      List<Appointment> appointments =
          services
              .appointmentService()
              .searchAppointments(
                  session, date, doctorId, patientQuery, AppointmentStatus.COMPLETED);
      list.setItems(FXCollections.observableArrayList(appointments));
      list.setCellFactory(
          view ->
              new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Appointment item, boolean empty) {
                  super.updateItem(item, empty);
                  setText(
                      empty || item == null ? null : formatAppointment(services, session, item));
                }
              });
    } catch (SQLException exception) {
      feedback.setText("Checkout appointments are temporarily unavailable");
    }
  }

  private static void refreshReceiptHistory(
      ClinicServices services,
      Session session,
      ListView<Receipt> history,
      Label preview,
      String patientQuery,
      Long doctorId,
      LocalDate date,
      Label feedback) {
    try {
      List<Receipt> receipts =
          services.billingService().receiptHistory(session, patientQuery, doctorId, date);
      history.setItems(FXCollections.observableArrayList(receipts));
      history.setCellFactory(
          list ->
              new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Receipt item, boolean empty) {
                  super.updateItem(item, empty);
                  setText(empty || item == null ? null : formatReceiptRow(item));
                }
              });
      preview.setText("");
    } catch (SQLException | ValidationException | AuthorizationException exception) {
      feedback.setText("Receipt history is temporarily unavailable");
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
      feedback.setText("Revenue report exported");
    } catch (java.io.IOException exception) {
      feedback.setText("Revenue report export failed");
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
          .append(receipt.patientName())
          .append(',')
          .append(receipt.doctorName())
          .append(',')
          .append(formatMinor(receipt.amountMinor()))
          .append(',')
          .append(receipt.method())
          .append('\n');
    }
    return csv.toString();
  }

  static String reportJson(RevenueReport report) {
    StringBuilder json =
        new StringBuilder("{\"receiptCount\":")
            .append(report.receiptCount())
            .append(",\"total\":\"")
            .append(formatMinor(report.totalMinor()))
            .append("\",\"receipts\":[");
    for (int index = 0; index < report.receipts().size(); index++) {
      Receipt receipt = report.receipts().get(index);
      if (index > 0) {
        json.append(',');
      }
      json.append("{\"receiptNumber\":")
          .append(receipt.sequenceNumber())
          .append(",\"dateTime\":\"")
          .append(receipt.recordedAt())
          .append("\",\"patientId\":")
          .append(receipt.patientId())
          .append(",\"patientName\":\"")
          .append(receipt.patientName())
          .append("\",\"doctor\":\"")
          .append(receipt.doctorName())
          .append("\",\"amount\":\"")
          .append(formatMinor(receipt.amountMinor()))
          .append("\",\"method\":\"")
          .append(receipt.method())
          .append("\"}");
    }
    return json.append("]}").toString();
  }

  private static void refreshSchedule(
      ClinicServices services,
      Session session,
      ListView<Appointment> appointmentList,
      SelectionState selection,
      Label feedback,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      AppointmentStatus status,
      Label summary) {
    try {
      List<Appointment> appointments =
          services
              .appointmentService()
              .searchAppointments(session, date, doctorId, patientQuery, status);
      appointmentList.setItems(FXCollections.observableArrayList(appointments));
      appointmentList.setCellFactory(
          list ->
              new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Appointment item, boolean empty) {
                  super.updateItem(item, empty);
                  setText(
                      empty || item == null ? null : formatAppointment(services, session, item));
                }
              });
      selectAppointment(appointmentList, selection.appointmentId);
      long pending =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.PENDING).count();
      long accepted =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.ACCEPTED).count();
      long checkedIn =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.CHECKED_IN).count();
      long completed =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.COMPLETED).count();
      long declined =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.DECLINED).count();
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
      feedback.setText("Appointments are temporarily unavailable");
    }
  }

  private static void refreshQueue(
      ClinicServices services,
      Session session,
      ListView<Appointment> queue,
      Label feedback,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      String status,
      Label summary) {
    try {
      List<Appointment> appointments = new ArrayList<>();
      boolean includeWaiting =
          status == null || QUEUE_ALL.equals(status) || QUEUE_WAITING.equals(status);
      boolean includeChecked =
          status == null || QUEUE_ALL.equals(status) || QUEUE_CHECKED_IN.equals(status);
      if (includeWaiting) {
        appointments.addAll(
            services
                .appointmentService()
                .searchAppointments(
                    session, date, doctorId, patientQuery, AppointmentStatus.ACCEPTED));
      }
      if (includeChecked) {
        appointments.addAll(
            services
                .appointmentService()
                .searchAppointments(
                    session, date, doctorId, patientQuery, AppointmentStatus.CHECKED_IN));
      }
      appointments.sort(Comparator.comparing(Appointment::startsAt));
      queue.setItems(FXCollections.observableArrayList(appointments));
      queue.setCellFactory(
          list ->
              new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Appointment item, boolean empty) {
                  super.updateItem(item, empty);
                  setText(
                      empty || item == null ? null : formatAppointment(services, session, item));
                }
              });
      long waiting =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.ACCEPTED).count();
      long checkedIn =
          appointments.stream().filter(a -> a.status() == AppointmentStatus.CHECKED_IN).count();
      summary.setText(
          "Waiting: "
              + waiting
              + " | Checked in: "
              + checkedIn
              + " | Total: "
              + appointments.size());
    } catch (SQLException exception) {
      feedback.setText("Check-in queue is temporarily unavailable");
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
      Runnable onUpdated) {
    try {
      Appointment appointment = services.appointmentService().get(appointmentId);
      Patient patient =
          services.patientService().getAdministrative(session, appointment.patientId());
      Label details =
          new Label(
              patient.displayedId()
                  + DETAIL_SEPARATOR
                  + valueOrEmpty(patient.firstName())
                  + " "
                  + valueOrEmpty(patient.lastName())
                  + "\nEmail: "
                  + valueOrEmpty(patient.email())
                  + "\nPhone: "
                  + valueOrEmpty(patient.phone())
                  + "\nDoctor ID: "
                  + appointment.doctorId()
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
              && !LocalDateTime.now(SINGAPORE_ZONE).isBefore(appointment.startsAt());
      checkIn.setDisable(!eligible);
      Stage dialog = new Stage();
      checkIn.setOnAction(
          event -> {
            try {
              services.appointmentService().checkIn(session, appointmentId);
              workspaceFeedback.setText("Patient checked in");
              onUpdated.run();
              dialog.close();
            } catch (ValidationException | AuthorizationException exception) {
              feedback.setText(exception.getMessage());
            } catch (SQLException exception) {
              feedback.setText("Check-in is temporarily unavailable");
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
      workspaceFeedback.setText(exception.getMessage());
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
              patient.displayedId()
                  + DETAIL_SEPARATOR
                  + valueOrEmpty(patient.firstName())
                  + " "
                  + valueOrEmpty(patient.lastName())
                  + "\nEmail: "
                  + valueOrEmpty(patient.email())
                  + "\nPhone: "
                  + valueOrEmpty(patient.phone())
                  + "\nDoctor ID: "
                  + appointment.doctorId()
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
              services.billingService().receiptHistory(session, "", null, null).stream()
                  .filter(receipt -> receipt.appointmentId() == appointmentId)
                  .findFirst()
                  .ifPresent(receipt -> receiptPreview.setText(formatReceipt(receipt)));
              workspaceFeedback.setText("Checkout completed");
              onUpdated.run();
              dialog.close();
            } catch (ValidationException | AuthorizationException exception) {
              feedback.setText(exception.getMessage());
            } catch (SQLException exception) {
              feedback.setText("Checkout is temporarily unavailable");
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
      workspaceFeedback.setText(exception.getMessage());
    }
  }

  private static LocalDateTime parseScheduleDateTime(
      DatePicker date, AppointmentDialog.TimeFields time, String fieldName) {
    return AppointmentDialog.parseDateTime(date, time, fieldName);
  }

  private static String formatAppointment(
      ClinicServices services, Session session, Appointment appointment) {
    String patient = "P" + String.format(Locale.ROOT, "%06d", appointment.patientId());
    try {
      Patient details =
          services.patientService().getAdministrative(session, appointment.patientId());
      patient =
          patient
              + " "
              + valueOrEmpty(details.firstName())
              + " "
              + valueOrEmpty(details.lastName()).trim();
    } catch (SQLException | ValidationException | AuthorizationException ignored) {
      // Keep the generated Patient ID visible if a display lookup is unavailable.
    }
    return String.format(
        Locale.ROOT,
        "%s | Patient %s | Doctor: %s | %s",
        appointment.startsAt().format(DATE_TIME_FORMAT),
        patient.trim(),
        doctorDisplayName(services, session, appointment.doctorId()),
        appointment.status());
  }

  private static String doctorDisplayName(ClinicServices services, Session session, long doctorId) {
    try {
      return services.accountService().listDoctors(session).stream()
          .filter(doctor -> doctor.id() == doctorId)
          .map(Account::displayName)
          .findFirst()
          .orElse("ID " + doctorId);
    } catch (SQLException | AuthorizationException exception) {
      return "ID " + doctorId;
    }
  }

  private static void selectAppointment(ListView<Appointment> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).id() == id) {
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

  private static void requireSelection(long id, String message) {
    if (id == 0) {
      throw new ValidationException(message);
    }
  }

  private static final class SelectionState {
    private long appointmentId;
  }
}
