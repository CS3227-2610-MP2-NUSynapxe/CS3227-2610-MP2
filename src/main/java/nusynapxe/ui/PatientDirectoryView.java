package nusynapxe.ui;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.LongConsumer;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the shared administrative patient directory for Doctors and Receptionists. */
final class PatientDirectoryView {
  private final ClinicServices services;
  private final Session session;
  private final String prefix;
  private final Label workspaceFeedback;
  private final LongConsumer onPatientChanged;
  private final Clock clock;
  private final ClinicTaskRunner taskRunner;
  private final TextField patientSearch;
  private final TableView<Patient> patientTable;
  private final Label pageTitle;
  private final VBox directoryContent;
  private final VBox registrationContent;
  private final VBox viewingContent;
  private final VBox editingContent;
  private final VBox root;
  private long preferredPatientId;
  private long refreshGeneration;
  private long patientViewGeneration;
  private boolean disposed;

  private PatientDirectoryView(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged,
      Runnable onClinicalHistoryRequested,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.prefix = requirePrefix(prefix);
    this.workspaceFeedback = Objects.requireNonNull(workspaceFeedback, "workspaceFeedback");
    this.onPatientChanged = Objects.requireNonNull(onPatientChanged, "onPatientChanged");
    this.clock = ClinicClock.withClinicZone(clock);
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");

    PatientDirectoryFormView.PatientForm registerForm =
        PatientDirectoryFormView.createForm(prefix + "-register", false, this.clock);
    Button register = button("Register patient", prefix + "-patient-register");
    patientSearch = field(prefix + "-patient-search", "Search by name, NRIC/FIN, phone, or email");
    Button searchPatients = button("Search patients", prefix + "-patient-search-submit");
    Button clearPatientSearch = button("Clear search", prefix + "-patient-search-clear");
    patientTable = PatientDirectoryTableView.create(prefix, this::showPatientView);
    long[] registrationGeneration = {0};
    Button cancelRegistration = button("Cancel", prefix + "-patient-register-cancel");
    register.setOnAction(
        event -> {
          if (register.isDisable()) {
            return;
          }
          try {
            Patient draft = PatientDirectoryFormView.fromForm(registerForm, 0, true);
            registrationGeneration[0]++;
            long generation = registrationGeneration[0];
            register.setDisable(true);
            cancelRegistration.setDisable(true);
            taskRunner.submit(
                () -> services.patientService().register(session, draft),
                patient -> {
                  register.setDisable(false);
                  cancelRegistration.setDisable(false);
                  if (generation != registrationGeneration[0]) {
                    refresh();
                    return;
                  }
                  PatientDirectoryFormView.clear(registerForm);
                  patientSearch.clear();
                  showDirectory();
                  UiComponents.showMessage(workspaceFeedback, "Patient registered");
                  refresh();
                  handlePatientChanged(patient.id());
                },
                failure -> {
                  register.setDisable(false);
                  cancelRegistration.setDisable(false);
                  showTaskError(
                      workspaceFeedback,
                      failure,
                      "Patient registration is temporarily unavailable");
                });
          } catch (ValidationException exception) {
            UiComponents.showError(workspaceFeedback, exception.getMessage());
          }
        });
    searchPatients.setOnAction(event -> refresh());
    clearPatientSearch.setOnAction(
        event -> {
          patientSearch.clear();
          refresh();
        });

    patientSearch.setPrefWidth(420);
    patientSearch.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(patientSearch, Priority.ALWAYS);
    HBox patientSearchBar = new HBox(8, patientSearch, searchPatients, clearPatientSearch);
    patientSearchBar.setId(prefix + "-patient-search-bar");
    patientSearchBar.getStyleClass().add("patient-directory-search-bar");
    Button openRegistration = button("Register new patient", prefix + "-patient-open-register");
    HBox directoryActions;
    if (onClinicalHistoryRequested == null) {
      directoryActions = UiComponents.actionBar(openRegistration);
    } else {
      Button openClinicalHistory =
          button("Consultation history", prefix + "-patient-clinical-history");
      openClinicalHistory.setOnAction(event -> onClinicalHistoryRequested.run());
      directoryActions = UiComponents.actionBar(openRegistration, openClinicalHistory);
    }
    directoryContent = new VBox(10, patientSearchBar, patientTable, directoryActions);
    directoryContent.setId(prefix + "-patient-directory-view");
    directoryContent.setMaxHeight(Double.MAX_VALUE);
    VBox.setVgrow(patientTable, Priority.ALWAYS);

    HBox registrationActions = new HBox(8, register, cancelRegistration);
    registrationContent =
        new VBox(
            10,
            UiComponents.card(
                prefix + "-patient-register-card",
                PatientDirectoryFormView.grid(registerForm, false),
                registrationActions));
    registrationContent.setId(prefix + "-patient-register-view");
    viewingContent = new VBox(10);
    viewingContent.setId(prefix + "-patient-view");
    editingContent = new VBox(10);
    editingContent.setId(prefix + "-patient-edit-view");
    openRegistration.setOnAction(
        event -> {
          registrationGeneration[0]++;
          register.setDisable(false);
          cancelRegistration.setDisable(false);
          PatientDirectoryFormView.clear(registerForm);
          showRegistration();
        });
    cancelRegistration.setOnAction(
        event -> {
          if (cancelRegistration.isDisable()) {
            return;
          }
          registrationGeneration[0]++;
          PatientDirectoryFormView.clear(registerForm);
          showDirectory();
          refresh();
        });

    StackPane patientContent =
        new StackPane(directoryContent, registrationContent, viewingContent, editingContent);
    patientContent.setId(prefix + "-patient-content");
    pageTitle = UiComponents.pageTitle("Patient Directory");
    VBox directoryCard =
        UiComponents.card(prefix + "-patient-directory-card", pageTitle, patientContent);
    directoryCard.getStyleClass().add("patient-directory-page");
    VBox.setVgrow(patientContent, Priority.ALWAYS);
    root = new VBox(directoryCard);
    root.setId(prefix + "-patient-directory");
    root.setMaxHeight(Double.MAX_VALUE);
    VBox.setVgrow(directoryCard, Priority.ALWAYS);
    showDirectory();
  }

