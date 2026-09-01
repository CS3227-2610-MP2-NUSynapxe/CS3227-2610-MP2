package nusynapxe.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
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
import javafx.scene.layout.VBox;
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

/** Builds the Receptionist scheduling, patient, checkout, and revenue workspace. */
public final class ReceptionistView {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

  private ReceptionistView() {
    throw new AssertionError("Utility class");
  }

  /** Creates the Receptionist workspace. */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    PatientForm registerForm = patientForm("reception-register");
    PatientForm editForm = patientForm("reception-patient");
    clearPatientForm(editForm);
    Button register = button("Register patient", "reception-patient-register");
    Button updatePatient = button("Save patient changes", "reception-patient-update");
    Button deactivatePatient = button("Deactivate patient", "reception-patient-deactivate");
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
    Button checkIn = button("Check in selected", "reception-check-in");
    TextField charge = field("reception-charge", "Amount");
    ComboBox<PaymentMethod> method =
        new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
    method.setId("reception-method");
    method.getSelectionModel().select(PaymentMethod.CASH);
    Button checkout = button("Complete checkout", "reception-checkout");
    TextField revenueDate = field("reception-revenue-date", "yyyy-MM-dd");
    Button revenueButton = button("Show revenue", "reception-revenue-submit");
    Label revenue = new Label();
    revenue.setId("reception-revenue");
    Label feedback = new Label();
    feedback.setId("reception-feedback");
    SelectionState selection = new SelectionState();
    Button refresh = button("Refresh", "reception-refresh");

