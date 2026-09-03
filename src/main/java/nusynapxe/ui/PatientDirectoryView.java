package nusynapxe.ui;

import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongConsumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PatientDeletionBlockers;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.PatientDeletionBlockedException;
import nusynapxe.service.ValidationException;

/** Builds the shared administrative patient directory for Doctors and Receptionists. */
final class PatientDirectoryView {
  private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");

  private final ClinicServices services;
  private final Session session;
  private final String prefix;
  private final Label workspaceFeedback;
  private final LongConsumer onPatientChanged;
  private final TextField patientSearch;
  private final TableView<Patient> patientTable;
  private final Label pageTitle;
  private final VBox directoryContent;
  private final VBox registrationContent;
  private final VBox editingContent;
  private final VBox root;
  private long preferredPatientId;

  private PatientDirectoryView(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.prefix = requirePrefix(prefix);
    this.workspaceFeedback = Objects.requireNonNull(workspaceFeedback, "workspaceFeedback");
    this.onPatientChanged = Objects.requireNonNull(onPatientChanged, "onPatientChanged");

    PatientForm registerForm = patientForm(prefix + "-register", false);
    Button register = button("Register patient", prefix + "-patient-register");
    patientSearch = field(prefix + "-patient-search", "Patient ID, document, or details");
    Button searchPatients = button("Search patients", prefix + "-patient-search-submit");
    Button clearPatientSearch = button("Clear search", prefix + "-patient-search-clear");
    patientTable = createPatientTable();

    register.setOnAction(
        event -> {
          try {
            Patient patient =
                services.patientService().register(session, patientFromForm(registerForm, 0, true));
            clearPatientForm(registerForm);
            patientSearch.clear();
            showDirectory();
            workspaceFeedback.setText("Patient registered");
            refresh();
            preferredPatientId = patient.id();
            onPatientChanged.accept(patient.id());
          } catch (ValidationException | AuthorizationException exception) {
            workspaceFeedback.setText(exception.getMessage());
          } catch (java.sql.SQLException exception) {
            workspaceFeedback.setText("Patient registration is temporarily unavailable");
          }
        });

    searchPatients.setOnAction(
        event -> {
          refresh();
        });
    clearPatientSearch.setOnAction(
        event -> {
          patientSearch.clear();
          refresh();
        });

    HBox patientSearchBar = new HBox(8, patientSearch, searchPatients, clearPatientSearch);
    Button openRegistration = button("Register new patient", prefix + "-patient-open-register");
    directoryContent = new VBox(10, patientSearchBar, patientTable, openRegistration);
    directoryContent.setId(prefix + "-patient-directory-view");
    VBox.setVgrow(patientTable, Priority.ALWAYS);

    Button cancelRegistration = button("Cancel", prefix + "-patient-register-cancel");
    HBox registrationActions = new HBox(8, register, cancelRegistration);
    registrationContent = new VBox(10, patientGrid(registerForm, false), registrationActions);
    registrationContent.setId(prefix + "-patient-register-view");

    editingContent = new VBox(10);
    editingContent.setId(prefix + "-patient-edit-view");

    openRegistration.setOnAction(
        event -> {
          clearPatientForm(registerForm);
          showRegistration();
        });
    cancelRegistration.setOnAction(
        event -> {
          clearPatientForm(registerForm);
          showDirectory();
          refresh();
        });

    StackPane patientContent = new StackPane(directoryContent, registrationContent, editingContent);
    patientContent.setId(prefix + "-patient-content");
    pageTitle = UiComponents.pageTitle("Patient directory");
    root = new VBox(12, pageTitle, patientContent);
    root.setId(prefix + "-patient-directory");
    showDirectory();
  }

