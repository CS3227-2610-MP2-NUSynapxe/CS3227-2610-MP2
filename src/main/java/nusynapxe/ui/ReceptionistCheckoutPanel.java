package nusynapxe.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ValidationException;

/** Owns Receptionist check-in, checkout, and receipt-history controls. */
final class ReceptionistCheckoutPanel {
  private static final String DATE_LABEL = "Date";
  private static final String DOCTOR_LABEL = "Doctor";
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String QUEUE_WAITING = "Waiting";
  private static final String QUEUE_CHECKED_IN = "Checked in";
  private static final String QUEUE_ALL = "All";
  private static final String PATIENT_NAME_ID = "Name, NRIC/FIN, phone, or email";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final ReceptionistDataLoader dataLoader;
  private final Label workspaceFeedback;
  private final Clock clinicClock;
  private final DatePicker queueDate;
  private final SearchSuggestionField<Account> queueDoctor;
  private final TextField queuePatient;
  private final ComboBox<String> queueStatus;
  private final Label queueSummary;
  private final TableView<AppointmentListRow> queueList;
  private final DatePicker checkoutDate;
  private final SearchSuggestionField<Account> checkoutDoctor;
  private final TextField checkoutPatient;
  private final TableView<AppointmentListRow> checkoutAppointmentList;
  private final ReceptionistReceiptPanel receiptPanel;
  private final VBox queueView;
  private final VBox checkoutView;
  private boolean checkoutTabActive;
  private boolean checkoutMouseSelection;
  private Runnable refreshAppointments =
      () -> {
        /* Set by the workspace after panel composition. */
      };

  ReceptionistCheckoutPanel(
      ReceptionistDataLoader dataLoader, Label workspaceFeedback, Clock clinicClock) {
    this.dataLoader = dataLoader;
    this.workspaceFeedback = workspaceFeedback;
    this.clinicClock = clinicClock;
    queueDate =
        UiComponents.compactDatePicker(
            clinicClock.instant().atZone(clinicClock.getZone()).toLocalDate());
    queueDate.setId("reception-check-in-queue-date");
    queueDoctor = doctorSelector("reception-check-in-queue-doctor", ALL_DOCTORS);
    queuePatient = field("reception-check-in-queue-patient", PATIENT_NAME_ID);
    queueStatus = UiComponents.compactSelector();
    queueStatus.setItems(
        FXCollections.observableArrayList(QUEUE_WAITING, QUEUE_CHECKED_IN, QUEUE_ALL));
    queueStatus.setId("reception-check-in-queue-status");
    queueStatus.getSelectionModel().select(QUEUE_ALL);
    queueSummary = new Label();
    queueSummary.setId("reception-check-in-queue-summary");
    queueList = ReceptionistAppointmentView.appointmentTable("reception-check-in-queue-list");
    Button queueSearch = button("Search", "reception-check-in-queue-search");

    checkoutDate = UiComponents.compactDatePicker();
    checkoutDate.setId("reception-checkout-date");
    checkoutDoctor = doctorSelector("reception-checkout-doctor", ALL_DOCTORS);
    checkoutPatient = field("reception-checkout-patient", PATIENT_NAME_ID);
    checkoutAppointmentList =
        ReceptionistAppointmentView.appointmentTable("reception-checkout-appointment-list");
    Button checkoutSearch = button("Search checkout", "reception-checkout-search");

    receiptPanel = new ReceptionistReceiptPanel(dataLoader, workspaceFeedback);

    configureQueue(queueSearch);
    configureCheckout(checkoutSearch);
    queueView = buildQueueView(queueSearch);
    checkoutView = buildCheckoutView(checkoutSearch);
  }

  VBox queueContent() {
    return queueView;
  }

  VBox checkoutContent() {
    return checkoutView;
  }

  void setRefreshAppointments(Runnable refreshAppointments) {
    this.refreshAppointments = refreshAppointments;
  }

  void setCheckoutTabActive(boolean active) {
    checkoutTabActive = active;
    if (!active) {
      dataLoader.invalidateAppointmentDetails();
    }
  }

  void refreshDoctors() {
    dataLoader.refreshDoctors(queueDoctor, workspaceFeedback, false);
    dataLoader.refreshDoctors(checkoutDoctor, workspaceFeedback, false);
    receiptPanel.refreshDoctors();
  }

  void refreshQueue() {
    dataLoader.refreshQueue(
        queueList,
        workspaceFeedback,
        queueDate.getValue(),
        queueDoctor.getValue() == null ? null : queueDoctor.getValue().id(),
        queuePatient.getText(),
        queueStatus.getValue(),
        queueSummary);
  }

  void refreshCheckout() {
    dataLoader.refreshCheckoutReady(
        checkoutAppointmentList,
        workspaceFeedback,
        checkoutPatient.getText(),
        checkoutDoctor.getValue() == null ? null : checkoutDoctor.getValue().id(),
        checkoutDate.getValue());
    receiptPanel.refresh();
  }

