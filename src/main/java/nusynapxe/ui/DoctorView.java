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
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.DoctorTimeOff;
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
  private static final DateTimeFormatter SHORT_DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("MMM d HH:mm");

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
    ListView<Appointment> appointments = new ListView<>();
    appointments.setId("doctor-appointment-list");
    appointments.setPlaceholder(
        UiComponents.emptyState(
            "doctor-appointment-empty", "No appointments are assigned to you."));
    appointments.setCellFactory(
        view ->
            new ListCell<>() {
              @Override
              protected void updateItem(Appointment appointment, boolean empty) {
                super.updateItem(appointment, empty);
                setText(
                    empty || appointment == null
                        ? null
                        : String.format(
                            Locale.ROOT,
                            "P%06d  •  %s%n%s",
                            appointment.patientId(),
                            appointment.startsAt().format(SHORT_DATE_TIME_FORMAT),
                            displayStatus(appointment.status().name())));
                setWrapText(true);
                setMaxWidth(Double.MAX_VALUE);
                if (empty || appointment == null) {
                  clearRecordStatus(this);
                } else {
                  applyRecordStatus(this, appointment.status().name());
                }
              }
            });
    SelectionState selection = new SelectionState();

    TextField rescheduleStart = field("doctor-reschedule-start", DATE_TIME_PATTERN);
    TextField rescheduleEnd = field("doctor-reschedule-end", DATE_TIME_PATTERN);
    Button accept = UiComponents.primaryButton("Accept selected", "doctor-accept");
    Button checkIn = UiComponents.primaryButton("Check in selected", "doctor-check-in");
    Button reschedule = UiComponents.secondaryButton("Reschedule selected", "doctor-reschedule");
    Button refresh = UiComponents.secondaryButton("Refresh schedule", "doctor-refresh");

    TextField timeOffStart = field("doctor-timeoff-start", DATE_TIME_PATTERN);
    TextField timeOffEnd = field("doctor-timeoff-end", DATE_TIME_PATTERN);
    Button blockTimeOff = UiComponents.secondaryButton("Block time off", "doctor-timeoff-submit");

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

    appointments
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              selection.appointmentId = selected == null ? 0 : selected.id();
              updateSelectionState(
                  selection,
                  selectionSummary,
                  noSelection,
                  selected,
                  checkIn,
                  accept,
                  reschedule,
                  saveConsultation,
                  addPrescription,
                  complete);
              loadClinical(
                  services,
                  session,
                  selected,
                  diagnosis,
                  consultationNotes,
                  followUpNotes,
                  prescriptions,
                  feedback);
            });

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

    refresh.setOnAction(
        event ->
            refreshSchedule(
                services,
                session,
                appointments,
                selection,
                diagnosis,
                consultationNotes,
                followUpNotes,
                prescriptions,
                feedback));

    accept.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services.appointmentService().accept(session, selection.appointmentId);
                  feedback.setText("Appointment accepted");
                  refreshSchedule(
                      services,
                      session,
                      appointments,
                      selection,
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback);
                }));

    checkIn.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
                  services.appointmentService().checkIn(session, selection.appointmentId);
                  feedback.setText("Patient checked in");
                  refreshSchedule(
                      services,
                      session,
                      appointments,
                      selection,
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback);
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
                  refreshSchedule(
                      services,
                      session,
                      appointments,
                      selection,
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback);
                }));

    blockTimeOff.setOnAction(
        event ->
            run(
                feedback,
                () -> {
                  DoctorTimeOff timeOff =
                      services
                          .appointmentService()
                          .blockTimeOff(
                              session,
                              parseDateTime(timeOffStart.getText(), "Time-off start"),
                              parseDateTime(timeOffEnd.getText(), "Time-off end"));
                  feedback.setText(
                      "Time off blocked from "
                          + timeOff.startsAt().format(DATE_TIME_FORMAT)
                          + " to "
                          + timeOff.endsAt().format(DATE_TIME_FORMAT));
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
                  refreshSchedule(
                      services,
                      session,
                      appointments,
                      selection,
                      diagnosis,
                      consultationNotes,
                      followUpNotes,
                      prescriptions,
                      feedback);
                }));

    DoctorCalendarView[] calendarHolder = new DoctorCalendarView[1];
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(
        event -> {
          if (calendarHolder[0] != null) {
            calendarHolder[0].dispose();
          }
          onLogout.run();
        });
    HBox header = UiComponents.workspaceHeader("DOCTOR workspace", session.username(), logout);

    VBox scheduleActions =
        new VBox(
            10,
            UiComponents.actionBar(accept),
            UiComponents.fieldGroup("Reschedule start", rescheduleStart),
            UiComponents.fieldGroup("Reschedule end", rescheduleEnd),
            UiComponents.actionBar(reschedule));

    VBox timeOffForm =
        new VBox(
            10,
            UiComponents.fieldGroup("Time-off start", timeOffStart),
            UiComponents.fieldGroup("Time-off end", timeOffEnd));

    VBox prescriptionForm =
        new VBox(
            10,
            UiComponents.fieldGroup("Medication", medication),
            UiComponents.fieldGroup("Dosage", dosage),
            UiComponents.fieldGroup("Frequency", frequency),
            UiComponents.fieldGroup("Duration", duration),
            UiComponents.fieldGroup("Instructions", instructions));

    VBox scheduleCard =
        UiComponents.card(
            "doctor-schedule-card",
            UiComponents.pageTitle("My appointment schedule"),
            UiComponents.supportingText("Select a visit to open its clinical context."),
            UiComponents.actionBar(refresh),
            UiComponents.actionBar(checkIn),
            appointments,
            scheduleActions);
    appointments.setPrefHeight(420);

    VBox availabilityCard =
        UiComponents.card(
            "doctor-availability-card",
            UiComponents.sectionHeading("Availability"),
            UiComponents.supportingText("Block a time interval so new bookings cannot overlap it."),
            timeOffForm,
            UiComponents.actionBar(blockTimeOff));
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
            availabilityCard,
            consultationCard,
            prescriptionCard,
            completionCard);
    detailContent.setId("doctor-detail-pane");
    ScrollPane scheduleScroll = new ScrollPane(scheduleCard);
    scheduleScroll.setId("doctor-schedule-scroll");
    scheduleScroll.setFitToWidth(true);
    ScrollPane detailScroll = new ScrollPane(detailContent);
    detailScroll.setId("doctor-detail-scroll");
    detailScroll.setFitToWidth(true);
    SplitPane masterDetail = new SplitPane(scheduleScroll, detailScroll);
    masterDetail.setId("doctor-master-detail");
    masterDetail.setDividerPositions(0.36);

    PatientDirectoryView patientDirectory =
        PatientDirectoryView.create(
            services,
            session,
            "doctor",
            feedback,
            ignoredPatientId ->
                refreshSchedule(
                    services,
                    session,
                    appointments,
                    selection,
                    diagnosis,
                    consultationNotes,
                    followUpNotes,
                    prescriptions,
                    feedback));
    ScrollPane patientsPage = new ScrollPane(patientDirectory.view());
    patientsPage.setId("doctor-patients-page");
    patientsPage.setFitToWidth(true);

    StackPane pages = new StackPane(masterDetail);
    pages.setId("doctor-page-content");
    Button dashboardNavigation = UiComponents.secondaryButton("Dashboard", "doctor-nav-dashboard");
    Button patientsNavigation = UiComponents.secondaryButton("Patients", "doctor-nav-patients");
    Button calendarNavigation = UiComponents.secondaryButton("Calendar", "doctor-nav-calendar");
    dashboardNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
    VBox navigation =
        new VBox(
            0,
            new Label("Navigation"),
            dashboardNavigation,
            patientsNavigation,
            calendarNavigation);
    navigation.setId("doctor-navigation");
    DoctorCalendarSettingsView[] settingsHolder = new DoctorCalendarSettingsView[1];
    Runnable showCalendar =
        () -> {
          calendarHolder[0].show();
          pages.getChildren().setAll(calendarHolder[0].view());
          calendarNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          dashboardNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        };
    Runnable showSettings =
        () -> {
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
          pages.getChildren().setAll(masterDetail);
          dashboardNavigation.getStyleClass().add(ACTIVE_NAVIGATION_STYLE);
          patientsNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
          calendarNavigation.getStyleClass().remove(ACTIVE_NAVIGATION_STYLE);
        });
    patientsNavigation.setOnAction(
        event -> {
          calendarHolder[0].hide();
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
    root.setBottom(feedback);
    refreshSchedule(
        services,
        session,
        appointments,
        selection,
        diagnosis,
        consultationNotes,
        followUpNotes,
        prescriptions,
        feedback);
    return root;
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

  private static void applyRecordStatus(ListCell<?> cell, String status) {
    clearRecordStatus(cell);
    cell.getStyleClass()
        .add(
            "record-status-"
                + status.toLowerCase(java.util.Locale.ROOT).replace('_', '-').replace(' ', '-'));
  }

  private static void clearRecordStatus(ListCell<?> cell) {
    cell.getStyleClass().removeIf(style -> style.startsWith("record-status-"));
  }

  private static String displayStatus(String status) {
    return UiComponents.humanizeStatus(status);
  }

  private static void refreshSchedule(
      ClinicServices services,
      Session session,
      ListView<Appointment> appointments,
      SelectionState selection,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Label feedback) {
    try {
      appointments.setItems(
          FXCollections.observableArrayList(
              services.appointmentService().schedule(session, session.accountId())));
      selectAppointment(appointments, selection.appointmentId);
      Appointment selected = appointments.getSelectionModel().getSelectedItem();
      loadClinical(
          services,
          session,
          selected,
          diagnosis,
          consultationNotes,
          followUpNotes,
          prescriptions,
          feedback);
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      feedback.setText(userMessage(exception, "Schedule is temporarily unavailable"));
    }
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
      feedback.setText(userMessage(exception, "Clinical information is temporarily unavailable"));
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

  private static void selectAppointment(ListView<Appointment> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).id() == id) {
        list.getSelectionModel().select(index);
        return;
      }
    }
    if (id == 0 && !list.getItems().isEmpty()) {
      list.getSelectionModel().selectFirst();
    }
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
      feedback.setText(exception.getMessage());
    } catch (SQLException exception) {
      feedback.setText("The requested operation is temporarily unavailable");
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