  /** Creates a patient directory with a role-specific semantic-control prefix. */
  static PatientDirectoryView create(
      ClinicServices services,
      Session session,
      String prefix,
      Label workspaceFeedback,
      LongConsumer onPatientChanged) {
    PatientDirectoryView view =
        new PatientDirectoryView(services, session, prefix, workspaceFeedback, onPatientChanged);
    view.refresh();
    return view;
  }

  /** Returns the directory content for embedding in a workspace. */
  Parent view() {
    return root;
  }

  private void showDirectory() {
    pageTitle.setText("Patient directory");
    directoryContent.setManaged(true);
    directoryContent.setVisible(true);
    registrationContent.setManaged(false);
    registrationContent.setVisible(false);
    editingContent.setManaged(false);
    editingContent.setVisible(false);
  }

  private void showRegistration() {
    pageTitle.setText("Register new patient");
    directoryContent.setManaged(false);
    directoryContent.setVisible(false);
    registrationContent.setManaged(true);
    registrationContent.setVisible(true);
    editingContent.setManaged(false);
    editingContent.setVisible(false);
  }

  private void showEditing() {
    pageTitle.setText("Edit patient");
    directoryContent.setManaged(false);
    directoryContent.setVisible(false);
    registrationContent.setManaged(false);
    registrationContent.setVisible(false);
    editingContent.setManaged(true);
    editingContent.setVisible(true);
  }

  /** Reloads directory results using the current search query. */
  void refresh() {
    try {
      patientTable.setItems(
          FXCollections.observableArrayList(
              services.patientService().searchAdministrative(session, patientSearch.getText())));
      patientTable.getSelectionModel().clearSelection();
      int visibleRows = Math.min(Math.max(patientTable.getItems().size(), 1), 5);
      patientTable.setPrefHeight(40 + visibleRows * 40);
    } catch (java.sql.SQLException exception) {
      workspaceFeedback.setText("Patients are temporarily unavailable");
      patientTable.setItems(FXCollections.observableArrayList());
    }
  }

  /** Returns the patient most recently selected for a related workflow. */
  long selectedPatientId() {
    return preferredPatientId;
  }

  /** Populates an appointment selector with active patients and retains a preferred selection. */
  static void refreshAppointmentPatients(
      ClinicServices services,
      Session session,
      ComboBox<Patient> selector,
      Label feedback,
      long preferredPatientId) {
    try {
      selector.setItems(
          FXCollections.observableArrayList(
              services.patientService().searchAdministrative(session, "").stream()
                  .filter(Patient::active)
                  .toList()));
      if (!selectPatient(selector, preferredPatientId) && !selector.getItems().isEmpty()) {
        selector.getSelectionModel().selectFirst();
      }
    } catch (java.sql.SQLException exception) {
      feedback.setText("Patients are temporarily unavailable");
    }
  }

