package nusynapxe.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/**
 * Builds the Receptionist scheduling, patient, checkout, and revenue workspace.
 */
public final class ReceptionistView {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");

  private ReceptionistView() {
    throw new AssertionError("Utility class");
  }

  /** Creates the Receptionist workspace. */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    PatientForm registerForm = patientForm("reception-register", false);
    Button register = button("Register patient", "reception-patient-register");
    TextField patientSearch = field("reception-patient-search", "Patient ID, document, or details");
    Button searchPatients = button("Search patients", "reception-patient-search-submit");
    Button clearPatientSearch = button("Clear search", "reception-patient-search-clear");
    ListView<Patient> patientList = new ListView<>();
    patientList.setId("reception-patient-list");

    ComboBox<Account> doctor = doctorSelector();
    TextField startsAt = field("reception-start", DATE_TIME_PATTERN);
    TextField endsAt = field("reception-end", DATE_TIME_PATTERN);
    Button book = button("Book appointment", "reception-book");
    TextField rescheduleStartsAt = field("reception-reschedule-start", DATE_TIME_PATTERN);
    TextField rescheduleEndsAt = field("reception-reschedule-end", DATE_TIME_PATTERN);
    Button reschedule = button("Reschedule selected", "reception-reschedule");
    Button cancel = button("Cancel selected", "reception-cancel");
    ListView<Appointment> appointmentList = new ListView<>();
    appointmentList.setId("reception-appointment-list");
    ComboBox<Patient> appointmentPatient = new ComboBox<>();
    appointmentPatient.setId("reception-appointment-patient");
    Button checkIn = button("Check in selected", "reception-check-in");
    TextField charge = field("reception-charge", "Amount");
    ComboBox<PaymentMethod> method = new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
    method.setId("reception-method");
    method.getSelectionModel().select(PaymentMethod.CASH);
    Button checkout = button("Complete checkout", "reception-checkout");
    ListView<Appointment> checkoutAppointmentList = new ListView<>();
    checkoutAppointmentList.setId("reception-checkout-appointment-list");
    TextField revenueDate = field("reception-revenue-date", "yyyy-MM-dd");
    Button revenueButton = button("Show revenue", "reception-revenue-submit");
    Label revenue = new Label();
    revenue.setId("reception-revenue");
    Label feedback = new Label();
    feedback.setId("reception-feedback");
    SelectionState selection = new SelectionState();

