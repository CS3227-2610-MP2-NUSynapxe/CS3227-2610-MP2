package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the Doctor schedule and clinical consultation workspace. */
public final class DoctorView {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final String FIELD_SEPARATOR = " | ";
  private static final String ACTIVE_NAVIGATION_STYLE = "active-navigation";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

  private DoctorView() {
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
    return create(services, session, onLogout, Clock.system(CalendarService.CLINIC_ZONE));
  }

  static Parent create(ClinicServices services, Session session, Runnable onLogout, Clock clock) {
    Clock clinicClock =
        Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    SelectionState selection = new SelectionState();

    Button accept = UiComponents.primaryButton("Accept", "doctor-accept");
    Button decline = UiComponents.dangerButton("Decline", "doctor-decline");
    Button checkIn = UiComponents.primaryButton("Check in", "doctor-check-in");
    Button reschedule = UiComponents.secondaryButton("Reschedule", "doctor-reschedule");

    TextField diagnosis = field("doctor-diagnosis", "Diagnosis");
    TextArea consultationNotes = textArea("doctor-consultation-notes", "Consultation notes");
    TextArea followUpNotes = textArea("doctor-follow-up", "Follow-up notes");
    Button saveConsultation =
        UiComponents.primaryButton("Save consultation", "doctor-consultation-save");

    TextField medication = field("doctor-medication", "Medication");
    TextField dosage = field("doctor-dosage", "Dosage");
    TextField frequency = field("doctor-frequency", "Frequency");
    TextField duration = field("doctor-duration", "Duration");
    TextField instructions = field("doctor-instructions", "Instructions");
    Button addPrescription =
        UiComponents.primaryButton("Add prescription", "doctor-prescription-submit");
    ListView<Prescription> prescriptions = new ListView<>();
    prescriptions.setId("doctor-prescription-list");
    prescriptions.setPlaceholder(
        UiComponents.emptyState("doctor-prescription-empty", "No prescriptions have been added."));
    prescriptions.setCellFactory(
        view ->
            new ListCell<>() {
              @Override
              protected void updateItem(Prescription prescription, boolean empty) {
                super.updateItem(prescription, empty);
                setText(
                    empty || prescription == null
                        ? null
                        : prescription.medication()
                            + FIELD_SEPARATOR
                            + prescription.dosage()
                            + FIELD_SEPARATOR
                            + prescription.frequency()
                            + FIELD_SEPARATOR
                            + prescription.duration()
                            + FIELD_SEPARATOR
                            + prescription.instructions());
              }
            });
    Button complete = UiComponents.primaryButton("Mark consultation completed", "doctor-complete");
    Label feedback = UiComponents.feedback("doctor-feedback");
    Label selectionSummary = new Label();
    selectionSummary.setId("doctor-selected-appointment");
    selectionSummary.getStyleClass().add("selection-summary");
    selectionSummary.setWrapText(true);
    ClinicalHistoryView clinicalHistoryView =
        ClinicalHistoryView.create(services, session, feedback);
    Runnable[] showHistoryHolder = new Runnable[1];
    Button viewPatientHistory =
        UiComponents.secondaryButton("View consultation history", "doctor-view-patient-history");
    viewPatientHistory.setOnAction(
        event -> {
          if (selection.patientId == 0) {
            UiComponents.showError(feedback, APPOINTMENT_REQUIRED);
            return;
          }
          clinicalHistoryView.showPatient(selection.patientId);
          showHistoryHolder[0].run();
        });
    Label noSelection =
        UiComponents.emptyState(
            "doctor-no-selection",
            "Select an appointment from the schedule to edit its consultation.");
    DoctorDashboardDayView[] dashboardHolder = new DoctorDashboardDayView[1];
    SelectionNodes[] nodesHolder = new SelectionNodes[1];

    accept.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services.appointmentService().accept(session, selection.appointmentId);
                  feedback.setText("Appointment accepted");
                  dashboardHolder[0].refresh();
                }));

    decline.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services.appointmentService().decline(session, selection.appointmentId);
                  feedback.setText("Appointment declined");
                  dashboardHolder[0].refresh();
                }));

    checkIn.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services.appointmentService().checkIn(session, selection.appointmentId);
                  feedback.setText("Patient checked in");
                  dashboardHolder[0].refresh();
                }));

    reschedule.setOnAction(
        event -> {
          if (selection.appointmentId == 0) {
            UiComponents.showError(feedback, APPOINTMENT_REQUIRED);
            return;
          }
          AppointmentDialog.showDoctorEdit(
              services, session, selection.appointmentId, feedback, dashboardHolder[0]::refresh);
        });

    saveConsultation.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services
                      .clinicalService()
                      .saveConsultation(
                          session,
                          selection.appointmentId,
                          diagnosis.getText(),
                          consultationNotes.getText(),
                          followUpNotes.getText());
                  feedback.setText("Consultation saved");
                  boolean clinicalLoaded =
                      loadClinical(
                          services,
                          session,
                          services.appointmentService().get(selection.appointmentId),
                          diagnosis,
                          consultationNotes,
                          followUpNotes,
                          prescriptions,
                          feedback);
                  if (!clinicalLoaded) {
                    setClinicalEditable(nodesHolder[0], false);
                  }
                }));

    addPrescription.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services
                      .clinicalService()
                      .addPrescription(
                          session,
                          selection.appointmentId,
                          medication.getText(),
                          dosage.getText(),
                          frequency.getText(),
                          duration.getText(),
                          instructions.getText());
                  feedback.setText("Prescription added");
                  boolean clinicalLoaded =
                      loadClinical(
                          services,
                          session,
                          services.appointmentService().get(selection.appointmentId),
                          diagnosis,
                          consultationNotes,
                          followUpNotes,
                          prescriptions,
                          feedback);
                  if (!clinicalLoaded) {
                    setClinicalEditable(nodesHolder[0], false);
                  }
                  clear(medication, dosage, frequency, duration, instructions);
                }));

    complete.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services.appointmentService().complete(session, selection.appointmentId);
                  feedback.setText("Appointment marked completed");
                  dashboardHolder[0].refresh();
                }));

    DoctorCalendarView[] calendarHolder = new DoctorCalendarView[1];
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(
        event -> {
          if (calendarHolder[0] != null) {
            calendarHolder[0].dispose();
          }
          if (dashboardHolder[0] != null) {
            dashboardHolder[0].dispose();
          }
          onLogout.run();
        });
    HBox header = UiComponents.workspaceHeader("DOCTOR workspace", session.username(), logout);

    VBox prescriptionForm =
        new VBox(
            10,
            UiComponents.fieldGroup("Medication", medication),
            UiComponents.fieldGroup("Dosage", dosage),
            UiComponents.fieldGroup("Frequency", frequency),
            UiComponents.fieldGroup("Duration", duration),
            UiComponents.fieldGroup("Instructions", instructions));

    HBox consultationActions = UiComponents.actionBar(saveConsultation);
    VBox consultationCard =
        UiComponents.card(
            "doctor-consultation-card",
            UiComponents.sectionHeading("Consultation"),
            UiComponents.fieldGroup("Diagnosis", diagnosis),
            UiComponents.fieldGroup("Consultation notes", consultationNotes),
            UiComponents.fieldGroup("Follow-up notes", followUpNotes),
            consultationActions);
    HBox prescriptionActions = UiComponents.actionBar(addPrescription);
    VBox prescriptionCard =
        UiComponents.card(
            "doctor-prescription-card",
            UiComponents.sectionHeading("Prescriptions"),
            prescriptionForm,
            prescriptionActions,
            prescriptions);
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
    SelectionNodes nodes =
        new SelectionNodes(
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
    updateSelectionState(selection, nodes, null, null, clinicClock);

    dashboardHolder[0] =
        new DoctorDashboardDayView(
            services,
            session,
            feedback,
            calendarAppointment ->
                selectDashboardAppointment(
                    services,
                    session,
                    calendarAppointment,
                    selection,
                    nodes,
                    feedback,
                    clinicClock),
            clinicClock);
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
            ignoredPatientId -> dashboardHolder[0].refresh(),
            () -> showHistoryHolder[0].run());
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
    DoctorCalendarSettingsView[] settingsHolder = new DoctorCalendarSettingsView[1];
    Runnable showDirectory =
        () -> {
          setVisibleManaged(patientDirectory.view(), true);
          setVisibleManaged(clinicalHistoryView.view(), false);
          patientDirectory.refresh();
          clinicalHistoryView.refreshPatients();
        };
    Runnable showClinicalHistory =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].hide();
          setVisibleManaged(patientDirectory.view(), false);
          setVisibleManaged(clinicalHistoryView.view(), true);
          pages.getChildren().setAll(patientsPage);
          patientsNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    showHistoryHolder[0] = showClinicalHistory;
    Runnable showCalendar =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].show();
          pages.getChildren().setAll(calendarHolder[0].view());
          calendarNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    Runnable showSettings =
        () -> {
          dashboardHolder[0].hide();
          calendarHolder[0].hide();
          settingsHolder[0].reload();
          pages.getChildren().setAll(settingsHolder[0].view());
          calendarNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    calendarHolder[0] = new DoctorCalendarView(services, session, showSettings, feedback);
    settingsHolder[0] =
        new DoctorCalendarSettingsView(services, session, showCalendar, showCalendar, feedback);
    dashboardNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
          dashboardHolder[0].show();
          pages.getChildren().setAll(masterDetail);
          dashboardNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        });
    patientsNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
          dashboardHolder[0].hide();
          showDirectory.run();
          pages.getChildren().setAll(patientsPage);
          patientsNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        });
    calendarNavigation.setOnAction(event -> showCalendar.run());

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

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
  }

  private static TextArea textArea(String id, String prompt) {
    TextArea area = new TextArea();
    area.setId(id);
    area.setPromptText(prompt);
    area.setPrefRowCount(3);
    return area;
  }

  private static void updateSelectionState(
      SelectionState selection,
      SelectionNodes nodes,
      Appointment appointment,
      Patient patient,
      Clock clock) {
    boolean selected = appointment != null && patient != null && selection.appointmentId != 0;
    nodes.summary().setVisible(selected);
    nodes.summary().setManaged(selected);
    nodes.noSelection().setVisible(!selected);
    nodes.noSelection().setManaged(!selected);
    nodes.selectedContent().getChildren().clear();
    setVisibleManaged(nodes.patientDetailsCard(), selected);
    setVisibleManaged(nodes.appointmentActionsCard(), false);
    setVisibleManaged(nodes.terminalStatusCard(), false);
    setActionVisibility(nodes, false);

    if (!selected) {
      nodes.patientDetailsCard().getChildren().clear();
      clearClinical(
          nodes.diagnosis(),
          nodes.consultationNotes(),
          nodes.followUpNotes(),
          nodes.prescriptions());
      setClinicalEditable(nodes, false);
      nodes.summary().getStyleClass().removeIf(style -> style.startsWith("status-"));
      return;
    }

    String readableStatus = displayStatus(appointment.status().name());
    nodes
        .summary()
        .setText(
            patientDisplayName(patient)
                + "  •  "
                + appointment.startsAt().format(DATE_TIME_FORMAT)
                + "  •  "
                + readableStatus);
    nodes
        .summary()
        .setAccessibleText(
            "Appointment for " + patientDisplayName(patient) + ", " + readableStatus);
    updateSummaryStatus(nodes.summary(), appointment.status());

    setClinicalEditable(nodes, false);
    GridPane patientDetails = PatientDirectoryView.patientDetailsGrid(patient);
    patientDetails.setId("doctor-patient-details");
    nodes
        .patientDetailsCard()
        .getChildren()
        .setAll(
            UiComponents.sectionHeading("Patient details"),
            patientDetails,
            UiComponents.actionBar(nodes.viewPatientHistory()));
    nodes.selectedContent().getChildren().add(nodes.patientDetailsCard());

    switch (appointment.status()) {
      case PENDING -> {
        setActionVisibility(nodes, true);
        nodes.checkIn().setVisible(false);
        nodes.checkIn().setManaged(false);
        nodes.accept().setDisable(false);
        nodes.decline().setDisable(false);
        nodes.reschedule().setDisable(false);
        nodes
            .appointmentActionsCard()
            .getChildren()
            .setAll(
                UiComponents.sectionHeading("Appointment actions"),
                UiComponents.actionBar(nodes.accept(), nodes.decline(), nodes.reschedule()));
        setVisibleManaged(nodes.appointmentActionsCard(), true);
        nodes.selectedContent().getChildren().add(nodes.appointmentActionsCard());
      }
      case ACCEPTED -> {
        setActionVisibility(nodes, true);
        nodes.accept().setVisible(false);
        nodes.accept().setManaged(false);
        nodes.decline().setDisable(false);
        nodes.reschedule().setDisable(false);
        boolean checkInEligible = !LocalDateTime.now(clock).isBefore(appointment.startsAt());
        nodes.checkIn().setDisable(!checkInEligible);
        nodes
            .appointmentActionsCard()
            .getChildren()
            .setAll(
                UiComponents.sectionHeading("Appointment actions"),
                UiComponents.actionBar(nodes.decline(), nodes.reschedule(), nodes.checkIn()));
        setVisibleManaged(nodes.appointmentActionsCard(), true);
        nodes.selectedContent().getChildren().add(nodes.appointmentActionsCard());
      }
      case CHECKED_IN -> {
        setClinicalEditable(nodes, true);
        nodes
            .selectedContent()
            .getChildren()
            .addAll(nodes.consultationCard(), nodes.prescriptionCard(), nodes.completionCard());
      }
      case COMPLETED, CHECKED_OUT -> {
        setClinicalEditable(nodes, false);
        nodes
            .selectedContent()
            .getChildren()
            .addAll(nodes.clinicalReadOnly(), nodes.consultationCard(), nodes.prescriptionCard());
      }
      case DECLINED, CANCELLED -> {
        nodes
            .terminalStatusMessage()
            .setText(
                "No Doctor actions are available for this " + readableStatus + " appointment.");
        setVisibleManaged(nodes.terminalStatusCard(), true);
        nodes.selectedContent().getChildren().add(nodes.terminalStatusCard());
      }
    }
  }

  private static String displayStatus(String status) {
    return UiComponents.humanizeStatus(status);
  }

  private static String patientDisplayName(Patient patient) {
    String firstName = patient.firstName() == null ? "" : patient.firstName().trim();
    String lastName = patient.lastName() == null ? "" : patient.lastName().trim();
    String name = (firstName + " " + lastName).trim();
    return name.isBlank() ? "Patient" : name;
  }

  private static void updateSummaryStatus(Label summary, AppointmentStatus status) {
    summary.getStyleClass().removeIf(style -> style.startsWith("status-"));
    summary
        .getStyleClass()
        .add("status-" + status.name().toLowerCase(Locale.ROOT).replace('_', '-'));
  }

  private static void setActionVisibility(SelectionNodes nodes, boolean visible) {
    setVisibleManaged(nodes.accept(), visible);
    setVisibleManaged(nodes.decline(), visible);
    setVisibleManaged(nodes.checkIn(), visible);
    setVisibleManaged(nodes.reschedule(), visible);
  }

  private static void setClinicalEditable(SelectionNodes nodes, boolean editable) {
    setEditable(nodes.diagnosis(), editable);
    setEditable(nodes.consultationNotes(), editable);
    setEditable(nodes.followUpNotes(), editable);
    setVisibleManaged(nodes.consultationActions(), editable);
    setVisibleManaged(nodes.prescriptionForm(), editable);
    setVisibleManaged(nodes.prescriptionActions(), editable);
    setVisibleManaged(nodes.completionActions(), editable);
    setVisibleManaged(nodes.saveConsultation(), editable);
    setVisibleManaged(nodes.addPrescription(), editable);
    setVisibleManaged(nodes.complete(), editable);
  }

  private static void setEditable(TextInputControl control, boolean editable) {
    control.setEditable(editable);
    if (editable) {
      control.getStyleClass().remove("read-only-field");
    } else if (!control.getStyleClass().contains("read-only-field")) {
      control.getStyleClass().add("read-only-field");
    }
  }

  private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
    node.setVisible(visible);
    node.setManaged(visible);
  }

  private static void selectDashboardAppointment(
      ClinicServices services,
      Session session,
      CalendarAppointment calendarAppointment,
      SelectionState selection,
      SelectionNodes nodes,
      Label feedback,
      Clock clock) {
    Optional<Appointment> resolvedAppointment = Optional.empty();
    Optional<Patient> resolvedPatient = Optional.empty();
    try {
      if (calendarAppointment != null) {
        Appointment appointment =
            services.appointmentService().get(calendarAppointment.appointmentId());
        if (appointment.doctorId() != session.accountId()) {
          throw new AuthorizationException("You are not allowed to view this appointment");
        }
        resolvedAppointment = Optional.of(appointment);
        resolvedPatient =
            Optional.of(
                services.patientService().getAdministrative(session, appointment.patientId()));
      }
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback, userMessage(exception, "Appointment is temporarily unavailable"));
    }
    Appointment appointment = resolvedAppointment.orElse(null);
    Patient patient = resolvedPatient.orElse(null);
    selection.appointmentId = appointment == null || patient == null ? 0 : appointment.id();
    selection.patientId = patient == null ? 0 : patient.id();
    updateSelectionState(selection, nodes, appointment, patient, clock);
    boolean clinicalLoaded =
        loadClinical(
            services,
            session,
            appointment,
            nodes.diagnosis(),
            nodes.consultationNotes(),
            nodes.followUpNotes(),
            nodes.prescriptions(),
            feedback);
    if (!clinicalLoaded
        && appointment != null
        && appointment.status() == AppointmentStatus.CHECKED_IN) {
      setClinicalEditable(nodes, false);
    }
  }

  private static boolean loadClinical(
      ClinicServices services,
      Session session,
      Appointment appointment,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Label feedback) {
    if (appointment == null) {
      clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
      return true;
    }
    clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
    try {
      Optional<ClinicalRecord> record =
          services.clinicalService().findForDoctor(session, appointment.id());
      if (record.isEmpty()) {
        clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
        return true;
      }
      ClinicalRecord value = record.orElseThrow();
      diagnosis.setText(value.diagnosis());
      consultationNotes.setText(value.consultationNotes());
      followUpNotes.setText(value.followUpNotes());
      prescriptions.setItems(
          FXCollections.observableArrayList(
              services.clinicalService().prescriptionsForDoctor(session, appointment.id())));
      return true;
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback, userMessage(exception, "Clinical information is temporarily unavailable"));
      return false;
    }
  }

  private static void clearClinical(
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions) {
    diagnosis.clear();
    consultationNotes.clear();
    followUpNotes.clear();
    prescriptions.setItems(FXCollections.observableArrayList());
  }

  private static void requireSelection(long id, String message) {
    if (id == 0) {
      throw new ValidationException(message);
    }
  }

  private static void clear(TextField... fields) {
    for (TextField field : fields) {
      field.clear();
    }
  }

  private static void run(Label feedback, UiOperation operation) {
    try {
      operation.run();
    } catch (ValidationException | AuthorizationException exception) {
      UiComponents.showError(feedback, exception.getMessage());
    } catch (SQLException exception) {
      UiComponents.showError(feedback, "The requested operation is temporarily unavailable");
    }
  }

  private static String userMessage(Exception exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }

  @FunctionalInterface
  private interface UiOperation {
    void run() throws SQLException;
  }

  private record SelectionNodes(
      Label summary,
      Label noSelection,
      VBox selectedContent,
      VBox patientDetailsCard,
      VBox appointmentActionsCard,
      VBox terminalStatusCard,
      Label terminalStatusMessage,
      Label clinicalReadOnly,
      VBox consultationCard,
      VBox prescriptionCard,
      VBox completionCard,
      HBox consultationActions,
      VBox prescriptionForm,
      HBox prescriptionActions,
      HBox completionActions,
      Button accept,
      Button decline,
      Button checkIn,
      Button reschedule,
      Button saveConsultation,
      Button addPrescription,
      Button complete,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Button viewPatientHistory) {
    // Record components define the complete selected-pane node bundle.
  }

  private static final class SelectionState {
    private long appointmentId;
    private long patientId;
  }
}
