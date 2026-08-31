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
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.domain.Session;
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
    TextField firstName = field("reception-patient-first-name", "First name");
    TextField lastName = field("reception-patient-last-name", "Last name");
    TextField phone = field("reception-patient-phone", "Phone");
    TextField email = field("reception-patient-email", "Email");
    TextField billing = field("reception-patient-billing", "Billing information");
    Button register = new Button("Register patient");
    register.setId("reception-patient-register");
    ListView<Patient> patientList = new ListView<>();
    patientList.setId("reception-patient-list");

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
    TextField startsAt = field("reception-start", DATE_TIME_PATTERN);
    TextField endsAt = field("reception-end", DATE_TIME_PATTERN);
    Button book = new Button("Book appointment");
    book.setId("reception-book");
    TextField rescheduleStartsAt = field("reception-reschedule-start", DATE_TIME_PATTERN);
    TextField rescheduleEndsAt = field("reception-reschedule-end", DATE_TIME_PATTERN);
    Button reschedule = new Button("Reschedule selected");
    reschedule.setId("reception-reschedule");
    Button cancel = new Button("Cancel selected");
    cancel.setId("reception-cancel");
    ListView<Appointment> appointmentList = new ListView<>();
    appointmentList.setId("reception-appointment-list");
    Button checkIn = new Button("Check in selected");
    checkIn.setId("reception-check-in");
    TextField charge = field("reception-charge", "Amount");
    ComboBox<PaymentMethod> method =
        new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
    method.setId("reception-method");
    method.getSelectionModel().select(PaymentMethod.CASH);
    Button checkout = new Button("Complete checkout");
    checkout.setId("reception-checkout");
    TextField revenueDate = field("reception-revenue-date", "yyyy-MM-dd");
    Button revenueButton = new Button("Show revenue");
    revenueButton.setId("reception-revenue-submit");
    Label revenue = new Label();
    revenue.setId("reception-revenue");
    Label feedback = new Label();
    feedback.setId("reception-feedback");
    SelectionState selection = new SelectionState();

    Button refresh = new Button("Refresh");
    refresh.setId("reception-refresh");

    patientList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) ->
                selection.patientId = selected == null ? 0 : selected.id());
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
                services
                    .patientService()
                    .register(
                        session,
                        new Patient(
                            0,
                            firstName.getText(),
                            lastName.getText(),
                            "",
                            phone.getText(),
                            email.getText(),
                            "",
                            billing.getText()));
            selection.patientId = patient.id();
            feedback.setText("Patient registered");
            clear(firstName, lastName, phone, email, billing);
            refreshPatients(services, session, patientList, selection, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Patient registration is temporarily unavailable");
          }
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
          refreshPatients(services, session, patientList, selection, feedback);
          refreshAppointments(services, session, appointmentList, selection, feedback);
        });

    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(event -> onLogout.run());
    HBox header = new HBox(12, new Label("RECEPTIONIST workspace"), refresh, logout);
    GridPane patientForm = new GridPane();
    patientForm.setHgap(8);
    patientForm.setVgap(8);
    patientForm.addRow(0, new Label("First name"), firstName, new Label("Last name"), lastName);
    patientForm.addRow(1, new Label("Phone"), phone, new Label("Email"), email);
    patientForm.addRow(2, new Label("Billing"), billing, register);
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
            new Label("Patient administration"),
            patientForm,
            patientList,
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
    refreshPatients(services, session, patientList, selection, feedback);
    refreshAppointments(services, session, appointmentList, selection, feedback);
    return root;
  }

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
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
      Label feedback) {
    try {
      long selectedPatientId = selection.patientId;
      patientList.setItems(
          FXCollections.observableArrayList(services.patientService().listAdministrative(session)));
      selection.patientId = selectedPatientId;
      selectPatient(patientList, selection.patientId);
    } catch (SQLException exception) {
      feedback.setText("Patients are temporarily unavailable");
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

  private static void selectPatient(ListView<Patient> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).id() == id) {
        list.getSelectionModel().select(index);
        return;
      }
    }
  }

  private static void selectAppointment(ListView<Appointment> list, long id) {
    for (int index = 0; index < list.getItems().size(); index++) {
      if (list.getItems().get(index).id() == id) {
        list.getSelectionModel().select(index);
        return;
      }
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

  private static final class SelectionState {
    private long patientId;
    private long appointmentId;
  }
}
