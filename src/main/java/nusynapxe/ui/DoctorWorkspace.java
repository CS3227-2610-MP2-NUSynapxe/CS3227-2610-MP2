package nusynapxe.ui;

import java.time.Clock;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;

/** Builds the Doctor schedule and clinical consultation workspace. */
final class DoctorWorkspace {
  private static final String ACTIVE_NAVIGATION_STYLE = "active-navigation";

  private DoctorWorkspace() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the Doctor workspace.
   *
   * @param services application services used by the workspace
   * @param session authenticated Doctor session
   * @param onLogout callback invoked when the Doctor logs out
   * @return root node for the Doctor workspace
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    return create(
        services,
        session,
        onLogout,
        Clock.system(CalendarService.CLINIC_ZONE),
        ClinicTaskRunner.immediate());
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
    Clock clinicClock =
        Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    WorkspaceLifecycle lifecycle = new WorkspaceLifecycle();
    DoctorAppointmentActions.SelectionState selection =
        new DoctorAppointmentActions.SelectionState();
    Label feedback = UiComponents.feedback("doctor-feedback");
    Button accept = UiComponents.primaryButton("Accept", "doctor-accept");
    Button decline = UiComponents.dangerButton("Decline", "doctor-decline");
    Button checkIn = UiComponents.primaryButton("Check in", "doctor-check-in");
    Button reschedule = UiComponents.secondaryButton("Reschedule", "doctor-reschedule");
    Button complete = UiComponents.primaryButton("Mark consultation completed", "doctor-complete");
    DoctorConsultationPanel consultationPanel =
        new DoctorConsultationPanel(
            services,
            session,
            feedback,
            taskRunner,
            () -> selection.appointmentId,
            () -> selection.generation);
    TextField diagnosis = consultationPanel.diagnosisField();
    TextArea consultationNotes = consultationPanel.consultationNotesArea();
    TextArea followUpNotes = consultationPanel.followUpNotesArea();
    Button saveConsultation = consultationPanel.saveButton();
    Button addPrescription = consultationPanel.addPrescriptionButton();
    ListView<Prescription> prescriptions = consultationPanel.prescriptionsList();
    Label selectionSummary = new Label();
    selectionSummary.setId("doctor-selected-appointment");
    selectionSummary.getStyleClass().add("selection-summary");
    selectionSummary.setWrapText(true);
    ClinicalHistoryView clinicalHistoryView =
        ClinicalHistoryView.create(services, session, feedback, taskRunner);
    Runnable[] showHistoryHolder = new Runnable[1];
    Button viewPatientHistory =
        UiComponents.secondaryButton("View consultation history", "doctor-view-patient-history");
    viewPatientHistory.setOnAction(
        event -> {
          if (selection.patientId == 0) {
            UiComponents.showError(feedback, "Select an appointment first");
            return;
          }
          clinicalHistoryView.showPatient(selection.patientId);
          showHistoryHolder[0].run();
        });
    Label noSelection =
        UiComponents.emptyState(
            "doctor-no-selection",
            "Select an appointment from the schedule to edit its clinical context.");
    DoctorDashboardDayView[] dashboardHolder = new DoctorDashboardDayView[1];
    DoctorAppointmentActions.SelectionNodes[] nodesHolder =
        new DoctorAppointmentActions.SelectionNodes[1];