  /** Makes a patient selector resolve its visible administrative label back to a patient. */
  static void makePatientSearchable(ComboBox<Patient> selector) {
    UiComponents.applyCompactSelector(selector);
    selector.setEditable(true);
    selector.setPromptText("Search patients");
    selector.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(Patient patient) {
            return patient == null
                ? ""
                : patient.displayedId()
                    + " "
                    + valueOrEmpty(patient.firstName())
                    + " "
                    + valueOrEmpty(patient.lastName());
          }

          @Override
          public Patient fromString(String value) {
            if (value == null || value.isBlank()) {
              return null;
            }
            return selector.getItems().stream()
                .filter(patient -> toString(patient).equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
          }
        });
  }

  private TableView<Patient> createPatientTable() {
    TableView<Patient> table = new TableView<>();
    table.setId(prefix + "-patient-table");
    table.getStyleClass().add("patient-table");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setFixedCellSize(40);
    table.setPrefHeight(80);
    table.setMinHeight(80);
    table.setMaxHeight(260);
    table.setPlaceholder(
        UiComponents.emptyState(prefix + "-patient-empty", "No patients match this search."));

    TableColumn<Patient, String> patientId = textColumn("Patient ID", Patient::displayedId);
    TableColumn<Patient, String> name =
        textColumn(
            "Name",
            patient ->
                (valueOrEmpty(patient.firstName()) + " " + valueOrEmpty(patient.lastName()))
                    .trim());
    TableColumn<Patient, String> dateOfBirth =
        textColumn("Date of birth", patient -> valueOrEmpty(patient.dateOfBirth()));
    TableColumn<Patient, String> phone = textColumn("Phone", PatientDirectoryView::displayPhone);
    TableColumn<Patient, String> email =
        textColumn("Email", patient -> valueOrEmpty(patient.email()));
    TableColumn<Patient, String> status =
        textColumn("Status", patient -> patient.active() ? "Active" : "Inactive");
    TableColumn<Patient, Void> actions = editColumn();
    actions.setMinWidth(96);
    actions.setPrefWidth(96);
    actions.setMaxWidth(96);
    table.getColumns().addAll(List.of(patientId, name, dateOfBirth, phone, email, status, actions));
    return table;
  }

  private static TableColumn<Patient, String> textColumn(
      String title, Function<Patient, String> valueProvider) {
    TableColumn<Patient, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
    return column;
  }

  private TableColumn<Patient, Void> editColumn() {
    TableColumn<Patient, Void> actions = new TableColumn<>("Actions");
    actions.setCellFactory(
        column ->
            new TableCell<>() {
              private final Button edit = button("Edit", prefix + "-patient-edit");

              {
                setAlignment(Pos.CENTER_RIGHT);
                edit.getStyleClass().add("table-row-action");
                edit.setOnAction(
                    event -> {
                      Patient patient = getTableRow().getItem();
                      if (patient != null) {
                        showPatientEdit(patient);
                      }
                    });
              }

              @Override
              protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                Patient patient = empty ? null : getTableRow().getItem();
                if (patient == null) {
                  setGraphic(null);
                } else {
                  edit.setId(prefix + "-patient-edit-" + patient.id());
                  setGraphic(edit);
                }
              }
            });
    return actions;
  }

  private static String displayPhone(Patient patient) {
    String countryCode = valueOrEmpty(patient.phoneCountryCode());
    String number = valueOrEmpty(patient.phoneNumber());
    if (countryCode.isBlank()) {
      return number;
    }
    if (number.isBlank()) {
      return "+" + countryCode;
    }
    return "+" + countryCode + " " + number;
  }

  private void showPatientEdit(Patient selected) {
    PatientForm form = patientForm(prefix + "-patient", true);
    populatePatientForm(selected, form);
    Patient[] current = {selected};
    Label feedback = new Label();
    feedback.setId(prefix + "-patient-edit-feedback");
    Button update = button("Save patient changes", prefix + "-patient-update");
    Button status = button(patientStatusButtonText(selected), prefix + "-patient-deactivate");
    Button delete = UiComponents.dangerButton("Delete patient", prefix + "-patient-delete");
    Button cancel = button("Cancel", prefix + "-patient-edit-cancel");

    update.setOnAction(
        event -> {
          try {
            Patient updated =
                services
                    .patientService()
                    .updateAdministrative(
                        session, patientFromForm(form, current[0].id(), current[0].active()));
            current[0] = updated;
            populatePatientForm(updated, form);
            feedback.setText("Patient changes saved");
            workspaceFeedback.setText("Patient changes saved");
            editingContent.getChildren().clear();
            showDirectory();
            refresh();
            preferredPatientId = updated.id();
            onPatientChanged.accept(updated.id());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (java.sql.SQLException exception) {
            feedback.setText("Patient update is temporarily unavailable");
          }
        });

    status.setOnAction(
        event -> {
          try {
            Patient updated =
                current[0].active()
                    ? services.patientService().deactivateAdministrative(session, current[0].id())
                    : services.patientService().activateAdministrative(session, current[0].id());
            current[0] = updated;
            populatePatientForm(updated, form);
            status.setText(patientStatusButtonText(updated));
            String message = updated.active() ? "Patient activated" : "Patient deactivated";
            feedback.setText(message);
            workspaceFeedback.setText(message);
            refresh();
            preferredPatientId = updated.id();
            onPatientChanged.accept(updated.id());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (java.sql.SQLException exception) {
            feedback.setText("Patient status update is temporarily unavailable");
          }
        });

    delete.setOnAction(
        event -> {
          Stage owner = (Stage) editingContent.getScene().getWindow();
          try {
            PatientDeletionBlockers blockers =
                services.patientService().deletionBlockers(session, current[0].id());
            if (!blockers.canDelete()) {
              showBlockedDeletionDialog(owner, blockers);
              return;
            }
            if (!confirmDeletion(owner, current[0])) {
              return;
            }
            services.patientService().deleteAdministrative(session, current[0].id());
            workspaceFeedback.setText("Patient deleted");
            editingContent.getChildren().clear();
            showDirectory();
            refresh();
            preferredPatientId = 0;
            onPatientChanged.accept(0);
          } catch (PatientDeletionBlockedException exception) {
            showBlockedDeletionDialog(owner, exception.blockers());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (java.sql.SQLException exception) {
            feedback.setText("Patient deletion is temporarily unavailable");
          }
        });

    cancel.setOnAction(
        event -> {
          clearPatientForm(form);
          editingContent.getChildren().clear();
          showDirectory();
          refresh();
        });

    HBox actions = new HBox(8, update, status, delete, cancel);
    editingContent.setPadding(new Insets(18));
    editingContent.getChildren().setAll(patientGrid(form, true), actions, feedback);
    showEditing();
  }

  private boolean confirmDeletion(Stage owner, Patient patient) {
    Label warning =
        new Label(
            "Delete "
                + patient.displayedId()
                + " permanently? This patient has no related clinic data, and this action cannot be undone.");
    warning.setWrapText(true);
    Button confirm =
        UiComponents.dangerButton("Delete permanently", prefix + "-patient-delete-confirm");
    Button cancel = UiComponents.secondaryButton("Cancel", prefix + "-patient-delete-cancel");
    boolean[] confirmed = {false};
    Stage dialog = new Stage();
    confirm.setOnAction(
        event -> {
          confirmed[0] = true;
          dialog.close();
        });
    cancel.setOnAction(event -> dialog.close());
    VBox content = new VBox(12, warning, UiComponents.actionBar(confirm, cancel));
    content.setId(prefix + "-patient-delete-confirm-window");
    content.setPadding(new Insets(18));
    dialog.initOwner(owner);
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Confirm patient deletion");
    dialog.setScene(new Scene(content, 460, 190));
    dialog.showAndWait();
    return confirmed[0];
  }

  private void showBlockedDeletionDialog(Stage owner, PatientDeletionBlockers blockers) {
    Label explanation =
        new Label(
            "This patient cannot be deleted because related clinic data exists. The patient and its history were preserved.");
    explanation.setId(prefix + "-patient-delete-blocked-explanation");
    explanation.setWrapText(true);
    VBox categories = new VBox(6);
    categories.setId(prefix + "-patient-delete-blocked-categories");
    for (PatientDeletionBlockers.BlockingRelation relation : blockers.blockingRelations()) {
      Label category = new Label(relation.label() + ": " + relation.count());
      category.setId(prefix + "-patient-delete-blocked-" + slug(relation.label()));
      categories.getChildren().add(category);
    }
    Label alternative =
        new Label(
            "To retain the history and prevent new bookings, deactivate the patient instead.");
    alternative.setId(prefix + "-patient-delete-blocked-alternative");
    alternative.setWrapText(true);
    Button close = UiComponents.secondaryButton("Close", prefix + "-patient-delete-blocked-close");
    Stage dialog = new Stage();
    close.setOnAction(event -> dialog.close());
    VBox content = new VBox(12, explanation, categories, alternative, close);
    content.setId(prefix + "-patient-delete-blocked-window");
    content.setPadding(new Insets(18));
    dialog.initOwner(owner);
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Patient cannot be deleted");
    dialog.setScene(new Scene(content, 500, 320));
    dialog.showAndWait();
  }

  private static String slug(String label) {
    return label.toLowerCase(Locale.ROOT).replace(' ', '-');
  }

  private static String requirePrefix(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Directory prefix is required");
    }
    return value;
  }

  private static String patientStatusButtonText(Patient patient) {
    return patient.active() ? "Deactivate patient" : "Activate patient";
  }

  private static PatientForm patientForm(String prefix, boolean includePatientId) {
    TextField patientId = includePatientId ? field(prefix + "-id", "Generated Patient ID") : null;
    if (patientId != null) {
      patientId.setEditable(false);
    }
    ComboBox<IdentityType> identityType = UiComponents.compactSelector();
    identityType.setItems(FXCollections.observableArrayList(IdentityType.values()));
    identityType.setId(prefix + "-identity-type");
    ComboBox<CountryOption> issuingCountry = UiComponents.compactSelector();
    issuingCountry.setItems(FXCollections.observableArrayList(CountryOption.allCountries()));
    issuingCountry.setId(prefix + "-issuing-country");
    TextField phoneCountryCode = field(prefix + "-phone-country-code", "65");
    issuingCountry
        .valueProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected != null) {
                phoneCountryCode.setText(selected.callingCode());
              }
            });
    identityType
        .valueProperty()
        .addListener(
            (observable, previous, selected) -> {
              boolean singaporeIdentity =
                  selected == IdentityType.NRIC || selected == IdentityType.FIN;
              if (singaporeIdentity) {
                CountryOption.fromCode("SG")
                    .ifPresent(country -> issuingCountry.getSelectionModel().select(country));
              }
              issuingCountry.setDisable(singaporeIdentity);
            });
    identityType.getSelectionModel().select(IdentityType.NRIC);
    ComboBox<Sex> sex = UiComponents.compactSelector();
    sex.setItems(FXCollections.observableArrayList(Sex.MALE, Sex.FEMALE));
    sex.setId(prefix + "-sex");
    ComboBox<Integer> birthDay = UiComponents.compactSelector();
    birthDay.setId(prefix + "-date-of-birth-day");
    birthDay.setPromptText("Day");
    for (int day = 1; day <= 31; day++) {
      birthDay.getItems().add(day);
    }
    ComboBox<Month> birthMonth = UiComponents.compactSelector();
    birthMonth.setItems(FXCollections.observableArrayList(Month.values()));
    birthMonth.setId(prefix + "-date-of-birth-month");
    birthMonth.setPromptText("Month");
    ComboBox<Integer> birthYear = UiComponents.compactSelector();
    birthYear.setId(prefix + "-date-of-birth-year");
    birthYear.setPromptText("Year");
    int currentYear = LocalDate.now(SINGAPORE_ZONE).getYear();
    for (int year = currentYear; year >= 1900; year--) {
      birthYear.getItems().add(year);
    }
    TextField age = field(prefix + "-age", "");
    age.setEditable(false);
    Runnable updateAgeDisplay =
        () -> {
          if (birthDay.getValue() != null
              && birthMonth.getValue() != null
              && birthYear.getValue() != null) {
            try {
              LocalDate dateOfBirth =
                  LocalDate.of(birthYear.getValue(), birthMonth.getValue(), birthDay.getValue());
              age.setText(calculateAgeText(dateOfBirth));
            } catch (java.time.DateTimeException exception) {
              age.setText("");
            }
          } else {
            age.setText("");
          }
        };
    birthDay
        .valueProperty()
        .addListener((observable, previous, selected) -> updateAgeDisplay.run());
    birthMonth
        .valueProperty()
        .addListener((observable, previous, selected) -> updateAgeDisplay.run());
    birthYear
        .valueProperty()
        .addListener((observable, previous, selected) -> updateAgeDisplay.run());
    return new PatientForm(
        patientId,
        identityType,
        field(prefix + "-identity-number", "Identity document number"),
        issuingCountry,
        field(prefix + "-first-name", "First name"),
        field(prefix + "-last-name", "Last name"),
        birthDay,
        birthMonth,
        birthYear,
        age,
        sex,
        phoneCountryCode,
        field(prefix + "-phone-number", "Digits only"),
        field(prefix + "-email", "Email"),
        field(prefix + "-address", "Address"),
        field(prefix + "-height", "Height (cm), optional"),
        field(prefix + "-weight", "Weight (kg), optional"));
  }

  private static GridPane patientGrid(PatientForm form, boolean includePatientId) {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    int row = 0;
    if (includePatientId) {
      grid.addRow(row, new Label("Patient ID"), form.patientId());
      row++;
    }
    grid.addRow(
        row,
        requiredLabel("Identity type"),
        form.identityType(),
        requiredLabel("Identity number"),
        form.identityNumber());
    row++;
    grid.addRow(row, requiredLabel("Issuing country"), form.issuingCountry());
    row++;
    grid.addRow(
        row,
        requiredLabel("First name"),
        form.firstName(),
        requiredLabel("Last name"),
        form.lastName());
    row++;
    HBox dateOfBirthControls = new HBox(6, form.birthDay(), form.birthMonth(), form.birthYear());
    grid.addRow(
        row, requiredLabel("Date of birth"), dateOfBirthControls, new Label("Age"), form.age());
    row++;
    grid.addRow(row, requiredLabel("Sex"), form.sex());
    row++;
    Label phonePlus = new Label("+");
    phonePlus.setId(form.phoneCountryCode().getId().replace("country-code", "plus"));
    HBox phoneCountryCode = new HBox(4, phonePlus, form.phoneCountryCode());
    grid.addRow(
        row,
        requiredLabel("Phone country code"),
        phoneCountryCode,
        requiredLabel("Phone number"),
        form.phoneNumber());
    row++;
    grid.addRow(row, requiredLabel("Email"), form.email());
    row++;
    grid.addRow(row, requiredLabel("Address"), form.address());
    row++;
    grid.addRow(
        row, new Label("Height (cm)"), form.height(), new Label("Weight (kg)"), form.weight());
    return grid;
  }

  private static Patient patientFromForm(PatientForm form, long id, boolean active) {
    CountryOption country = form.issuingCountry().getValue();
    LocalDate dateOfBirth = null;
    if (form.birthDay().getValue() != null
        && form.birthMonth().getValue() != null
        && form.birthYear().getValue() != null) {
      try {
        dateOfBirth =
            LocalDate.of(
                form.birthYear().getValue(),
                form.birthMonth().getValue(),
                form.birthDay().getValue());
      } catch (java.time.DateTimeException exception) {
        throw new ValidationException("Date of birth must be valid", exception);
      }
    }
    return new Patient(
        id,
        form.identityType().getValue(),
        form.identityNumber().getText(),
        country == null ? null : country.code(),
        form.firstName().getText(),
        form.lastName().getText(),
        dateOfBirth == null ? null : dateOfBirth.toString(),
        form.sex().getValue(),
        form.phoneCountryCode().getText(),
        form.phoneNumber().getText(),
        form.email().getText(),
        form.address().getText(),
        parseOptionalMeasurement(form.height().getText(), "Height"),
        parseOptionalMeasurement(form.weight().getText(), "Weight"),
        active);
  }

  private static void populatePatientForm(Patient patient, PatientForm form) {
    if (form.patientId() != null) {
      form.patientId().setText(patient.displayedId());
    }
    form.identityType().setValue(patient.identityType());
    form.identityNumber().setText(valueOrEmpty(patient.identityNumber()));
    if (patient.identityType() != IdentityType.NRIC && patient.identityType() != IdentityType.FIN) {
      form.issuingCountry().setValue(CountryOption.fromCode(patient.issuingCountry()).orElse(null));
    }
    form.firstName().setText(valueOrEmpty(patient.firstName()));
    form.lastName().setText(valueOrEmpty(patient.lastName()));
    if (patient.dateOfBirth() != null && !patient.dateOfBirth().isBlank()) {
      LocalDate dob = LocalDate.parse(patient.dateOfBirth());
      form.birthDay().setValue(dob.getDayOfMonth());
      form.birthMonth().setValue(dob.getMonth());
      form.birthYear().setValue(dob.getYear());
    }
    form.sex().setValue(patient.sex());
    form.phoneCountryCode().setText(valueOrEmpty(patient.phoneCountryCode()));
    form.phoneNumber().setText(valueOrEmpty(patient.phoneNumber()));
    form.email().setText(valueOrEmpty(patient.email()));
    form.address().setText(valueOrEmpty(patient.address()));
    form.height().setText(patient.heightCm() == null ? "" : patient.heightCm().toString());
    form.weight().setText(patient.weightKg() == null ? "" : patient.weightKg().toString());
  }

  private static void clearPatientForm(PatientForm form) {
    clear(
        form.identityNumber(),
        form.firstName(),
        form.lastName(),
        form.age(),
        form.phoneCountryCode(),
        form.phoneNumber(),
        form.email(),
        form.address(),
        form.height(),
        form.weight());
    if (form.patientId() != null) {
      form.patientId().clear();
    }
    form.birthDay().getSelectionModel().clearSelection();
    form.birthMonth().getSelectionModel().clearSelection();
    form.birthYear().getSelectionModel().clearSelection();
    form.identityType().getSelectionModel().clearSelection();
    form.issuingCountry().getSelectionModel().clearSelection();
    form.sex().getSelectionModel().clearSelection();
  }

  static String calculateAgeText(LocalDate dateOfBirth) {
    if (dateOfBirth == null) {
      return "";
    }
    LocalDate today = LocalDate.now(SINGAPORE_ZONE);
    return dateOfBirth.isAfter(today)
        ? ""
        : Integer.toString(Period.between(dateOfBirth, today).getYears());
  }

  private static Label requiredLabel(String text) {
    return new Label(text + " *");
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

  private static Double parseOptionalMeasurement(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      double measurement = Double.parseDouble(value.trim());
      if (!Double.isFinite(measurement) || measurement <= 0) {
        throw new ValidationException(fieldName + " must be a positive number");
      }
      return measurement;
    } catch (NumberFormatException exception) {
      throw new ValidationException(fieldName + " must be a positive number", exception);
    }
  }

  private static boolean selectPatient(ComboBox<Patient> selector, long id) {
    for (Patient patient : selector.getItems()) {
      if (patient.id() == id) {
        selector.getSelectionModel().select(patient);
        return true;
      }
    }
    selector.getSelectionModel().clearSelection();
    return false;
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private static void clear(TextField... fields) {
    for (TextField field : fields) {
      field.clear();
    }
  }

  private record PatientForm(
      TextField patientId,
      ComboBox<IdentityType> identityType,
      TextField identityNumber,
      ComboBox<CountryOption> issuingCountry,
      TextField firstName,
      TextField lastName,
      ComboBox<Integer> birthDay,
      ComboBox<Month> birthMonth,
      ComboBox<Integer> birthYear,
      TextField age,
      ComboBox<Sex> sex,
      TextField phoneCountryCode,
      TextField phoneNumber,
      TextField email,
      TextField address,
      TextField height,
      TextField weight) {
    // Groups one independent registration or details form's controls.
  }
}
