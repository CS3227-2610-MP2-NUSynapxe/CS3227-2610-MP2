package nusynapxe.ui;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Owns Doctor consultation notes, prescriptions, and their database-backed actions. */
final class DoctorConsultationPanel {
  private static final String FIELD_SEPARATOR = " | ";

  private final ClinicServices services;
  private final Session session;
  private final Label feedback;
  private final ClinicTaskRunner taskRunner;
  private final LongSupplier appointmentId;
  private final LongSupplier selectionGeneration;
  private final TextField diagnosis = field("doctor-diagnosis", "Diagnosis");
  private final TextArea consultationNotes =
      textArea("doctor-consultation-notes", "Consultation notes");
  private final TextArea followUpNotes = textArea("doctor-follow-up", "Follow-up notes");
  private final Button saveConsultation =
      UiComponents.primaryButton("Save consultation", "doctor-consultation-save");
  private final TextField medication = field("doctor-medication", "Medication");
  private final TextField dosage = field("doctor-dosage", "Dosage");
  private final TextField frequency = field("doctor-frequency", "Frequency");
  private final TextField duration = field("doctor-duration", "Duration");
  private final TextField instructions = field("doctor-instructions", "Instructions");
  private final Button addPrescription =
      UiComponents.primaryButton("Add prescription", "doctor-prescription-submit");
  private final ListView<Prescription> prescriptions = new ListView<>();
  private final VBox prescriptionForm;
  private final HBox consultationActions;
  private final HBox prescriptionActions;
  private final VBox consultationCard;
  private final VBox prescriptionCard;
  private Consumer<Boolean> onClinicalLoaded = Objects::requireNonNull;