    VBox prescriptionForm = consultationPanel.prescriptionFormView();
    HBox consultationActions = consultationPanel.consultationActionsBar();
    VBox consultationCard = consultationPanel.consultationCardView();
    HBox prescriptionActions = consultationPanel.prescriptionActionsBar();
    VBox prescriptionCard = consultationPanel.prescriptionCardView();
    HBox completionActions = UiComponents.actionBar(complete);
    VBox completionCard =
        UiComponents.card(
            "doctor-completion-card",
            UiComponents.sectionHeading("Complete visit"),
            UiComponents.supportingText(
                "Complete the consultation when the clinical record is ready."),
            completionActions);
    VBox patientDetailsCard = UiComponents.card("doctor-patient-details-card");
    VBox appointmentActionsCard = UiComponents.card("doctor-appointment-actions-card");
    Label terminalStatusMessage = UiComponents.supportingText("");
    terminalStatusMessage.setId("doctor-status-message");
    VBox terminalStatusCard =
        UiComponents.card(
            "doctor-terminal-status-card",
            UiComponents.sectionHeading("Appointment status"),
            terminalStatusMessage);
    Label clinicalReadOnly =
        UiComponents.supportingText(
            "This consultation is complete and can only be viewed from the Dashboard.");
    clinicalReadOnly.setId("doctor-clinical-read-only");
    VBox selectedContent = new VBox(16);
    selectedContent.setId("doctor-selected-content");
    DoctorAppointmentActions.SelectionNodes nodes =
        new DoctorAppointmentActions.SelectionNodes(
            selectionSummary,
            noSelection,
            selectedContent,
            patientDetailsCard,
            appointmentActionsCard,
            terminalStatusCard,
            terminalStatusMessage,
            clinicalReadOnly,
            consultationCard,
            prescriptionCard,
            completionCard,
            consultationActions,
            prescriptionForm,
            prescriptionActions,
            completionActions,
            accept,
            decline,
            checkIn,
            reschedule,
            saveConsultation,
            addPrescription,
            complete,
            diagnosis,
            consultationNotes,
            followUpNotes,
            prescriptions,
            viewPatientHistory);
    nodesHolder[0] = nodes;
    DoctorAppointmentActions.configure(
        accept,
        decline,
        checkIn,
        reschedule,
        complete,
        saveConsultation,
        addPrescription,
        services,
        session,
        selection,
        feedback,
        taskRunner,
        () -> dashboardHolder[0].refresh(),
        lifecycle);
    consultationPanel.setOnClinicalLoaded(
        clinicalLoaded -> {
          if (!clinicalLoaded && nodesHolder[0] != null) {
            DoctorAppointmentActions.setClinicalEditable(nodesHolder[0], false);
          }
        });
    DoctorAppointmentActions.updateSelectionState(selection, nodes, null, null, clinicClock);

    dashboardHolder[0] =
        new DoctorDashboardDayView(
            services,
            session,
            feedback,
            appointment ->
                DoctorAppointmentActions.selectDashboardAppointment(
                    services,
                    session,
                    appointment,
                    selection,
                    nodes,
                    consultationPanel,
                    feedback,
                    clinicClock,
                    taskRunner),
            clinicClock,
            taskRunner);
    VBox scheduleCard =
        UiComponents.card(
            "doctor-schedule-card",
            UiComponents.pageTitle("Dashboard"),
            UiComponents.supportingText("Select an appointment to open its clinical context."),
            dashboardHolder[0].view());
    VBox detailContent = new VBox(16, noSelection, selectionSummary, selectedContent);
    detailContent.setId("doctor-detail-pane");
    ScrollPane detailScroll = new ScrollPane(detailContent);
    detailScroll.setId("doctor-detail-scroll");
    detailScroll.setFitToWidth(true);
    SplitPane masterDetail = new SplitPane(scheduleCard, detailScroll);
    masterDetail.setId("doctor-master-detail");
    masterDetail.setDividerPositions(0.36);

