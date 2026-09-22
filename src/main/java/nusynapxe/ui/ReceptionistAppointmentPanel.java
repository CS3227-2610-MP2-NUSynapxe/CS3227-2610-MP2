package nusynapxe.ui;

import java.util.function.BooleanSupplier;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Owns Receptionist appointment booking and appointment-management controls. */
final class ReceptionistAppointmentPanel {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final String DATE_LABEL = "Date";
  private static final String DOCTOR_LABEL = "Doctor";
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String ALL_STATUSES = "All statuses";
  private static final String PATIENT_LABEL = "Patient";
  private static final String PATIENT_NAME_ID = "Name, NRIC/FIN, phone, or email";
  private static final String SUBTAB_CONTENT_STYLE = "subtab-content";

  private final ClinicServices services;
  private final Session session;
  private final ReceptionistDataLoader dataLoader;
  private final Label feedback;
  private final ClinicTaskRunner taskRunner;
  private final BooleanSupplier workspaceActive;
  private final SearchSuggestionField<Patient> patientSearch;
  private final SearchSuggestionField<Account> doctor;
  private final SearchSuggestionField<Account> scheduleDoctor;
  private final DatePicker scheduleDate;
  private final ComboBox<String> scheduleStatus;
  private final TextField schedulePatient;
  private final Label scheduleSummary;
  private final TableView<AppointmentListRow> appointmentList;
  private final AppointmentDialog.TimeFields startsAt;
  private final AppointmentDialog.TimeFields endsAt;
  private final DatePicker appointmentDate;
  private final SelectionState selection = new SelectionState();
  private final VBox view;
  private Runnable refreshCheckout =
      () -> {
        /* Set by the workspace after panel composition. */
      };

  ReceptionistAppointmentPanel(
      ClinicServices services,
      Session session,
      ReceptionistDataLoader dataLoader,
      Label feedback,
      ClinicTaskRunner taskRunner) {
    this(services, session, dataLoader, feedback, taskRunner, () -> true);
  }

  ReceptionistAppointmentPanel(
      ClinicServices services,
      Session session,
      ReceptionistDataLoader dataLoader,
      Label feedback,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    this.services = services;
    this.session = session;
    this.dataLoader = dataLoader;
    this.feedback = feedback;
    this.taskRunner = taskRunner;
    this.workspaceActive = workspaceActive;
    patientSearch = PatientDirectoryView.patientSearchField("reception-appointment-patient");
    doctor = doctorSelector("reception-doctor", "Search doctors by name or username");
    startsAt = AppointmentDialog.timeSelector("reception-start");
    endsAt = AppointmentDialog.timeSelector("reception-end");
    appointmentDate = UiComponents.compactDatePicker();
    appointmentDate.setId("reception-appointment-date");
    appointmentDate.setPromptText("Appointment date");
    scheduleDate = UiComponents.compactDatePicker();
    scheduleDate.setId("reception-schedule-date");
    scheduleDate.setPromptText("Any date");
    scheduleDoctor = doctorSelector("reception-schedule-doctor", ALL_DOCTORS);
    scheduleStatus = UiComponents.compactSelector();
    scheduleStatus.getItems().add(ALL_STATUSES);
    for (AppointmentStatus status : AppointmentStatus.values()) {
      scheduleStatus.getItems().add(displayStatus(status));
    }
    scheduleStatus.setId("reception-schedule-status");
    scheduleStatus.getSelectionModel().select(ALL_STATUSES);
    schedulePatient = field("reception-schedule-patient", PATIENT_NAME_ID);
    scheduleSummary = new Label();
    scheduleSummary.setId("reception-schedule-summary");
    appointmentList = ReceptionistAppointmentView.appointmentTable("reception-appointment-list");

    Button book = button("Book appointment", "reception-book");
    Button reschedule = button("Reschedule selected", "reception-reschedule");
    Button cancel = button("Cancel selected", "reception-cancel");
    Button checkIn = button("Check in selected", "reception-check-in");
    configureSelection(reschedule, cancel, checkIn);
    configureFilters();
    configureActions(book, reschedule, cancel, checkIn);
    view = buildView(book, reschedule, cancel);
  }

  VBox content() {
    return view;
  }

  SearchSuggestionField<Patient> patientSearchField() {
    return patientSearch;
  }