  DoctorConsultationPanel(
      ClinicServices services,
      Session session,
      Label feedback,
      ClinicTaskRunner taskRunner,
      LongSupplier appointmentId,
      LongSupplier selectionGeneration) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    this.appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
    this.selectionGeneration = Objects.requireNonNull(selectionGeneration, "selectionGeneration");
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
    prescriptionForm =
        new VBox(
            10,
            UiComponents.fieldGroup("Medication", medication),
            UiComponents.fieldGroup("Dosage", dosage),
            UiComponents.fieldGroup("Frequency", frequency),
            UiComponents.fieldGroup("Duration", duration),
            UiComponents.fieldGroup("Instructions", instructions));
    consultationActions = UiComponents.actionBar(saveConsultation);
    consultationCard =
        UiComponents.card(
            "doctor-consultation-card",
            UiComponents.sectionHeading("Consultation"),
            UiComponents.fieldGroup("Diagnosis", diagnosis),
            UiComponents.fieldGroup("Consultation notes", consultationNotes),
            UiComponents.fieldGroup("Follow-up notes", followUpNotes),
            consultationActions);
    prescriptionActions = UiComponents.actionBar(addPrescription);
    prescriptionCard =
        UiComponents.card(
            "doctor-prescription-card",
            UiComponents.sectionHeading("Prescriptions"),
            prescriptionForm,
            prescriptionActions,
            prescriptions);
    saveConsultation.setOnAction(event -> save());
    addPrescription.setOnAction(event -> submitPrescription());
  }

  void setOnClinicalLoaded(Consumer<Boolean> onClinicalLoaded) {
    this.onClinicalLoaded = Objects.requireNonNull(onClinicalLoaded, "onClinicalLoaded");
  }

  void load(Appointment appointment, long generation) {
    DoctorConsultationView.loadClinicalAsync(
        services,
        session,
        appointment,
        diagnosis,
        consultationNotes,
        followUpNotes,
        prescriptions,
        feedback,
        taskRunner,
        () -> generation == selectionGeneration.getAsLong(),
        onClinicalLoaded);
  }

  void clear() {
    setConsultationDisabled(false);
    DoctorConsultationView.clearClinical(
        diagnosis, consultationNotes, followUpNotes, prescriptions);
  }

  TextField diagnosisField() {
    return diagnosis;
  }

  TextArea consultationNotesArea() {
    return consultationNotes;
  }

  TextArea followUpNotesArea() {
    return followUpNotes;
  }

  TextField medicationField() {
    return medication;
  }

  TextField dosageField() {
    return dosage;
  }

  TextField frequencyField() {
    return frequency;
  }

  TextField durationField() {
    return duration;
  }

  TextField instructionsField() {
    return instructions;
  }

  ListView<Prescription> prescriptionsList() {
    return prescriptions;
  }

  VBox prescriptionFormView() {
    return prescriptionForm;
  }

  HBox consultationActionsBar() {
    return consultationActions;
  }

  HBox prescriptionActionsBar() {
    return prescriptionActions;
  }

  VBox consultationCardView() {
    return consultationCard;
  }

  VBox prescriptionCardView() {
    return prescriptionCard;
  }

  Button saveButton() {
    return saveConsultation;
  }

  Button addPrescriptionButton() {
    return addPrescription;
  }

  private void save() {
    if (saveConsultation.isDisable()) {
      return;
    }
    try {
      long selectedAppointmentId = requireAppointment();
      long generation = selectionGeneration.getAsLong();
      String diagnosisValue = diagnosis.getText();
      String consultationValue = consultationNotes.getText();
      String followUpValue = followUpNotes.getText();
      setConsultationDisabled(true);
      taskRunner.submit(
          () -> {
            services
                .clinicalService()
                .saveConsultation(
                    session,
                    selectedAppointmentId,
                    diagnosisValue,
                    consultationValue,
                    followUpValue);
            return services.appointmentService().get(selectedAppointmentId);
          },
          appointment -> {
            if (generation != selectionGeneration.getAsLong()) {
              return;
            }
            setConsultationDisabled(false);
            feedback.setText("Consultation saved");
            load(appointment, generation);
          },
          failure -> {
            if (generation == selectionGeneration.getAsLong()) {
              setConsultationDisabled(false);
            }
            showError(failure, "Consultation could not be saved");
          });
    } catch (ValidationException exception) {
      showError(exception, "Consultation could not be saved");
    }
  }

  private void setConsultationDisabled(boolean disabled) {
    saveConsultation.setDisable(disabled);
    diagnosis.setDisable(disabled);
    consultationNotes.setDisable(disabled);
    followUpNotes.setDisable(disabled);
  }

  private void submitPrescription() {
    if (addPrescription.isDisable()) {
      return;
    }
    try {
      long selectedAppointmentId = requireAppointment();
      long generation = selectionGeneration.getAsLong();
      String medicationValue = medication.getText();
      String dosageValue = dosage.getText();
      String frequencyValue = frequency.getText();
      String durationValue = duration.getText();
      String instructionsValue = instructions.getText();
      addPrescription.setDisable(true);
      taskRunner.submit(
          () -> {
            services
                .clinicalService()
                .addPrescription(
                    session,
                    selectedAppointmentId,
                    medicationValue,
                    dosageValue,
                    frequencyValue,
                    durationValue,
                    instructionsValue);
            return services.appointmentService().get(selectedAppointmentId);
          },
          appointment -> {
            addPrescription.setDisable(false);
            if (generation != selectionGeneration.getAsLong()) {
              return;
            }
            feedback.setText("Prescription added");
            clear(medication, dosage, frequency, duration, instructions);
            load(appointment, generation);
          },
          failure -> {
            addPrescription.setDisable(false);
            showError(failure, "Prescription could not be added");
          });
    } catch (RejectedExecutionException exception) {
      addPrescription.setDisable(false);
      showError(exception, "Prescription could not be added");
    } catch (ValidationException exception) {
      showError(exception, "Prescription could not be added");
    }
  }

  private long requireAppointment() {
    long selectedAppointmentId = appointmentId.getAsLong();
    if (selectedAppointmentId == 0) {
      throw new ValidationException("Select an appointment first");
    }
    return selectedAppointmentId;
  }

  private void showError(Throwable failure, String fallback) {
    UiComponents.showError(
        feedback, failure.getMessage() == null ? fallback : failure.getMessage());
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

  private static void clear(TextField... fields) {
    for (TextField field : fields) {
      field.clear();
    }
  }
}