  /** Creates a patient directory with a role-specific semantic-control prefix. */
  static PatientDirectoryView create(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged) {
    return create(
        services,
        session,
        prefix,
        workspaceFeedback,
        onPatientChanged,
        null,
        ClinicClock.system(),
        ClinicTaskRunner.immediate());
  }

  /** Creates a directory with an optional Doctor clinical-history action. */
  static PatientDirectoryView create(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged,
      Runnable onClinicalHistoryRequested) {
    return create(
        services,
        session,
        prefix,
        workspaceFeedback,
        onPatientChanged,
        onClinicalHistoryRequested,
        ClinicClock.system(),
        ClinicTaskRunner.immediate());
  }

  /** Creates a directory with an injectable clinic clock. */
  static PatientDirectoryView create(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged,
      Runnable onClinicalHistoryRequested,
      Clock clock) {
    return create(
        services,
        session,
        prefix,
        workspaceFeedback,
        onPatientChanged,
        onClinicalHistoryRequested,
        clock,
        ClinicTaskRunner.immediate());
  }

  /** Creates a directory with an injectable clock and database task runner. */
  static PatientDirectoryView create(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged,
      Runnable onClinicalHistoryRequested,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    PatientDirectoryView view =
        new PatientDirectoryView(
            services,
            session,
            prefix,
            workspaceFeedback,
            onPatientChanged,
            onClinicalHistoryRequested,
            clock,
            taskRunner);
    view.refresh();
    return view;
  }

  /** Returns the directory content for embedding in a workspace. */
  Parent view() {
    return root;
  }

  /** Prevents further directory work after the owning workspace is discarded. */
  void dispose() {
    disposed = true;
    refreshGeneration++;
    patientViewGeneration++;
  }

  private void showDirectory() {
    patientViewGeneration++;
    pageTitle.setText("Patient Directory");
    setPage(directoryContent, true);
    setPage(registrationContent, false);
    setPage(viewingContent, false);
    setPage(editingContent, false);
  }

  private void showRegistration() {
    patientViewGeneration++;
    pageTitle.setText("Register new patient");
    setPage(directoryContent, false);
    setPage(registrationContent, true);
    setPage(viewingContent, false);
    setPage(editingContent, false);
  }

  private void showViewing() {
    pageTitle.setText("Patient details");
    setPage(directoryContent, false);
    setPage(registrationContent, false);
    setPage(viewingContent, true);
    setPage(editingContent, false);
  }

