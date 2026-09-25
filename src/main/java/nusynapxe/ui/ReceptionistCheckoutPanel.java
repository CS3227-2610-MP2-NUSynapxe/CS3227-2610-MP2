package nusynapxe.ui;

import java.time.Clock;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;

/** Owns Receptionist check-in, checkout, and receipt-history controls. */
final class ReceptionistCheckoutPanel {
  private static final String DATE_LABEL = "Date";
  private static final String DOCTOR_LABEL = "Doctor";
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String QUEUE_WAITING = "Waiting";
  private static final String QUEUE_CHECKED_IN = "Checked in";
  private static final String QUEUE_ALL = "All";
  private static final String PATIENT_NAME_ID = "Name, NRIC/FIN, phone, or email";

  private final ReceptionistDataLoader dataLoader;
  private final Label workspaceFeedback;
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
  private final ReceptionistCheckoutDialogs dialogs;
  private final VBox queueView;
  private final VBox checkoutView;
  private final FxDebouncer queueSearchDebouncer;
  private final FxDebouncer receiptSearchDebouncer;
  private final EventHandler<MouseEvent> checkoutMouseReleased =
      event -> checkoutMouseSelection = false;
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
    dialogs =
        new ReceptionistCheckoutDialogs(
            dataLoader, workspaceFeedback, clinicClock, receiptPanel::showReceipt);
    queueSearchDebouncer = new FxDebouncer(Duration.millis(250), this::refreshQueue);
    receiptSearchDebouncer = new FxDebouncer(Duration.millis(250), this::refreshReceipts);

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
            dialogs.showCheckInDetailsDialog(
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
    queuePatient
        .textProperty()
        .addListener((observable, previous, selected) -> queueSearchDebouncer.request());
    queueSearch.setOnAction(event -> queueSearchDebouncer.runNow());
  }

  private void configureCheckout(Button checkoutSearch) {
    checkoutAppointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected != null && checkoutTabActive && !checkoutMouseSelection) {
                dialogs.showCheckoutDetailsDialog(
                    selected.appointment().id(), this::refreshAfterCheckout);
              }
            });
    checkoutAppointmentList.setOnMousePressed(event -> checkoutMouseSelection = true);
    checkoutAppointmentList.setOnMouseClicked(
        event -> {
          AppointmentListRow selected =
              checkoutAppointmentList.getSelectionModel().getSelectedItem();
          if (selected != null) {
            dialogs.showCheckoutDetailsDialog(
                selected.appointment().id(), this::refreshAfterCheckout);
          }
        });
    checkoutAppointmentList
        .sceneProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (previous != null) {
                previous.removeEventFilter(MouseEvent.MOUSE_RELEASED, checkoutMouseReleased);
              }
              if (selected != null) {
                selected.addEventFilter(MouseEvent.MOUSE_RELEASED, checkoutMouseReleased);
              }
            });
    checkoutSearch.setOnAction(
        event -> {
          receiptSearchDebouncer.cancel();
          refreshCheckout();
        });
    checkoutPatient
        .textProperty()
        .addListener((observable, previous, selected) -> receiptSearchDebouncer.request());
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
}