    patientList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              selection.patientId = selected == null ? 0 : selected.id();
              if (selected == null) {
                clearPatientForm(editForm);
              } else {
                populatePatientForm(selected, editForm);
              }
            });
    appointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) ->
                selection.appointmentId = selected == null ? 0 : selected.id());

    register.setOnAction(
        event -> {
          try {
            Patient patient =
                services.patientService().register(session, patientFromForm(registerForm, 0, true));
            populatePatientForm(patient, registerForm);
            feedback.setText("Patient registered");
            refreshPatients(
                services, session, patientList, selection, feedback, patientSearch.getText());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient registration is temporarily unavailable");
          }
        });

    updatePatient.setOnAction(
        event -> {
          try {
            requireSelection(selection.patientId, "Select a patient first");
            Patient selected = patientList.getSelectionModel().getSelectedItem();
            Patient updated =
                services
                    .patientService()
                    .updateAdministrative(
                        session,
                        patientFromForm(
                            editForm, selection.patientId, selected == null || selected.active()));
            selection.patientId = updated.id();
            feedback.setText("Patient changes saved");
            refreshPatients(
                services, session, patientList, selection, feedback, patientSearch.getText());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient update is temporarily unavailable");
          }
        });

    deactivatePatient.setOnAction(
        event -> {
          try {
            requireSelection(selection.patientId, "Select a patient first");
            services.patientService().deactivateAdministrative(session, selection.patientId);
            feedback.setText("Patient deactivated");
            refreshPatients(
                services, session, patientList, selection, feedback, patientSearch.getText());
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient deactivation is temporarily unavailable");
          }
        });

    searchPatients.setOnAction(
        event ->
            refreshPatients(
                services, session, patientList, selection, feedback, patientSearch.getText()));
    clearPatientSearch.setOnAction(
        event -> {
          patientSearch.clear();
          refreshPatients(services, session, patientList, selection, feedback, "");
        });

    book.setOnAction(
        event -> {
          try {
            if (selection.patientId == 0 || doctor.getValue() == null) {
              throw new ValidationException("Select a patient and Doctor first");
            }
            Appointment appointment =
                services
                    .appointmentService()
                    .book(
                        session,
                        selection.patientId,
                        doctor.getValue().id(),
                        parseDateTime(startsAt.getText(), "Start time"),
                        parseDateTime(endsAt.getText(), "End time"));
            selection.appointmentId = appointment.id();
            feedback.setText("Appointment booked and awaiting Doctor acceptance");
            refreshAppointments(services, session, appointmentList, selection, feedback);
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
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Checkout is temporarily unavailable");
          }
        });

    revenueButton.setOnAction(
        event -> {
          try {
            RevenueSummary summary =
                services
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

    refresh.setOnAction(
        event -> {
          refreshDoctors(services, session, doctor, feedback);
          refreshPatients(
              services, session, patientList, selection, feedback, patientSearch.getText());
          refreshAppointments(services, session, appointmentList, selection, feedback);
        });

    Button logout = button("Log out", "logout-button");
    logout.setOnAction(event -> onLogout.run());
    HBox header = new HBox(12, new Label("RECEPTIONIST workspace"), refresh, logout);

    VBox registerContent = new VBox(10, patientGrid(registerForm), register);
    registerContent.setId("reception-patient-register-tab");
    Tab registerTab = new Tab("Register new patient", registerContent);
    registerTab.setClosable(false);

    HBox patientSearchBar = new HBox(8, patientSearch, searchPatients, clearPatientSearch);
    HBox editActions = new HBox(8, updatePatient, deactivatePatient);
    VBox manageContent =
        new VBox(10, patientSearchBar, patientList, patientGrid(editForm), editActions);
    manageContent.setId("reception-patient-manage-tab");
    Tab manageTab = new Tab("Search and manage patients", manageContent);
    manageTab.setClosable(false);

    TabPane patientTabs = new TabPane(registerTab, manageTab);
    patientTabs.setId("reception-patient-tabs");
    GridPane appointmentForm = new GridPane();
    appointmentForm.setHgap(8);
    appointmentForm.setVgap(8);
    appointmentForm.addRow(0, new Label("Doctor"), doctor);
    appointmentForm.addRow(1, new Label("Starts"), startsAt, new Label("Ends"), endsAt, book);
    appointmentForm.addRow(
        2, new Label("New start"), rescheduleStartsAt, new Label("New end"), rescheduleEndsAt);
    appointmentForm.addRow(3, reschedule, cancel);
    GridPane checkoutForm = new GridPane();
    checkoutForm.setHgap(8);
    checkoutForm.setVgap(8);
    checkoutForm.addRow(0, new Label("Amount"), charge, new Label("Method"), method, checkout);
    GridPane revenueForm = new GridPane();
    revenueForm.setHgap(8);
    revenueForm.setVgap(8);
    revenueForm.addRow(0, new Label("Date"), revenueDate, revenueButton, revenue);
    VBox content =
        new VBox(
            12,
            new Label("Patient directory and basic data"),
            patientTabs,
            new Label("Appointments across all Doctors"),
            appointmentForm,
            appointmentList,
            checkIn,
            new Label("Checkout"),
            checkoutForm,
            new Label("Daily revenue"),
            revenueForm,
            feedback);
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    BorderPane root = new BorderPane(scroll);
    root.setId("receptionist-workspace");
    root.setPadding(new Insets(24));
    root.setTop(header);
    refreshDoctors(services, session, doctor, feedback);
    refreshPatients(services, session, patientList, selection, feedback, "");
    refreshAppointments(services, session, appointmentList, selection, feedback);
    return root;
  }

  private static PatientForm patientForm(String prefix) {
    TextField patientId = field(prefix + "-id", "Generated after registration");
    patientId.setEditable(false);
    ComboBox<IdentityType> identityType =
        new ComboBox<>(FXCollections.observableArrayList(IdentityType.values()));
    identityType.setId(prefix + "-identity-type");
    ComboBox<CountryOption> issuingCountry =
        new ComboBox<>(FXCollections.observableArrayList(CountryOption.allCountries()));
    issuingCountry.setId(prefix + "-issuing-country");
    identityType
        .valueProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected == IdentityType.NRIC || selected == IdentityType.FIN) {
                CountryOption.fromCode("SG")
                    .ifPresent(country -> issuingCountry.getSelectionModel().select(country));
              }
            });
    identityType.getSelectionModel().select(IdentityType.NRIC);
    ComboBox<Sex> sex = new ComboBox<>(FXCollections.observableArrayList(Sex.values()));
    sex.setId(prefix + "-sex");
    return new PatientForm(
        patientId,
        identityType,
        field(prefix + "-identity-number", "Identity document number"),
        issuingCountry,
        field(prefix + "-first-name", "First name"),
        field(prefix + "-last-name", "Last name"),
        field(prefix + "-date-of-birth", "yyyy-MM-dd"),
        sex,
        field(prefix + "-phone", "Phone"),
        field(prefix + "-email", "Email"),
        field(prefix + "-address", "Address"),
        field(prefix + "-height", "Height (cm), optional"),
        field(prefix + "-weight", "Weight (kg), optional"));
  }

  private static GridPane patientGrid(PatientForm form) {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.addRow(0, new Label("Patient ID"), form.patientId());
    grid.addRow(
        1,
        new Label("Identity type"),
        form.identityType(),
        new Label("Identity number"),
        form.identityNumber());
    grid.addRow(2, new Label("Issuing country"), form.issuingCountry());
    grid.addRow(
        3, new Label("First name"), form.firstName(), new Label("Last name"), form.lastName());
    grid.addRow(4, new Label("Date of birth"), form.dateOfBirth(), new Label("Sex"), form.sex());
    grid.addRow(5, new Label("Phone"), form.phone(), new Label("Email"), form.email());
    grid.addRow(6, new Label("Address"), form.address());
    grid.addRow(
        7, new Label("Height (cm)"), form.height(), new Label("Weight (kg)"), form.weight());
    return grid;
  }

  private static Patient patientFromForm(PatientForm form, long id, boolean active) {
    CountryOption country = form.issuingCountry().getValue();
    return new Patient(
        id,
        form.identityType().getValue(),
        form.identityNumber().getText(),
        country == null ? null : country.code(),
        form.firstName().getText(),
        form.lastName().getText(),
        form.dateOfBirth().getText(),
        form.sex().getValue(),
        form.phone().getText(),
        form.email().getText(),
        form.address().getText(),
        parseOptionalMeasurement(form.height().getText(), "Height"),
        parseOptionalMeasurement(form.weight().getText(), "Weight"),
        active);
  }

  private static void populatePatientForm(Patient patient, PatientForm form) {
    form.patientId().setText(patient.displayedId());
    form.identityType().setValue(patient.identityType());
    form.identityNumber().setText(valueOrEmpty(patient.identityNumber()));
    form.issuingCountry().setValue(CountryOption.fromCode(patient.issuingCountry()).orElse(null));
    form.firstName().setText(valueOrEmpty(patient.firstName()));
    form.lastName().setText(valueOrEmpty(patient.lastName()));
    form.dateOfBirth().setText(valueOrEmpty(patient.dateOfBirth()));
    form.sex().setValue(patient.sex());
    form.phone().setText(valueOrEmpty(patient.phone()));
    form.email().setText(valueOrEmpty(patient.email()));
    form.address().setText(valueOrEmpty(patient.address()));
    form.height().setText(patient.heightCm() == null ? "" : patient.heightCm().toString());
    form.weight().setText(patient.weightKg() == null ? "" : patient.weightKg().toString());
  }

  private static void clearPatientForm(PatientForm form) {
    clear(
        form.patientId(),
        form.identityNumber(),
        form.firstName(),
        form.lastName(),
        form.dateOfBirth(),
        form.phone(),
        form.email(),
        form.address(),
        form.height(),
        form.weight());
    form.identityType().getSelectionModel().clearSelection();
    form.issuingCountry().getSelectionModel().clearSelection();
    form.sex().getSelectionModel().clearSelection();
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
      TextField dateOfBirth,
      ComboBox<Sex> sex,
      TextField phone,
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
