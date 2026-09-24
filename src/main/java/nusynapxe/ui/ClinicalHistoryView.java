package nusynapxe.ui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import nusynapxe.domain.ClinicalHistoryEntry;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/** Builds the Doctor-only, read-only consultation history view. */
final class ClinicalHistoryView {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final String FIELD_SEPARATOR = " | ";
  private static final double HISTORY_LIST_MIN_HEIGHT = 180.0;
  private static final double HISTORY_LIST_PREF_HEIGHT = 220.0;
  private static final double PRESCRIPTION_LIST_MIN_HEIGHT = 96.0;
  private static final double PRESCRIPTION_LIST_PREF_HEIGHT = 120.0;
  private static final int READ_ONLY_TEXT_AREA_ROWS = 4;
  private static final double READ_ONLY_TEXT_AREA_MIN_HEIGHT = 72.0;

  private final ClinicServices services;
  private final Session session;
  private final ClinicTaskRunner taskRunner;
  private final Label workspaceFeedback;
  private final SearchSuggestionField<Patient> patientSelector;
  private final Label state;
  private final ListView<ClinicalHistoryEntry> historyList;
  private final VBox detailContent;
  private final VBox root;
  private long patientGeneration;
  private long historyGeneration;
  private boolean disposed;

  private ClinicalHistoryView(
      ClinicServices services,
      Session session,
      Label workspaceFeedback,
      ClinicTaskRunner taskRunner) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.workspaceFeedback = workspaceFeedback;
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");

    patientSelector = PatientDirectoryView.patientSearchField("doctor-history-patient");
    patientSelector.setAccessibleRoleDescription("Patient selector");
    patientSelector.setMaxWidth(Double.MAX_VALUE);
    patientSelector.setId("doctor-history-patient-field");
    state = UiComponents.emptyState("doctor-history-state", "Select a patient to view history.");
    state.setAccessibleRoleDescription("Clinical history status");
    javafx.scene.control.Button load =
        UiComponents.primaryButton("View history", "doctor-history-load");
    javafx.scene.control.Button clear =
        UiComponents.secondaryButton("Clear", "doctor-history-clear");
    load.setOnAction(event -> loadSelectedPatient());
    clear.setOnAction(
        event -> {
          historyGeneration++;
          patientSelector.clearSelection();
          clearHistory();
          state.setText("Select a patient to view completed consultation history.");
        });

