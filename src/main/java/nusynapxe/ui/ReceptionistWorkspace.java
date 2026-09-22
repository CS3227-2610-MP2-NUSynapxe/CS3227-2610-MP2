package nusynapxe.ui;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/** Composes the Receptionist feature panels and owns shared workspace lifecycle. */
final class ReceptionistWorkspace {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private ReceptionistWorkspace() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the Receptionist workspace.
   *
   * @param services application services used by the workspace
   * @param session authenticated Receptionist session
   * @param onLogout callback invoked when the Receptionist logs out
   * @return root node for the Receptionist workspace
   */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    return create(services, session, onLogout, ClinicClock.system(), ClinicTaskRunner.immediate());
  }

  static Parent create(ClinicServices services, Session session, Runnable onLogout, Clock clock) {
    return create(services, session, onLogout, clock, ClinicTaskRunner.immediate());
  }

  static Parent create(
      ClinicServices services,
      Session session,
      Runnable onLogout,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    Clock clinicClock = ClinicClock.withClinicZone(clock);
    Label feedback = UiComponents.feedback("reception-feedback");
    ReceptionistDataLoader dataLoader = new ReceptionistDataLoader(services, session, taskRunner);
    ReceptionistAppointmentPanel appointmentPanel =
        new ReceptionistAppointmentPanel(services, session, dataLoader, feedback, taskRunner);
    ReceptionistCheckoutPanel checkoutPanel =
        new ReceptionistCheckoutPanel(dataLoader, feedback, clinicClock);
    appointmentPanel.setRefreshCheckout(checkoutPanel::refreshCheckout);
    checkoutPanel.setRefreshAppointments(appointmentPanel::refresh);
    ReceptionistRevenuePanel revenuePanel =
        new ReceptionistRevenuePanel(dataLoader, feedback, clinicClock);
    PatientDirectoryView patientDirectory =
        PatientDirectoryView.create(
            services,
            session,
            "reception",
            feedback,
            appointmentPanel::refreshPatients,
            null,
            clinicClock,
            taskRunner);

    ReceptionistCalendarView[] calendarHolder = new ReceptionistCalendarView[1];
    Button logout = navigationButton("Log out", "logout-button");
    logout.setOnAction(
        event -> {
          dataLoader.dispose();
          patientDirectory.dispose();
          if (calendarHolder[0] != null) {
            calendarHolder[0].dispose();
          }
          onLogout.run();
        });
    HBox header =
        UiComponents.workspaceHeader("RECEPTIONIST workspace", session.username(), logout);

    Tab directoryFeature = featureTab("Directory", patientContent(patientDirectory));
    Tab appointmentFeature = featureTab("Appointments", appointmentPanel.content());
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
                      appointmentPanel.refresh();
                      checkoutPanel.refreshCheckout();
                    },
                    taskRunner),
            selectedAppointment ->
                AppointmentDialog.showReceptionistEdit(
                    services,
                    session,
                    selectedAppointment.appointmentId(),
                    feedback,
                    () -> {
                      calendarHolder[0].refresh();
                      appointmentPanel.refresh();
                      checkoutPanel.refreshCheckout();
                    },
                    taskRunner),
            clinicClock,
            taskRunner);
    Tab calendarFeature = new Tab("Calendar", calendarHolder[0].view());
    calendarFeature.setClosable(false);
    Tab queueFeature = featureTab("Check in", checkoutPanel.queueContent());
    Tab checkoutFeature = featureTab("Checkout", checkoutPanel.checkoutContent());
    Tab revenueFeature = featureTab("Revenue Reports", revenuePanel.view());
    TabPane workspaceTabs =
        new TabPane(
            directoryFeature,
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
        event -> workspaceTabs.getSelectionModel().select(directoryFeature));
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
              checkoutPanel.setCheckoutTabActive(selected == checkoutFeature);
              if (selected == directoryFeature) {
                patientDirectory.refresh();
              } else if (selected == appointmentFeature) {
                appointmentPanel.refreshDoctors();
                appointmentPanel.refreshPatients(patientDirectory.selectedPatientId());
                appointmentPanel.refresh();
              } else if (selected == calendarFeature) {
                calendarHolder[0].refreshDoctors();
                calendarHolder[0].refresh();
              } else if (selected == queueFeature) {
                checkoutPanel.refreshDoctors();
                checkoutPanel.refreshQueue();
              } else if (selected == checkoutFeature) {
                checkoutPanel.refreshDoctors();
                checkoutPanel.refreshCheckout();
              } else if (selected == revenueFeature) {
                revenuePanel.refreshDoctors();
              }
            });

    BorderPane root = new BorderPane(workspaceTabs);
    root.setId("receptionist-workspace");
    root.getStyleClass().add("workspace-shell");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setLeft(navigation);
    BorderPane.setMargin(navigation, new Insets(0, 16, 0, 0));
    appointmentPanel.refreshDoctors();
    appointmentPanel.refreshPatients(0);
    appointmentPanel.refresh();
    checkoutPanel.refreshDoctors();
    checkoutPanel.refreshQueue();
    checkoutPanel.refreshCheckout();
    revenuePanel.refreshDoctors();
    return UiComponents.notificationOverlay(root, feedback);
  }

  private static VBox patientContent(PatientDirectoryView patientDirectory) {
    Parent view = patientDirectory.view();
    VBox content = new VBox(12, view);
    VBox.setVgrow(view, Priority.ALWAYS);
    return content;
  }

  private static Tab featureTab(String title, VBox content) {
    Tab tab = new Tab(title, content);
    tab.setClosable(false);
    return tab;
  }

  private static Button navigationButton(String text, String id) {
    Button button = new Button(text);
    button.setId(id);
    return button;
  }

  static TableView<Receipt> receiptTable(String id) {
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
                    "Receipt", receipt -> receipt.receiptDate() + "-" + receipt.sequenceNumber()),
                textColumn("Date", receipt -> DATE_TIME_FORMAT.format(receipt.recordedAt())),
                textColumn("Patient", Receipt::patientName),
                textColumn("Doctor", Receipt::doctorName),
                textColumn("Amount", receipt -> ReportExporter.formatMinor(receipt.amountMinor())),
                textColumn("Method", receipt -> receipt.method().name())));
    return table;
  }

  private static <T> TableColumn<T, String> textColumn(
      String title, java.util.function.Function<T, String> valueProvider) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
    return column;
  }
}