  private void showEditing() {
    patientViewGeneration++;
    pageTitle.setText("Edit patient");
    setPage(directoryContent, false);
    setPage(registrationContent, false);
    setPage(viewingContent, false);
    setPage(editingContent, true);
  }

  private static void setPage(VBox page, boolean visible) {
    page.setManaged(visible);
    page.setVisible(visible);
  }

  /** Reloads directory results using the current search query. */
  void refresh() {
    if (disposed) {
      return;
    }
    refreshGeneration++;
    long generation = refreshGeneration;
    String query = patientSearch.getText();
    taskRunner.submit(
        () -> services.patientService().searchAdministrative(session, query),
        patients -> {
          if (disposed || generation != refreshGeneration) {
            return;
          }
          patientTable.setItems(FXCollections.observableArrayList(patients));
          patientTable.getSelectionModel().clearSelection();
          int visibleRows = Math.min(Math.max(patientTable.getItems().size(), 1), 5);
          patientTable.setPrefHeight(44 + visibleRows * 52);
        },
        failure -> {
          if (disposed || generation != refreshGeneration) {
            return;
          }
          UiComponents.showError(workspaceFeedback, "Patients are temporarily unavailable");
          patientTable.setItems(FXCollections.observableArrayList());
        });
  }

  /** Returns the patient most recently selected for a related workflow. */
  long selectedPatientId() {
    return preferredPatientId;
  }

  /** Creates a patient search field for appointment workflows. */
  static SearchSuggestionField<Patient> patientSearchField(String id) {
    return new SearchSuggestionField<>(
        id,
        "Search by name or NRIC/FIN",
        PatientDirectoryView::patientOptionLabel,
        patient ->
            String.join(
                " ",
                patient.displayedId(),
                fullName(patient),
                valueOrEmpty(patient.identityType()),
                valueOrEmpty(patient.identityNumber()),
                displayPhone(patient),
                valueOrEmpty(patient.email())));
  }

  private void showPatientView(Patient selected) {
    if (selected == null) {
      return;
    }
    preferredPatientId = selected.id();
    patientViewGeneration++;
    long generation = patientViewGeneration;
    PatientDirectoryPatientView.showView(
        prefix,
        selected,
        viewingContent,
        services,
        session,
        workspaceFeedback,
        this::handlePatientChanged,
        taskRunner,
        this::isActive,
        () -> isActive() && generation == patientViewGeneration && viewingContent.isVisible(),
        this::refresh,
        this::showDirectory,
        this::showPatientEdit,
        this::showPatientView);
    showViewing();
  }

  private void showPatientEdit(Patient selected) {
    PatientDirectoryPatientView.showEdit(
        prefix,
        selected,
        editingContent,
        clock,
        services,
        session,
        workspaceFeedback,
        this::handlePatientChanged,
        taskRunner,
        this::refresh,
        this::showEditing,
        this::showPatientView);
  }

  private void handlePatientChanged(long patientId) {
    preferredPatientId = patientId;
    onPatientChanged.accept(patientId);
  }

  private boolean isActive() {
    return !disposed;
  }

  /** Creates a read-only administrative details grid for a patient. */
  static GridPane patientDetailsGrid(Patient patient) {
    return PatientDirectoryTableView.details(patient);
  }

  static String calculateAgeText(LocalDate dateOfBirth) {
    return calculateAgeText(dateOfBirth, ClinicClock.system());
  }

  static String calculateAgeText(LocalDate dateOfBirth, Clock clock) {
    return PatientDirectoryFormView.calculateAgeText(dateOfBirth, clock);
  }

  private static String patientOptionLabel(Patient patient) {
    String document =
        (valueOrEmpty(patient.identityType()) + " " + valueOrEmpty(patient.identityNumber()))
            .trim();
    return document.isBlank() ? fullName(patient) : fullName(patient) + " · " + document;
  }

  private static String fullName(Patient patient) {
    return PatientDirectoryTableView.fullName(patient);
  }

  private static String displayPhone(Patient patient) {
    return PatientDirectoryTableView.displayPhone(patient);
  }

  private static String valueOrEmpty(Object value) {
    return PatientDirectoryTableView.valueOrEmpty(value);
  }

  private static String requirePrefix(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Directory prefix is required");
    }
    return value;
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

  private static void showTaskError(Label feedback, Throwable failure, String fallback) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, fallback);
    }
  }
}
