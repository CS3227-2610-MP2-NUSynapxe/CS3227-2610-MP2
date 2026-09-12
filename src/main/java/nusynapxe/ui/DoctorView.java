package nusynapxe.ui;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.ClinicalRecord;
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
    SelectionState selection = new SelectionState();

    TextField rescheduleStart = field("doctor-reschedule-start", DATE_TIME_PATTERN);
    TextField rescheduleEnd = field("doctor-reschedule-end", DATE_TIME_PATTERN);
    Button accept = UiComponents.primaryButton("Accept selected", "doctor-accept");
    Button checkIn = UiComponents.primaryButton("Check in selected", "doctor-check-in");
    Button reschedule = UiComponents.secondaryButton("Reschedule selected", "doctor-reschedule");

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
    Label noSelection =
        UiComponents.emptyState(
            "doctor-no-selection",
            "Select an appointment from the schedule to edit its consultation.");
    DoctorDashboardDayView[] dashboardHolder = new DoctorDashboardDayView[1];

    updateSelectionState(
        selection,
        selectionSummary,
        noSelection,
        null,
        checkIn,
        accept,
        reschedule,
        saveConsultation,
        addPrescription,
        complete);

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
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services
                      .appointmentService()
                      .reschedule(
                          session,
                          selection.appointmentId,
                          parseDateTime(rescheduleStart.getText(), "Start time"),
                          parseDateTime(rescheduleEnd.getText(), "End time"));
                  feedback.setText("Appointment rescheduled");
                  dashboardHolder[0].refresh();
                }));

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
                  loadClinical(
                      services,
                      session,
                      services.appointmentService().get(selection.appointmentId),
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback);
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
                  loadClinical(
                      services,
                      session,
                      services.appointmentService().get(selection.appointmentId),
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback);
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

    VBox scheduleActions =
        new VBox(
            10,
            UiComponents.actionBar(checkIn, accept),
            UiComponents.fieldGroup("Reschedule start", rescheduleStart),
            UiComponents.fieldGroup("Reschedule end", rescheduleEnd),
            UiComponents.actionBar(reschedule));

    VBox prescriptionForm =
        new VBox(
            10,
            UiComponents.fieldGroup("Medication", medication),
            UiComponents.fieldGroup("Dosage", dosage),
            UiComponents.fieldGroup("Frequency", frequency),
            UiComponents.fieldGroup("Duration", duration),
            UiComponents.fieldGroup("Instructions", instructions));

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
                    selectionSummary,
                    noSelection,
                    checkIn,
                    accept,
                    reschedule,
                    saveConsultation,
                    addPrescription,
                    complete,
                    diagnosis,
                    consultationNotes,
                    followUpNotes,
                    prescriptions,
                    feedback));
    VBox scheduleCard =
        UiComponents.card(
            "doctor-schedule-card",
            UiComponents.pageTitle("My day"),
            UiComponents.supportingText(
                "Select an appointment to open its clinical context. Manage blocked time in Calendar."),
            dashboardHolder[0].view());
    VBox consultationCard =
        UiComponents.card(
            "doctor-consultation-card",
            UiComponents.sectionHeading("Consultation"),
            UiComponents.fieldGroup("Diagnosis", diagnosis),
            UiComponents.fieldGroup("Consultation notes", consultationNotes),
            UiComponents.fieldGroup("Follow-up notes", followUpNotes),
            UiComponents.actionBar(saveConsultation));
    VBox prescriptionCard =
        UiComponents.card(
            "doctor-prescription-card",
            UiComponents.sectionHeading("Prescriptions"),
            prescriptionForm,
            UiComponents.actionBar(addPrescription),
            prescriptions);
    VBox completionCard =
        UiComponents.card(
            "doctor-completion-card",
            UiComponents.sectionHeading("Complete visit"),
            UiComponents.supportingText(
                "Complete the consultation when the clinical record is ready."),
            UiComponents.actionBar(complete));
    VBox detailContent =
        new VBox(
            16,
            noSelection,
            selectionSummary,
            UiComponents.card(
                "doctor-appointment-actions-card",
                UiComponents.sectionHeading("Appointment actions"),
                scheduleActions),
            consultationCard,
            prescriptionCard,
            completionCard);
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
            ignoredPatientId -> dashboardHolder[0].refresh());
    ScrollPane patientsPage = new ScrollPane(patientDirectory.view());
    patientsPage.setId("doctor-patients-page");
    patientsPage.setFitToWidth(true);

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
          patientDirectory.refresh();
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
      Label selectionSummary,
      Label noSelection,
      Appointment appointment,
      Button checkIn,
      Button... actions) {
    boolean selected = appointment != null && selection.appointmentId != 0;
    boolean checkInEligible =
        selected
            && appointment.status() == AppointmentStatus.ACCEPTED
            && !LocalDateTime.now(CalendarService.CLINIC_ZONE).isBefore(appointment.startsAt());
    checkIn.setDisable(!checkInEligible);
    for (Button action : actions) {
      action.setDisable(!selected);
    }
    selectionSummary.setVisible(selected);
    selectionSummary.setManaged(selected);
    noSelection.setVisible(!selected);
    noSelection.setManaged(!selected);
    if (selected) {
      selectionSummary.setText(
          "Selected appointment  •  Patient P"
              + String.format(Locale.ROOT, "%06d", appointment.patientId())
              + "  •  "
              + appointment.startsAt().format(DATE_TIME_FORMAT)
              + "  •  Status: "
              + displayStatus(appointment.status().name()));
    }
  }

  private static String displayStatus(String status) {
    return UiComponents.humanizeStatus(status);
  }

  @SuppressWarnings("PMD.ExcessiveParameterList")
  private static void selectDashboardAppointment(
      ClinicServices services,
      Session session,
      CalendarAppointment calendarAppointment,
      SelectionState selection,
      Label selectionSummary,
      Label noSelection,
      Button checkIn,
      Button accept,
      Button reschedule,
      Button saveConsultation,
      Button addPrescription,
      Button complete,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Label feedback) {
    Appointment appointment = null;
    try {
      if (calendarAppointment != null) {
        appointment = services.appointmentService().get(calendarAppointment.appointmentId());
        if (appointment.doctorId() != session.accountId()) {
          throw new AuthorizationException("You are not allowed to view this appointment");
        }
      }
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback, userMessage(exception, "Appointment is temporarily unavailable"));
      appointment = null;
    }
    selection.appointmentId = appointment == null ? 0 : appointment.id();
    updateSelectionState(
        selection,
        selectionSummary,
        noSelection,
        appointment,
        checkIn,
        accept,
        reschedule,
        saveConsultation,
        addPrescription,
        complete);
    loadClinical(
        services,
        session,
        appointment,
        diagnosis,
        consultationNotes,
        followUpNotes,
        prescriptions,
        feedback);
  }

  private static void loadClinical(
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
      return;
    }
    try {
      Optional<ClinicalRecord> record =
          services.clinicalService().findForDoctor(session, appointment.id());
      if (record.isEmpty()) {
        clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
        return;
      }
      ClinicalRecord value = record.orElseThrow();
      diagnosis.setText(value.diagnosis());
      consultationNotes.setText(value.consultationNotes());
      followUpNotes.setText(value.followUpNotes());
      prescriptions.setItems(
          FXCollections.observableArrayList(
              services.clinicalService().prescriptionsForDoctor(session, appointment.id())));
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback, userMessage(exception, "Clinical information is temporarily unavailable"));
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

  private static LocalDateTime parseDateTime(String value, String fieldName) {
    if (value == null) {
      throw new ValidationException(fieldName + " must use " + DATE_TIME_PATTERN);
    }
    try {
      return LocalDateTime.parse(value.trim(), DATE_TIME_FORMAT);
    } catch (DateTimeParseException exception) {
      throw new ValidationException(fieldName + " must use " + DATE_TIME_PATTERN, exception);
    }
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

  private static final class SelectionState {
    private long appointmentId;
  }
}