    historyList = new ListView<>();
    historyList.setId("doctor-history-list");
    historyList.setAccessibleRoleDescription("Completed consultation history");
    historyList.setMinHeight(HISTORY_LIST_MIN_HEIGHT);
    historyList.setPrefHeight(HISTORY_LIST_PREF_HEIGHT);
    historyList.setPlaceholder(
        UiComponents.emptyState("doctor-history-empty", "No completed consultations found."));
    historyList.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(ClinicalHistoryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(
                    empty || entry == null
                        ? null
                        : entry.appointment().startsAt().format(DATE_TIME_FORMAT)
                            + FIELD_SEPARATOR
                            + UiComponents.humanizeStatus(entry.appointment().status().name())
                            + FIELD_SEPARATOR
                            + entry.doctorName());
              }
            });
    historyList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((observable, previous, selected) -> showDetail(selected));
    VBox.setVgrow(historyList, Priority.ALWAYS);

    detailContent = new VBox(10);
    detailContent.setId("doctor-history-detail");
    detailContent
        .getChildren()
        .add(
            UiComponents.emptyState(
                "doctor-history-detail-empty",
                "Select a completed consultation to view its details."));

    HBox patientActions = UiComponents.actionBar(load, clear);
    VBox content =
        new VBox(
            12,
            UiComponents.fieldGroup("Patient", patientSelector),
            patientActions,
            state,
            historyList,
            UiComponents.sectionHeading("Selected consultation"),
            detailContent);
    content.setId("doctor-history-content");
    VBox card =
        UiComponents.card(
            "doctor-clinical-history-card",
            UiComponents.pageTitle("Consultation History"),
            UiComponents.supportingText(
                "Completed consultations for any patient are available to Doctors as read-only history."),
            content);
    root = new VBox(card);
    root.setId("doctor-clinical-history-view");
    root.setMaxHeight(Double.MAX_VALUE);
    VBox.setVgrow(card, Priority.ALWAYS);
    refreshPatients();
  }

  /** Creates the shared history view for an authenticated Doctor. */
  static ClinicalHistoryView create(
      ClinicServices services, Session session, Label workspaceFeedback) {
    return new ClinicalHistoryView(
        services, session, workspaceFeedback, ClinicTaskRunner.immediate());
  }

  /** Creates the history view with an injected database task runner. */
  static ClinicalHistoryView create(
      ClinicServices services,
      Session session,
      Label workspaceFeedback,
      ClinicTaskRunner taskRunner) {
    return new ClinicalHistoryView(services, session, workspaceFeedback, taskRunner);
  }

  /** Returns the view root for embedding in the Doctor workspace. */
  Parent view() {
    return root;
  }

  /** Reloads the patient selector without changing the selected history entry. */
  void refreshPatients() {
    patientGeneration++;
    long generation = patientGeneration;
    submit(
        () -> services.patientService().searchAdministrative(session, ""),
        patients -> {
          if (!disposed && generation == patientGeneration) {
            patientSelector.setItems(FXCollections.observableArrayList(patients));
          }
        },
        failure -> {
          if (!disposed && generation == patientGeneration) {
            showError("Patients are temporarily unavailable");
          }
        });
  }

  /** Opens history for a patient selected from another Doctor workflow. */
  void showPatient(long patientId) {
    historyGeneration++;
    long generation = historyGeneration;
    patientSelector.clearSelection();
    clearHistory();
    state.setText("Loading consultation history...");
    submit(
        () -> services.patientService().getAdministrative(session, patientId),
        patient -> {
          if (disposed || generation != historyGeneration) {
            return;
          }
          if (patientSelector.getItems().stream().noneMatch(value -> value.id() == patient.id())) {
            refreshPatients();
          }
          patientSelector.select(patient);
          loadHistory(patient);
        },
        failure -> {
          if (!disposed && generation == historyGeneration) {
            showError(userMessage(failure, "Patient history is temporarily unavailable"));
          }
        });
  }

  private void loadSelectedPatient() {
    Patient patient = patientSelector.getValue();
    if (patient == null) {
      state.setText("Select a patient before loading consultation history.");
      clearHistory();
      return;
    }
    loadHistory(patient);
  }

  private void loadHistory(Patient patient) {
    historyGeneration++;
    long generation = historyGeneration;
    state.setText("Loading consultation history...");
    clearHistory();
    submit(
        () -> services.clinicalService().historyForDoctor(session, patient.id()),
        history -> {
          if (disposed || generation != historyGeneration) {
            return;
          }
          historyList.setItems(FXCollections.observableArrayList(history));
          if (history.isEmpty()) {
            state.setText("No completed consultations found for this patient.");
          } else {
            state.setText(history.size() + " completed consultation(s) found.");
            historyList.getSelectionModel().selectFirst();
          }
        },
        failure -> {
          if (!disposed && generation == historyGeneration) {
            showError("Consultation history is temporarily unavailable");
          }
        });
  }

  /** Prevents callbacks from applying after the Doctor workspace is discarded. */
  void dispose() {
    disposed = true;
    patientGeneration++;
    historyGeneration++;
    clearHistory();
  }

  private <T> void submit(
      ClinicTaskRunner.ClinicTask<T> task,
      java.util.function.Consumer<T> onSuccess,
      java.util.function.Consumer<Throwable> onFailure) {
    try {
      taskRunner.submit(task, onSuccess, onFailure);
    } catch (RejectedExecutionException exception) {
      onFailure.accept(exception);
    }
  }

  private void showDetail(ClinicalHistoryEntry entry) {
    if (entry == null) {
      detailContent
          .getChildren()
          .setAll(
              UiComponents.emptyState(
                  "doctor-history-detail-empty",
                  "Select a completed consultation to view its details."));
      return;
    }
    TextArea diagnosis =
        readOnlyArea("doctor-history-diagnosis", entry.clinicalRecord().diagnosis());
    TextArea consultationNotes =
        readOnlyArea(
            "doctor-history-consultation-notes", entry.clinicalRecord().consultationNotes());
    TextArea followUp =
        readOnlyArea("doctor-history-follow-up", entry.clinicalRecord().followUpNotes());
    ListView<Prescription> prescriptions = prescriptionList(entry.prescriptions());
    detailContent
        .getChildren()
        .setAll(
            UiComponents.supportingText(
                "Appointment "
                    + entry.appointment().id()
                    + "  •  "
                    + entry.appointment().startsAt().format(DATE_TIME_FORMAT)
                    + "  •  "
                    + UiComponents.humanizeStatus(entry.appointment().status().name())),
            UiComponents.supportingText("Assigned Doctor: " + entry.doctorName()),
            UiComponents.fieldGroup("Diagnosis", diagnosis),
            UiComponents.fieldGroup("Consultation notes", consultationNotes),
            UiComponents.fieldGroup("Follow-up notes", followUp),
            UiComponents.sectionHeading("Prescriptions"),
            prescriptions);
  }

  private ListView<Prescription> prescriptionList(List<Prescription> values) {
    ListView<Prescription> prescriptions = new ListView<>();
    prescriptions.setId("doctor-history-prescription-list");
    prescriptions.setAccessibleRoleDescription("Historical prescriptions");
    prescriptions.setMinHeight(PRESCRIPTION_LIST_MIN_HEIGHT);
    prescriptions.setPrefHeight(PRESCRIPTION_LIST_PREF_HEIGHT);
    prescriptions.setPlaceholder(
        UiComponents.emptyState("doctor-history-prescription-empty", "No prescriptions recorded."));
    prescriptions.setItems(FXCollections.observableArrayList(values));
    prescriptions.setCellFactory(
        ignored ->
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
    return prescriptions;
  }

  private void clearHistory() {
    historyList.getSelectionModel().clearSelection();
    historyList.setItems(FXCollections.observableArrayList());
    showDetail(null);
  }

  private void showError(String message) {
    state.setText(message);
    UiComponents.showError(workspaceFeedback, message);
    clearHistory();
  }

  private static TextArea readOnlyArea(String id, String text) {
    TextArea area = new TextArea(text == null ? "" : text);
    area.setId(id);
    area.setEditable(false);
    area.setWrapText(true);
    area.setPrefRowCount(READ_ONLY_TEXT_AREA_ROWS);
    area.setMinHeight(READ_ONLY_TEXT_AREA_MIN_HEIGHT);
    area.setFocusTraversable(false);
    area.getStyleClass().add("read-only-field");
    return area;
  }

  private static String userMessage(Throwable exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }
}