    PatientDirectoryView patientDirectory =
        PatientDirectoryView.create(
            services,
            session,
            "doctor",
            feedback,
            ignoredPatientId -> {
              dashboardHolder[0].refresh();
              clinicalHistoryView.refreshPatients();
            },
            () -> showHistoryHolder[0].run(),
            clinicClock,
            taskRunner);
    setVisibleManaged(clinicalHistoryView.view(), false);
    StackPane patientPages = new StackPane(patientDirectory.view(), clinicalHistoryView.view());
    patientPages.setId("doctor-patient-pages");
    patientPages.setMaxHeight(Double.MAX_VALUE);
    ScrollPane patientsPage = new ScrollPane(patientPages);
    patientsPage.setId("doctor-patients-page");
    patientsPage.setFitToWidth(true);
    patientsPage.setFitToHeight(true);
    StackPane pages = new StackPane(masterDetail);
    pages.setId("doctor-page-content");
    Button dashboardNavigation = UiComponents.secondaryButton("Dashboard", "doctor-nav-dashboard");
    Button patientsNavigation = UiComponents.secondaryButton("Patients", "doctor-nav-patients");
    Button calendarNavigation = UiComponents.secondaryButton("Calendar", "doctor-nav-calendar");
    dashboardNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
    Label navigationTitle = new Label("Navigation");
    navigationTitle.setId("doctor-navigation-title");
    navigationTitle.getStyleClass().add("navigation-title");
    navigationTitle.setMaxWidth(Double.MAX_VALUE);
    VBox navigation =
        new VBox(0, navigationTitle, dashboardNavigation, patientsNavigation, calendarNavigation);
    navigation.setId("doctor-navigation");
    DoctorCalendarView[] calendarHolder = new DoctorCalendarView[1];
    DoctorCalendarSettingsView[] settingsHolder = new DoctorCalendarSettingsView[1];
    Runnable showClinicalHistory =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].hide();
          setVisibleManaged(patientDirectory.view(), false);
          setVisibleManaged(clinicalHistoryView.view(), true);
          pages.getChildren().setAll(patientsPage);
          activate(dashboardNavigation, patientsNavigation, calendarNavigation, patientsNavigation);
        };
    showHistoryHolder[0] = showClinicalHistory;
    Runnable showDirectory =
        () -> {
          setVisibleManaged(patientDirectory.view(), true);
          setVisibleManaged(clinicalHistoryView.view(), false);
          patientDirectory.refresh();
          clinicalHistoryView.refreshPatients();
        };
    Runnable showCalendar =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].show();
          pages.getChildren().setAll(calendarHolder[0].view());
          activate(dashboardNavigation, patientsNavigation, calendarNavigation, calendarNavigation);
        };
    Runnable showSettings =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].hide();
          settingsHolder[0].reload();
          pages.getChildren().setAll(settingsHolder[0].view());
          activate(dashboardNavigation, patientsNavigation, calendarNavigation, calendarNavigation);
        };
    calendarHolder[0] =
        new DoctorCalendarView(
            services,
            session,
            showSettings,
            feedback,
            clinicClock,
            taskRunner,
            lifecycle::isActive);
    settingsHolder[0] =
        new DoctorCalendarSettingsView(
            services, session, showCalendar, showCalendar, feedback, taskRunner);
    dashboardNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
          dashboardHolder[0].show();
          pages.getChildren().setAll(masterDetail);
          activate(
              dashboardNavigation, patientsNavigation, calendarNavigation, dashboardNavigation);
        });
    patientsNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
          dashboardHolder[0].hide();
          showDirectory.run();
          pages.getChildren().setAll(patientsPage);
          activate(dashboardNavigation, patientsNavigation, calendarNavigation, patientsNavigation);
        });
    calendarNavigation.setOnAction(event -> showCalendar.run());

    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(
        event -> {
          lifecycle.invalidate();
          clinicalHistoryView.dispose();
          patientDirectory.dispose();
          calendarHolder[0].dispose();
          dashboardHolder[0].dispose();
          settingsHolder[0].dispose();
          onLogout.run();
        });
    HBox header = UiComponents.workspaceHeader("DOCTOR workspace", session.username(), logout);
    BorderPane root = new BorderPane();
    root.setId("doctor-workspace");
    root.getStyleClass().add("workspace-shell");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setLeft(navigation);
    BorderPane.setMargin(navigation, new Insets(0, 16, 0, 0));
    root.setCenter(pages);
    dashboardHolder[0].show();
    return UiComponents.notificationOverlay(root, feedback);
  }

  private static void activate(Button dashboard, Button patients, Button calendar, Button active) {
    dashboard.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
    patients.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
    calendar.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
    active.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
  }

  private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
    node.setVisible(visible);
    node.setManaged(visible);
  }
}