  void refreshReceipts() {
    receiptPanel.refresh();
  }

  private void configureQueue(Button queueSearch) {
    queueList.setOnMouseClicked(
        event -> {
          AppointmentListRow selectedRow = queueList.getSelectionModel().getSelectedItem();
          Appointment selected = selectedRow == null ? null : selectedRow.appointment();
          if (selected != null) {
            showCheckInDetailsDialog(
                selected.id(),
                () -> {
                  refreshQueue();
                  refreshAppointments.run();
                });
          }
        });
    queueDate.valueProperty().addListener((observable, previous, selected) -> refreshQueue());
    queueDoctor.valueProperty().addListener((observable, previous, selected) -> refreshQueue());
    queueStatus.valueProperty().addListener((observable, previous, selected) -> refreshQueue());
    queuePatient.textProperty().addListener((observable, previous, selected) -> refreshQueue());
    queueSearch.setOnAction(event -> refreshQueue());
  }

  private void configureCheckout(Button checkoutSearch) {
    checkoutAppointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected != null && checkoutTabActive && !checkoutMouseSelection) {
                showCheckoutDetailsDialog(selected.appointment().id(), this::refreshAfterCheckout);
              }
            });
    checkoutAppointmentList.setOnMousePressed(event -> checkoutMouseSelection = true);
    checkoutAppointmentList.setOnMouseClicked(
        event -> {
          AppointmentListRow selected =
              checkoutAppointmentList.getSelectionModel().getSelectedItem();
          if (selected != null) {
            showCheckoutDetailsDialog(selected.appointment().id(), this::refreshAfterCheckout);
          }
          checkoutMouseSelection = false;
        });
    checkoutSearch.setOnAction(event -> refreshCheckout());
    checkoutPatient
        .textProperty()
        .addListener((observable, previous, selected) -> refreshReceipts());
    checkoutDoctor
        .valueProperty()
        .addListener((observable, previous, selected) -> refreshReceipts());
    checkoutDate.valueProperty().addListener((observable, previous, selected) -> refreshReceipts());
  }

  private VBox buildQueueView(Button queueSearch) {
    GridPane filters = filterGrid("reception-check-in-filter-grid");
    filters.getStyleClass().add("uniform-filter-grid");
    addUniformColumns(filters, 4, 170, 220, 220);
    filters.add(UiComponents.fieldGroup(DATE_LABEL, queueDate), 0, 0);
    filters.add(UiComponents.fieldGroup(DOCTOR_LABEL, queueDoctor), 1, 0);
    filters.add(UiComponents.fieldGroup("Status", queueStatus), 2, 0);
    filters.add(UiComponents.fieldGroup("Patient", queuePatient), 3, 0);
    filters.add(queueSearch, 4, 0);
    alignFilterAction(queueSearch);
    return new VBox(
        12,
        UiComponents.pageTitle("Check in"),
        UiComponents.supportingText(
            "Find arriving patients and review appointments already checked in."),
        UiComponents.card(
            "reception-check-in-card",
            UiComponents.sectionHeading("Check-in appointments"),
            filters,
            queueSummary,
            queueList));
  }

  private VBox buildCheckoutView(Button checkoutSearch) {
    GridPane checkoutFilters = filterGrid("reception-checkout-filter-grid");
    checkoutFilters.getStyleClass().add("spacious-filter-grid");
    addUniformColumns(checkoutFilters, 3, 180, 240, 240);
    checkoutFilters.add(UiComponents.fieldGroup("Patient", checkoutPatient), 0, 0);
    checkoutFilters.add(UiComponents.fieldGroup(DOCTOR_LABEL, checkoutDoctor), 1, 0);
    checkoutFilters.add(UiComponents.fieldGroup(DATE_LABEL, checkoutDate), 2, 0);
    checkoutFilters.add(checkoutSearch, 3, 0);
    alignFilterAction(checkoutSearch);
    VBox ready =
        UiComponents.card(
            "reception-checkout-ready-card",
            UiComponents.sectionHeading("Checkout appointments"),
            checkoutFilters,
            checkoutAppointmentList);
    VBox readyContent = new VBox(ready);
    readyContent.getStyleClass().add("subtab-content");
    readyContent.setPadding(new Insets(22, 0, 0, 0));
    readyContent.setId("reception-checkout-ready-tab");
    javafx.scene.control.Tab checkout = new javafx.scene.control.Tab("Checkout", readyContent);
    checkout.setClosable(false);
    javafx.scene.control.Tab receiptsTab =
        new javafx.scene.control.Tab("Receipts", receiptPanel.content());
    receiptsTab.setClosable(false);
    javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane(checkout, receiptsTab);
    tabs.setId("reception-checkout-tabs");
    tabs.getStyleClass().add("sub-navigation");
    tabs.getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              setCheckoutTabActive(selected == checkout);
            });
    return new VBox(
        12,
        UiComponents.pageTitle("Checkout and receipts"),
        UiComponents.supportingText(
            "Complete payments for finished visits or review previously issued receipts."),
        tabs);
  }

  private void refreshAfterCheckout() {
    refreshCheckout();
    refreshAppointments.run();
    refreshQueue();
  }

  private void showCheckInDetailsDialog(long appointmentId, Runnable onUpdated) {
    dataLoader.loadCheckInDetails(
        appointmentId,
        details -> showCheckInDetailsDialog(details, appointmentId, onUpdated),
        failure ->
            showTaskError(
                workspaceFeedback, failure, "Check-in details are temporarily unavailable"));
  }

  private void showCheckInDetailsDialog(
      ReceptionistDataLoader.AppointmentDetails loaded, long appointmentId, Runnable onUpdated) {
    Appointment appointment = loaded.appointment();
    Patient patient = loaded.patient();
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
                + loaded.doctorName()
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
    checkIn.setDisable(
        appointment.status() != AppointmentStatus.ACCEPTED
            || clinicClock
                .instant()
                .atZone(clinicClock.getZone())
                .toLocalDateTime()
                .isBefore(appointment.startsAt()));
    Stage dialog = new Stage();
    checkIn.setOnAction(
        event ->
            dataLoader.checkIn(
                appointmentId,
                () -> {
                  UiComponents.showMessage(workspaceFeedback, "Patient checked in");
                  onUpdated.run();
                  dialog.close();
                },
                failure ->
                    showTaskError(feedback, failure, "Check-in is temporarily unavailable")));
    VBox content = new VBox(12, new Label("Appointment details"), details, checkIn, feedback);
    content.setPadding(new Insets(18));
    dialog.initOwner(workspaceFeedback.getScene().getWindow());
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Check-in details");
    dialog.setScene(new Scene(content, 500, 300));
    UiComponents.applyStylesheet(dialog.getScene());
    dialog.show();
  }

  private void showCheckoutDetailsDialog(long appointmentId, Runnable onUpdated) {
    dataLoader.loadCheckoutDetails(
        appointmentId,
        details -> showCheckoutDetailsDialog(details, appointmentId, onUpdated),
        failure ->
            showTaskError(
                workspaceFeedback, failure, "Checkout details are temporarily unavailable"));
  }

  private void showCheckoutDetailsDialog(
      ReceptionistDataLoader.AppointmentDetails loaded, long appointmentId, Runnable onUpdated) {
    Appointment appointment = loaded.appointment();
    Patient patient = loaded.patient();
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
                + loaded.doctorName()
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
            dataLoader.checkout(
                appointmentId,
                parseMinor(charge.getText()),
                method.getValue(),
                receipt -> {
                  receipt.ifPresent(receiptPanel::showReceipt);
                  UiComponents.showMessage(workspaceFeedback, "Checkout completed");
                  onUpdated.run();
                  dialog.close();
                },
                failure -> showTaskError(feedback, failure, "Checkout is temporarily unavailable"));
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
    VBox content =
        new VBox(12, new Label("Checkout appointment"), details, paymentForm, checkout, feedback);
    content.setPadding(new Insets(18));
    dialog.initOwner(workspaceFeedback.getScene().getWindow());
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Checkout details");
    dialog.setScene(new Scene(content, 500, 400));
    UiComponents.applyStylesheet(dialog.getScene());
    dialog.show();
  }

  private static SearchSuggestionField<Account> doctorSelector(String id, String prompt) {
    return new SearchSuggestionField<>(
        id,
        prompt,
        Account::displayName,
        account -> account.displayName() + " " + account.username());
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

  private static GridPane filterGrid(String id) {
    GridPane grid = new GridPane();
    grid.setId(id);
    grid.setHgap(12);
    grid.setVgap(10);
    return grid;
  }

  private static void addUniformColumns(GridPane grid, int count, double... widths) {
    for (int index = 0; index < count; index++) {
      javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints();
      column.setMinWidth(widths[Math.min(index, widths.length - 1)]);
      column.setHgrow(Priority.SOMETIMES);
      grid.getColumnConstraints().add(column);
    }
  }

  private static void alignFilterAction(Button button) {
    GridPane.setValignment(button, VPos.BOTTOM);
    button.setMinHeight(38);
    button.setPrefHeight(38);
  }

  private static long parseMinor(String value) {
    if (value == null) {
      throw new ValidationException("Amount must be a positive number with at most two decimals");
    }
    try {
      long amount =
          new BigDecimal(value.trim())
              .setScale(2, RoundingMode.UNNECESSARY)
              .movePointRight(2)
              .longValueExact();
      if (amount <= 0) {
        throw new ValidationException("Amount must be a positive number with at most two decimals");
      }
      return amount;
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new ValidationException(
          "Amount must be a positive number with at most two decimals", exception);
    }
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private static void showTaskError(Label feedback, Throwable failure, String fallback) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, fallback);
    }
  }
}