  void setRefreshCheckout(Runnable refreshCheckout) {
    this.refreshCheckout = refreshCheckout;
  }

  void refreshPatients(long patientId) {
    PatientDirectoryView.refreshAppointmentPatients(
        services, session, patientSearch, feedback, patientId, taskRunner);
  }

  void refreshDoctors() {
    dataLoader.refreshDoctors(doctor, feedback);
    dataLoader.refreshDoctors(scheduleDoctor, feedback, false);
  }

  void refresh() {
    refreshSchedule();
  }

  private void configureSelection(Button reschedule, Button cancel, Button checkIn) {
    appointmentList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              Appointment appointment = selected == null ? null : selected.appointment();
              selection.appointmentId = appointment == null ? 0 : appointment.id();
              boolean editable =
                  appointment != null
                      && (appointment.status() == AppointmentStatus.PENDING
                          || appointment.status() == AppointmentStatus.ACCEPTED
                          || appointment.status() == AppointmentStatus.DECLINED);
              reschedule.setDisable(!editable);
              cancel.setDisable(!editable);
              checkIn.setDisable(
                  appointment == null || appointment.status() != AppointmentStatus.ACCEPTED);
            });
  }

  private void configureFilters() {
    scheduleDate.valueProperty().addListener((observable, previous, selected) -> refreshSchedule());
    scheduleDoctor
        .valueProperty()
        .addListener((observable, previous, selected) -> refreshSchedule());
    scheduleStatus
        .valueProperty()
        .addListener((observable, previous, selected) -> refreshSchedule());
    schedulePatient
        .textProperty()
        .addListener((observable, previous, selected) -> refreshSchedule());
  }

  private void configureActions(Button book, Button reschedule, Button cancel, Button checkIn) {
    book.setOnAction(
        event -> {
          try {
            if (patientSearch.getValue() == null || doctor.getValue() == null) {
              throw new ValidationException("Select a patient and Doctor first");
            }
            dataLoader.book(
                patientSearch.getValue().id(),
                doctor.getValue().id(),
                AppointmentDialog.parseDateTime(appointmentDate, startsAt, "Start time"),
                AppointmentDialog.parseDateTime(appointmentDate, endsAt, "End time"),
                appointment -> {
                  selection.appointmentId = appointment.id();
                  UiComponents.showMessage(
                      feedback, "Appointment booked and awaiting Doctor acceptance");
                  refreshSchedule();
                  refreshCheckout.run();
                },
                failure ->
                    showTaskError(
                        feedback, failure, "Appointment booking is temporarily unavailable"));
          } catch (ValidationException | IllegalArgumentException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
    reschedule.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId);
            AppointmentDialog.showReceptionistEdit(
                services,
                session,
                selection.appointmentId,
                feedback,
                () -> {
                  refreshSchedule();
                  refreshCheckout.run();
                },
                taskRunner,
                workspaceActive);
          } catch (ValidationException | AuthorizationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
    cancel.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId);
            dataLoader.cancel(
                selection.appointmentId,
                () -> {
                  UiComponents.showMessage(feedback, "Appointment cancelled");
                  refreshSchedule();
                  refreshCheckout.run();
                },
                failure ->
                    showTaskError(
                        feedback, failure, "Appointment cancellation is temporarily unavailable"));
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
    checkIn.setOnAction(
        event -> {
          try {
            requireSelection(selection.appointmentId);
            dataLoader.checkIn(
                selection.appointmentId,
                () -> {
                  UiComponents.showMessage(feedback, "Patient checked in");
                  refreshSchedule();
                  refreshCheckout.run();
                },
                failure -> showTaskError(feedback, failure, "Check-in is temporarily unavailable"));
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
  }

  private VBox buildView(Button book, Button reschedule, Button cancel) {
    GridPane appointmentForm = new GridPane();
    appointmentForm.getStyleClass().add("appointment-form-grid");
    appointmentForm.setHgap(16);
    appointmentForm.setVgap(14);
    addUniformColumns(appointmentForm, 3, 180, 240, 240);
    appointmentForm.add(UiComponents.fieldGroup(PATIENT_LABEL, patientSearch), 0, 0, 3, 1);
    appointmentForm.add(UiComponents.fieldGroup(DOCTOR_LABEL, doctor), 0, 1, 3, 1);
    VBox appointmentDateField = UiComponents.fieldGroup(DATE_LABEL, appointmentDate);
    VBox appointmentStartField = UiComponents.fieldGroup("Starts", startsAt.view());
    VBox appointmentEndField = UiComponents.fieldGroup("Ends", endsAt.view());
    appointmentDateField.getStyleClass().add("appointment-interval-field");
    appointmentStartField.getStyleClass().add("appointment-interval-field");
    appointmentEndField.getStyleClass().add("appointment-interval-field");
    appointmentForm.add(appointmentDateField, 0, 2);
    appointmentForm.add(appointmentStartField, 1, 2);
    appointmentForm.add(appointmentEndField, 2, 2);
    appointmentForm.add(book, 2, 3);
    GridPane scheduleFilters = new GridPane();
    scheduleFilters.getStyleClass().add("uniform-filter-grid");
    scheduleFilters.setHgap(12);
    scheduleFilters.setVgap(10);
    addUniformColumns(scheduleFilters, 4, 170, 220, 220);
    scheduleFilters.add(UiComponents.fieldGroup(DATE_LABEL, scheduleDate), 0, 0);
    scheduleFilters.add(UiComponents.fieldGroup(DOCTOR_LABEL, scheduleDoctor), 1, 0);
    scheduleFilters.add(UiComponents.fieldGroup("Status", scheduleStatus), 2, 0);
    scheduleFilters.add(UiComponents.fieldGroup(PATIENT_LABEL, schedulePatient), 3, 0);
    VBox bookingContent =
        UiComponents.card(
            "reception-booking-card",
            UiComponents.sectionHeading("Book appointment"),
            appointmentForm);
    VBox appointmentManageContent =
        UiComponents.card(
            "reception-appointment-results-card",
            scheduleFilters,
            scheduleSummary,
            appointmentList,
            UiComponents.actionBar(reschedule, cancel));
    VBox bookingTabContent = new VBox(bookingContent);
    bookingTabContent.getStyleClass().add(SUBTAB_CONTENT_STYLE);
    bookingTabContent.setPadding(new Insets(22, 0, 0, 0));
    VBox manageTabContent = new VBox(appointmentManageContent);
    manageTabContent.getStyleClass().add(SUBTAB_CONTENT_STYLE);
    manageTabContent.setPadding(new Insets(22, 0, 0, 0));
    javafx.scene.control.Tab bookingTab =
        new javafx.scene.control.Tab("Book appointment", bookingTabContent);
    bookingTab.setClosable(false);
    javafx.scene.control.Tab manageTab =
        new javafx.scene.control.Tab("Search and manage appointments", manageTabContent);
    manageTab.setClosable(false);
    javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane(bookingTab, manageTab);
    tabs.setId("reception-appointment-tabs");
    tabs.getStyleClass().add("sub-navigation");
    return new VBox(
        12,
        UiComponents.pageTitle("Appointments"),
        UiComponents.supportingText("Book, search, reschedule, and cancel appointments."),
        tabs);
  }

  private void refreshSchedule() {
    dataLoader.refreshSchedule(
        appointmentList,
        selection,
        feedback,
        scheduleDate.getValue(),
        scheduleDoctor.getValue() == null ? null : scheduleDoctor.getValue().id(),
        schedulePatient.getText(),
        scheduleStatus.getValue(),
        scheduleSummary);
  }

  private static SearchSuggestionField<Account> doctorSelector(String id, String prompt) {
    return new SearchSuggestionField<>(
        id,
        prompt,
        Account::displayName,
        account -> account.displayName() + " " + account.username());
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

  private static String displayStatus(Object value) {
    return UiComponents.humanizeStatus(value.toString());
  }

  private static void addUniformColumns(GridPane grid, int count, double... widths) {
    for (int index = 0; index < count; index++) {
      javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints();
      column.setMinWidth(widths[Math.min(index, widths.length - 1)]);
      column.setHgrow(javafx.scene.layout.Priority.SOMETIMES);
      grid.getColumnConstraints().add(column);
    }
  }

  private static void requireSelection(long id) {
    if (id == 0) {
      throw new ValidationException(APPOINTMENT_REQUIRED);
    }
  }

  private static void showTaskError(Label feedback, Throwable failure, String fallback) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, fallback);
    }
  }

  static final class SelectionState {
    long appointmentId;
  }
}
