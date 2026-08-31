package nusynapxe.ui;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the Doctor schedule and clinical consultation workspace. */
public final class DoctorView {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final String FIELD_SEPARATOR = " | ";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

  private DoctorView() {
    throw new AssertionError("Utility class");
  }

  /** Creates the Doctor workspace. */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    ListView<Appointment> appointments = new ListView<>();
    appointments.setId("doctor-appointment-list");
    appointments.setCellFactory(
        view ->
            new ListCell<>() {
              @Override
              protected void updateItem(Appointment appointment, boolean empty) {
                super.updateItem(appointment, empty);
                setText(
                    empty || appointment == null
                        ? null
                        : "Patient #"
                            + appointment.patientId()
                            + FIELD_SEPARATOR
                            + appointment.startsAt().format(DATE_TIME_FORMAT)
                            + " - "
                            + appointment.endsAt().format(DATE_TIME_FORMAT)
                            + FIELD_SEPARATOR
                            + appointment.status());
              }
            });
    SelectionState selection = new SelectionState();

    TextField rescheduleStart = field("doctor-reschedule-start", DATE_TIME_PATTERN);
    TextField rescheduleEnd = field("doctor-reschedule-end", DATE_TIME_PATTERN);
    Button accept = new Button("Accept selected");
    accept.setId("doctor-accept");
    Button reschedule = new Button("Reschedule selected");
    reschedule.setId("doctor-reschedule");
    Button refresh = new Button("Refresh schedule");
    refresh.setId("doctor-refresh");

    TextField timeOffStart = field("doctor-timeoff-start", DATE_TIME_PATTERN);
    TextField timeOffEnd = field("doctor-timeoff-end", DATE_TIME_PATTERN);
    Button blockTimeOff = new Button("Block time off");
    blockTimeOff.setId("doctor-timeoff-submit");

    TextField diagnosis = field("doctor-diagnosis", "Diagnosis");
    TextArea consultationNotes = textArea("doctor-consultation-notes", "Consultation notes");
    TextArea followUpNotes = textArea("doctor-follow-up", "Follow-up notes");
    Button saveConsultation = new Button("Save consultation");
    saveConsultation.setId("doctor-consultation-save");

    TextField medication = field("doctor-medication", "Medication");
    TextField dosage = field("doctor-dosage", "Dosage");
    TextField frequency = field("doctor-frequency", "Frequency");
    TextField duration = field("doctor-duration", "Duration");
    TextField instructions = field("doctor-instructions", "Instructions");
    Button addPrescription = new Button("Add prescription");
    addPrescription.setId("doctor-prescription-submit");
    ListView<Prescription> prescriptions = new ListView<>();
    prescriptions.setId("doctor-prescription-list");
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
                            + prescription.duration());
              }
            });
    Button complete = new Button("Mark consultation completed");
    complete.setId("doctor-complete");
    Label feedback = new Label();
    feedback.setId("doctor-feedback");

    appointments
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              selection.appointmentId = selected == null ? 0 : selected.id();
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

    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(event -> onLogout.run());
    HBox header = new HBox(12, new Label("DOCTOR workspace"), refresh, logout);

    GridPane scheduleActions = new GridPane();
    scheduleActions.setHgap(8);
    scheduleActions.setVgap(8);
    scheduleActions.addRow(0, accept);
    scheduleActions.addRow(1, new Label("Start"), rescheduleStart, new Label("End"), rescheduleEnd);
    scheduleActions.addRow(2, reschedule);

    GridPane timeOffForm = new GridPane();
    timeOffForm.setHgap(8);
    timeOffForm.setVgap(8);
    timeOffForm.addRow(0, new Label("Start"), timeOffStart, new Label("End"), timeOffEnd);
    timeOffForm.addRow(1, blockTimeOff);

    GridPane prescriptionForm = new GridPane();
    prescriptionForm.setHgap(8);
    prescriptionForm.setVgap(8);
    prescriptionForm.setVgap(8);
    prescriptionForm.addRow(0, new Label("Medication"), medication, new Label("Dosage"), dosage);
    prescriptionForm.addRow(1, new Label("Frequency"), frequency, new Label("Duration"), duration);
    prescriptionForm.addRow(2, new Label("Instructions"), instructions, addPrescription);

    VBox content =
        new VBox(
            12,
            new Label("My appointment schedule"),
            appointments,
            scheduleActions,
            new Label("Availability"),
            timeOffForm,
            new Label("Consultation"),
            new Label("Diagnosis"),
            diagnosis,
            new Label("Consultation notes"),
            consultationNotes,
            new Label("Follow-up notes"),
            followUpNotes,
            saveConsultation,
            prescriptionForm,
            prescriptions,
            complete,
            feedback);
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    BorderPane root = new BorderPane(scroll);
    root.setId("doctor-workspace");
    root.setPadding(new Insets(24));
    root.setTop(header);
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