    patientList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              selection.patientId = selected == null ? 0 : selected.id();
              if (selected != null) {
                showPatientDetails(
                    services,
                    session,
                    selected,
                    patientList,
                    selection,
                    feedback,
                    patientSearch,
                    appointmentPatient);
              }
            });
    appointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> selection.appointmentId = selected == null ? 0 : selected.id());
    checkoutAppointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> selection.appointmentId = selected == null ? 0 : selected.id());

    register.setOnAction(
        event -> {
          try {
            Patient patient = services.patientService().register(session, patientFromForm(registerForm, 0, true));
            clearPatientForm(registerForm);
            feedback.setText("Patient registered");
            refreshPatients(
                services, session, patientList, selection, feedback, patientSearch.getText());
            refreshAppointmentPatients(
                services, session, appointmentPatient, feedback, patient.id());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient registration is temporarily unavailable");
          }
        });

    searchPatients.setOnAction(
        event -> refreshPatients(
            services, session, patientList, selection, feedback, patientSearch.getText()));
    clearPatientSearch.setOnAction(
        event -> {
          patientSearch.clear();
          refreshPatients(services, session, patientList, selection, feedback, "");
        });

    book.setOnAction(
        event -> {
          try {
            if (appointmentPatient.getValue() == null || doctor.getValue() == null) {
              throw new ValidationException("Select a patient and Doctor first");
            }
            Appointment appointment = services
                .appointmentService()
                .book(
                    session,
                    appointmentPatient.getValue().id(),
                    doctor.getValue().id(),
                    parseDateTime(startsAt.getText(), "Start time"),
                    parseDateTime(endsAt.getText(), "End time"));
            selection.appointmentId = appointment.id();
            feedback.setText("Appointment booked and awaiting Doctor acceptance");
            refreshAppointments(services, session, appointmentList, selection, feedback);
            refreshAppointments(services, session, checkoutAppointmentList, selection, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Appointment booking is temporarily unavailable");
          }
        });

    reschedule.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services
                .appointmentService()
                .reschedule(
                    session,
                    selection.appointmentId,
                    parseDateTime(rescheduleStartsAt.getText(), "New start time"),
                    parseDateTime(rescheduleEndsAt.getText(), "New end time"));
            feedback.setText("Appointment rescheduled");
            refreshAppointments(services, session, appointmentList, selection, feedback);
            refreshAppointments(services, session, checkoutAppointmentList, selection, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Appointment rescheduling is temporarily unavailable");
          }
        });

    cancel.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services.appointmentService().cancel(session, selection.appointmentId);
            feedback.setText("Appointment cancelled");
            refreshAppointments(services, session, appointmentList, selection, feedback);
            refreshAppointments(services, session, checkoutAppointmentList, selection, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Appointment cancellation is temporarily unavailable");
          }
        });

    checkIn.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services.appointmentService().checkIn(session, selection.appointmentId);
            feedback.setText("Patient checked in");
            refreshAppointments(services, session, appointmentList, selection, feedback);
            refreshAppointments(services, session, checkoutAppointmentList, selection, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Check-in is temporarily unavailable");
          }
        });

    checkout.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId, APPOINTMENT_REQUIRED);
            services
                .billingService()
                .checkout(
                    session,
                    selection.appointmentId,
                    parseMinor(charge.getText()),
                    method.getValue());
            feedback.setText("Checkout completed");
            refreshAppointments(services, session, appointmentList, selection, feedback);
            refreshAppointments(services, session, checkoutAppointmentList, selection, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Checkout is temporarily unavailable");
          }
        });

    revenueButton.setOnAction(
        event -> {
          try {
            RevenueSummary summary = services
                .billingService()
                .dailyRevenue(session, parseDate(revenueDate.getText(), "Revenue date"));
            revenue.setText(
                summary.transactionCount()
                    + " successful payment(s), total "
                    + formatMinor(summary.totalMinor()));
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Revenue is temporarily unavailable");
          }
        });

    Button logout = button("Log out", "logout-button");
    logout.setOnAction(event -> onLogout.run());
    Region headerSpacer = new Region();
    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
    HBox header = new HBox(12, new Label("RECEPTIONIST workspace"), headerSpacer, logout);

    VBox registerContent = new VBox(10, patientGrid(registerForm, false), register);
    registerContent.setId("reception-patient-register-tab");
    Tab registerTab = new Tab("Register new patient", registerContent);
    registerTab.setClosable(false);

    HBox patientSearchBar = new HBox(8, patientSearch, searchPatients, clearPatientSearch);
    VBox manageContent = new VBox(10, patientSearchBar, patientList);
    manageContent.setId("reception-patient-manage-tab");
    Tab manageTab = new Tab("Search and manage patients", manageContent);
    manageTab.setClosable(false);

    TabPane patientTabs = new TabPane(registerTab, manageTab);
    patientTabs.setId("reception-patient-tabs");
    GridPane appointmentForm = new GridPane();
    appointmentForm.setHgap(8);
    appointmentForm.setVgap(8);
    appointmentForm.addRow(0, new Label("Patient"), appointmentPatient);
    appointmentForm.addRow(1, new Label("Doctor"), doctor);
    appointmentForm.addRow(2, new Label("Starts"), startsAt, new Label("Ends"), endsAt, book);
    appointmentForm.addRow(
        3, new Label("New start"), rescheduleStartsAt, new Label("New end"), rescheduleEndsAt);
    appointmentForm.addRow(4, reschedule, cancel);
    GridPane checkoutForm = new GridPane();
    checkoutForm.setHgap(8);
    checkoutForm.setVgap(8);
    checkoutForm.addRow(0, new Label("Amount"), charge, new Label("Method"), method, checkout);
    GridPane revenueForm = new GridPane();
    revenueForm.setHgap(8);
    revenueForm.setVgap(8);
    revenueForm.addRow(0, new Label("Date"), revenueDate, revenueButton, revenue);
    VBox patientContent = new VBox(12, patientTabs);
    VBox appointmentContent = new VBox(12, appointmentForm, appointmentList, checkIn);
    VBox checkoutContent = new VBox(12, checkoutAppointmentList, checkoutForm);
    VBox revenueContent = new VBox(12, revenueForm);
    Tab patientFeature = featureTab("Patient directory and basic data", patientContent);
    Tab appointmentFeature = featureTab("Appointments across all Doctors", appointmentContent);
    Tab checkoutFeature = featureTab("Checkout", checkoutContent);
    Tab revenueFeature = featureTab("Daily revenue", revenueContent);
    TabPane workspaceTabs = new TabPane(patientFeature, appointmentFeature, checkoutFeature, revenueFeature);
    workspaceTabs.setId("reception-workspace-tabs");
    workspaceTabs
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected == patientFeature) {
                refreshPatients(
                    services, session, patientList, selection, feedback, patientSearch.getText());
              } else if (selected == appointmentFeature) {
                refreshDoctors(services, session, doctor, feedback);
                refreshAppointmentPatients(
                    services, session, appointmentPatient, feedback, selection.patientId);
                refreshAppointments(services, session, appointmentList, selection, feedback);
              } else if (selected == checkoutFeature) {
                refreshAppointments(
                    services, session, checkoutAppointmentList, selection, feedback);
              } else if (selected == revenueFeature && !revenueDate.getText().isBlank()) {
                revenueButton.fire();
              }
            });
    BorderPane root = new BorderPane(workspaceTabs);
    root.setId("receptionist-workspace");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setBottom(feedback);
    refreshDoctors(services, session, doctor, feedback);
    refreshPatients(services, session, patientList, selection, feedback, "");
    refreshAppointmentPatients(services, session, appointmentPatient, feedback, 0);
    refreshAppointments(services, session, appointmentList, selection, feedback);
    refreshAppointments(services, session, checkoutAppointmentList, selection, feedback);
    return root;
  }

  private static void showPatientDetails(
      ClinicServices services,
      Session session,
      Patient selected,
      ListView<Patient> patientList,
      SelectionState selection,
      Label workspaceFeedback,
      TextField patientSearch,
      ComboBox<Patient> appointmentPatient) {
    PatientForm form = patientForm("reception-patient", true);
    populatePatientForm(selected, form);
    Patient[] current = { selected };
    Label feedback = new Label();
    feedback.setId("reception-patient-details-feedback");
    Button update = button("Save patient changes", "reception-patient-update");
    Button status = button(patientStatusButtonText(selected), "reception-patient-deactivate");

    update.setOnAction(
        event -> {
          try {
            Patient updated = services
                .patientService()
                .updateAdministrative(
                    session, patientFromForm(form, current[0].id(), current[0].active()));
            current[0] = updated;
            populatePatientForm(updated, form);
            feedback.setText("Patient changes saved");
            workspaceFeedback.setText("Patient changes saved");
            selection.patientId = 0;
            refreshPatients(
                services,
                session,
                patientList,
                selection,
                workspaceFeedback,
                patientSearch.getText());
            refreshAppointmentPatients(
                services, session, appointmentPatient, workspaceFeedback, updated.id());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient update is temporarily unavailable");
          }
        });

    status.setOnAction(
        event -> {
          try {
            Patient updated = current[0].active()
                ? services.patientService().deactivateAdministrative(session, current[0].id())
                : services.patientService().activateAdministrative(session, current[0].id());
            current[0] = updated;
            populatePatientForm(updated, form);
            status.setText(patientStatusButtonText(updated));
            String message = updated.active() ? "Patient activated" : "Patient deactivated";
            feedback.setText(message);
            workspaceFeedback.setText(message);
            selection.patientId = 0;
            refreshPatients(
                services,
                session,
                patientList,
                selection,
                workspaceFeedback,
                patientSearch.getText());
            refreshAppointmentPatients(
                services, session, appointmentPatient, workspaceFeedback, updated.id());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient status update is temporarily unavailable");
          }
        });

    HBox actions = new HBox(8, update, status);
    VBox content = new VBox(10, patientGrid(form, true), actions, feedback);
    content.setPadding(new Insets(18));
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setId("reception-patient-details-window");
    Stage details = new Stage();
    details.setTitle("Patient details - " + selected.displayedId());
    details.initOwner(patientList.getScene().getWindow());
    details.initModality(Modality.WINDOW_MODAL);
    details.setScene(new Scene(scroll, 920, 520));
    details.show();
  }

  private static String patientStatusButtonText(Patient patient) {
    return patient.active() ? "Deactivate patient" : "Activate patient";
  }

  private static PatientForm patientForm(String prefix, boolean includePatientId) {
    TextField patientId = includePatientId ? field(prefix + "-id", "Generated Patient ID") : null;
    if (patientId != null) {
      patientId.setEditable(false);
    }
    ComboBox<IdentityType> identityType = new ComboBox<>(FXCollections.observableArrayList(IdentityType.values()));
    identityType.setId(prefix + "-identity-type");
    ComboBox<CountryOption> issuingCountry = new ComboBox<>(
        FXCollections.observableArrayList(CountryOption.allCountries()));
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
              boolean singaporeIdentity = selected == IdentityType.NRIC || selected == IdentityType.FIN;
              if (singaporeIdentity) {
                CountryOption.fromCode("SG")
                    .ifPresent(country -> issuingCountry.getSelectionModel().select(country));
              }
              issuingCountry.setDisable(singaporeIdentity);
            });
    identityType.getSelectionModel().select(IdentityType.NRIC);
    ComboBox<Sex> sex = new ComboBox<>(FXCollections.observableArrayList(Sex.MALE, Sex.FEMALE));
    sex.setId(prefix + "-sex");
    ComboBox<Integer> birthDay = new ComboBox<>();
    birthDay.setId(prefix + "-date-of-birth-day");
    birthDay.setPromptText("Day");
    for (int day = 1; day <= 31; day++) {
      birthDay.getItems().add(day);
    }
    ComboBox<Month> birthMonth = new ComboBox<>(FXCollections.observableArrayList(Month.values()));
    birthMonth.setId(prefix + "-date-of-birth-month");
    birthMonth.setPromptText("Month");
    ComboBox<Integer> birthYear = new ComboBox<>();
    birthYear.setId(prefix + "-date-of-birth-year");
    birthYear.setPromptText("Year");
    int currentYear = LocalDate.now(SINGAPORE_ZONE).getYear();
    for (int year = currentYear; year >= 1900; year--) {
      birthYear.getItems().add(year);
    }
    TextField age = field(prefix + "-age", "");
    age.setEditable(false);
    Runnable updateAgeDisplay = () -> {
      if (birthDay.getValue() != null
          && birthMonth.getValue() != null
          && birthYear.getValue() != null) {
        LocalDate dateOfBirth = LocalDate.of(birthYear.getValue(), birthMonth.getValue(), birthDay.getValue());
        age.setText(calculateAgeText(dateOfBirth));
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
    HBox dateOfBirthControls = new HBox(6, form.dateOfBirth(), form.birthMonth(), form.birthYear());
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
      dateOfBirth = LocalDate.of(
          form.birthYear().getValue(),
          form.birthMonth().getValue(),
          form.birthDay().getValue());
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
    if (patient.dateOfBirth() != null) {
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

  private static Label requiredLabel(String text) {
    return new Label(text + " *");
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

  private static Tab featureTab(String title, VBox content) {
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    Tab tab = new Tab(title, scroll);
    tab.setClosable(false);
    return tab;
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

  private static ComboBox<Account> doctorSelector() {
    ComboBox<Account> doctor = new ComboBox<>();
    doctor.setId("reception-doctor");
    doctor.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(Account account) {
            return account == null ? "" : account.displayName() + " (" + account.username() + ")";
          }

          @Override
          public Account fromString(String value) {
            return null;
          }
        });
    return doctor;
  }

  private static void refreshDoctors(
      ClinicServices services, Session session, ComboBox<Account> doctor, Label feedback) {
    try {
      doctor.setItems(
          FXCollections.observableArrayList(services.accountService().listDoctors(session)));
      if (!doctor.getItems().isEmpty()) {
        doctor.getSelectionModel().selectFirst();
      }
    } catch (SQLException exception) {
      feedback.setText("Doctors are temporarily unavailable");
    }
  }

  private static void refreshPatients(
      ClinicServices services,
      Session session,
      ListView<Patient> patientList,
      SelectionState selection,
      Label feedback,
      String query) {
    try {
      long selectedPatientId = selection.patientId;
      patientList.setItems(
          FXCollections.observableArrayList(
              services.patientService().searchAdministrative(session, query)));
      if (!selectPatient(patientList, selectedPatientId)) {
        selection.patientId = 0;
      }
    } catch (SQLException exception) {
      feedback.setText("Patients are temporarily unavailable");
    }
  }

  private static void refreshAppointmentPatients(
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
    } catch (SQLException exception) {
      feedback.setText("Patients are temporarily unavailable");
    }
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

  private static void refreshAppointments(
      ClinicServices services,
      Session session,
      ListView<Appointment> appointmentList,
      SelectionState selection,
      Label feedback) {
    try {
      long selectedAppointmentId = selection.appointmentId;
      appointmentList.setItems(
          FXCollections.observableArrayList(
              services.appointmentService().allAppointments(session)));
      selection.appointmentId = selectedAppointmentId;
      selectAppointment(appointmentList, selection.appointmentId);
    } catch (SQLException exception) {
      feedback.setText("Appointments are temporarily unavailable");
    }
  }

  private static boolean selectPatient(ListView<Patient> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).id() == id) {
        list.getSelectionModel().select(index);
        return true;
      }
    }
    list.getSelectionModel().clearSelection();
    return false;
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

  private static void selectAppointment(ListView<Appointment> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).id() == id) {
        list.getSelectionModel().select(index);
        return;
      }
    }
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
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

  private static LocalDate parseDate(String value, String fieldName) {
    if (value == null) {
      throw new ValidationException(fieldName + " must use yyyy-MM-dd");
    }
    try {
      return LocalDate.parse(value.trim(), DATE_FORMAT);
    } catch (DateTimeParseException exception) {
      throw new ValidationException(fieldName + " must use yyyy-MM-dd", exception);
    }
  }

  private static long parseMinor(String value) {
    if (value == null) {
      throw new ValidationException("Amount must be a positive number with at most two decimals");
    }
    try {
      return new BigDecimal(value.trim())
          .setScale(2, RoundingMode.UNNECESSARY)
          .movePointRight(2)
          .longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new ValidationException(
          "Amount must be a positive number with at most two decimals", exception);
    }
  }

  private static String formatMinor(long amountMinor) {
    return BigDecimal.valueOf(amountMinor, 2).toPlainString();
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
    // Groups one tab's independent patient-form controls.
  }

  private static final class SelectionState {
    private long patientId;
    private long appointmentId;
  }
}
