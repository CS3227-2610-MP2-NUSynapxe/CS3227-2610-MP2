package nusynapxe.ui;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Shared patient, interval, and appointment-action dialog used by staff views. */
final class AppointmentDialog {
  private static final String PATIENT_LABEL = "Patient";
  private static final Map<String, AtomicLong> EDITOR_GENERATIONS = new ConcurrentHashMap<>();

  private AppointmentDialog() {
    throw new AssertionError("Utility class");
  }

  /** Opens a Doctor-owned appointment creation dialog with an accepted initial state. */
  static void showCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showCreate(
        services,
        session,
        doctorId,
        initialStart,
        workspaceFeedback,
        onUpdated,
        ClinicClock.system(),
        ClinicTaskRunner.immediate());
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    showCreate(
        services,
        session,
        doctorId,
        initialStart,
        workspaceFeedback,
        onUpdated,
        ClinicClock.system(),
        taskRunner);
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      java.time.Clock clock,
      ClinicTaskRunner taskRunner) {
    LocalDateTime start =
        initialStart == null ? ClinicClock.now(clock).withSecond(0).withNano(0) : initialStart;
    start = nearestHalfHour(start);
    showEditor(
        services,
        session,
        null,
        doctorId,
        start,
        "doctor-calendar-appointment-dialog",
        "Add appointment",
        "Create appointment",
        false,
        workspaceFeedback,
        onUpdated,
        taskRunner);
  }

  /** Opens a Receptionist booking dialog for a Doctor and optional Calendar slot. */
  static void showReceptionistCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showReceptionistCreate(
        services,
        session,
        doctorId,
        initialStart,
        workspaceFeedback,
        onUpdated,
        ClinicTaskRunner.immediate());
  }

  static void showReceptionistCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    LocalDateTime start = nearestHalfHour(initialStart);
    showEditor(
        services,
        session,
        null,
        doctorId,
        start,
        "reception-calendar-appointment-dialog",
        "Book appointment",
        "Book appointment",
        false,
        workspaceFeedback,
        onUpdated,
        taskRunner);
  }

  /** Opens a shared appointment editor for a Doctor-owned appointment. */
  static void showDoctorEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showDoctorEdit(
        services,
        session,
        appointmentId,
        workspaceFeedback,
        onUpdated,
        ClinicTaskRunner.immediate());
  }

  static void showDoctorEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    showEdit(
        services,
        session,
        appointmentId,
        "doctor-calendar-appointment-dialog",
        "Appointment details",
        true,
        workspaceFeedback,
        onUpdated,
        taskRunner);
  }

  /** Opens the shared receptionist rescheduling and cancellation dialog. */
  static void showReceptionistEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showReceptionistEdit(
        services,
        session,
        appointmentId,
        workspaceFeedback,
        onUpdated,
        ClinicTaskRunner.immediate());
  }

  static void showReceptionistEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    showEdit(
        services,
        session,
        appointmentId,
        "reception-reschedule-dialog",
        "Reschedule appointment",
        false,
        workspaceFeedback,
        onUpdated,
        taskRunner);
  }

  /** Creates the half-hour time controls used by inline receptionist booking. */
  static TimeFields timeSelector(String id) {
    ComboBox<String> hours = UiComponents.compactSelector();
    hours.setId(id + "-hour");
    ComboBox<String> minutes = UiComponents.compactSelector();
    minutes.setId(id + "-minute");
    hours.setPromptText("HH");
    minutes.setPromptText("mm");
    for (int hour = 0; hour < 24; hour++) {
      hours.getItems().add(String.format(Locale.ROOT, "%02d", hour));
    }
    minutes.getItems().addAll("00", "30");
    hours.getSelectionModel().select(0);
    minutes.getSelectionModel().select(0);
    HBox view = new HBox(4, hours, new Label(":"), minutes);
    view.setId(id);
    view.getStyleClass().add("appointment-time-field");
    HBox.setHgrow(hours, javafx.scene.layout.Priority.ALWAYS);
    HBox.setHgrow(minutes, javafx.scene.layout.Priority.ALWAYS);
    hours.setMaxWidth(Double.MAX_VALUE);
    minutes.setMaxWidth(Double.MAX_VALUE);
    return new TimeFields(hours, minutes, view);
  }

  /** Converts a date picker and half-hour controls into an appointment timestamp. */
  static LocalDateTime parseDateTime(DatePicker date, TimeFields time, String fieldName) {
    if (date.getValue() == null) {
      throw new ValidationException("Appointment date is required");
    }
    String hourValue = time.hours().getValue();
    String minuteValue = time.minutes().getValue();
    if (hourValue == null || minuteValue == null) {
      throw new ValidationException(fieldName + " must use a valid time");
    }
    try {
      return LocalDateTime.of(
          date.getValue(),
          LocalTime.of(Integer.parseInt(hourValue), Integer.parseInt(minuteValue)));
    } catch (DateTimeException | NumberFormatException exception) {
      throw new ValidationException(fieldName + " must use a valid time", exception);
    }
  }

  /** Selects the nearest half-hour value in a shared time control. */
  static void selectTime(TimeFields fields, LocalTime time) {
    fields.hours().setValue(String.format(Locale.ROOT, "%02d", time.getHour()));
    fields.minutes().setValue(String.format(Locale.ROOT, "%02d", time.getMinute() < 30 ? 0 : 30));
  }

  private static void showEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      String prefix,
      String title,
      boolean doctorActions,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    long generation = nextGeneration(prefix);
    taskRunner.submit(
        () -> services.appointmentService().get(appointmentId),
        appointment -> {
          if (isCurrent(prefix, generation)) {
            showEditor(
                services,
                session,
                appointment,
                appointment.doctorId(),
                appointment.startsAt(),
                prefix,
                title,
                "Reschedule appointment",
                doctorActions,
                workspaceFeedback,
                onUpdated,
                taskRunner);
          }
        },
        failure -> {
          if (isCurrent(prefix, generation)) {
            workspaceFeedback.setText(message(failure));
          }
        });
  }

  private static void showEditor(
      ClinicServices services,
      Session session,
      Appointment appointment,
      long doctorId,
      LocalDateTime initialStart,
      String prefix,
      String title,
      String submitText,
      boolean doctorActions,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    Objects.requireNonNull(services, "services");
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(workspaceFeedback, "workspaceFeedback");
    Objects.requireNonNull(onUpdated, "onUpdated");
    long generation = nextGeneration(prefix);
    taskRunner.submit(
        () -> {
          Patient currentPatient =
              appointment == null
                  ? null
                  : services.patientService().getAdministrative(session, appointment.patientId());
          List<Patient> availablePatients =
              services.patientService().searchAdministrative(session, "").stream()
                  .filter(
                      patient ->
                          patient.active()
                              || currentPatient != null && patient.id() == currentPatient.id())
                  .toList();
          String doctorName =
              session.accountId() == doctorId && session.role() == nusynapxe.domain.Role.DOCTOR
                  ? session.username()
                  : services.accountService().listDoctors(session).stream()
                      .filter(doctor -> doctor.id() == doctorId)
                      .map(nusynapxe.domain.Account::displayName)
                      .findFirst()
                      .orElse("Assigned doctor");
          return new EditorData(appointment, currentPatient, availablePatients, doctorName);
        },
        loaded -> {
          if (isCurrent(prefix, generation)) {
            renderEditor(
                services,
                session,
                initialStart,
                doctorId,
                prefix,
                title,
                submitText,
                doctorActions,
                workspaceFeedback,
                onUpdated,
                taskRunner,
                generation,
                loaded);
          }
        },
        failure -> {
          if (isCurrent(prefix, generation)) {
            workspaceFeedback.setText(message(failure));
          }
        });
  }

  private static void renderEditor(
      ClinicServices services,
      Session session,
      LocalDateTime initialStart,
      long doctorId,
      String prefix,
      String title,
      String submitText,
      boolean doctorActions,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      long generation,
      EditorData loaded) {
    Appointment appointment = loaded.appointment();
    Patient currentPatient = loaded.currentPatient();
    SearchSuggestionField<Patient> patients =
        PatientDirectoryView.patientSearchField(prefix + "-patient");
    patients.setItems(loaded.availablePatients());
    if (currentPatient != null) {
      patients.select(currentPatient);
    } else if (!patients.getItems().isEmpty()) {
      patients.select(patients.getItems().getFirst());
    }
    patients.setDisable(appointment != null);

    DatePicker date = UiComponents.compactDatePicker(initialStart.toLocalDate());
    date.setId(prefix + "-date");
    TimeFields start = timeSelector(prefix + "-start");
    TimeFields end = timeSelector(prefix + "-end");
    selectTime(start, initialStart.toLocalTime());
    LocalDateTime initialEnd =
        appointment == null ? initialStart.plusMinutes(30) : appointment.endsAt();
    selectDateAndTime(date, end, initialEnd);

    Label patientDetails = patientDetails(prefix, currentPatient);
    Label assignedDoctor = new Label("Doctor: " + loaded.doctorName());
    assignedDoctor.setId(prefix + "-doctor");
    Label status =
        appointment == null
            ? new Label(
                session.role() == nusynapxe.domain.Role.DOCTOR
                    ? "New appointments created by a Doctor are accepted immediately."
                    : "New appointments await the assigned Doctor's acceptance.")
            : UiComponents.statusBadge(appointment.status().name());
    status.setId(prefix + "-status");
    status.setWrapText(true);

    Label feedback = UiComponents.feedback(prefix + "-feedback");
    Button submit = UiComponents.primaryButton(submitText, prefix + "-submit");
    Button cancel =
        appointment == null
            ? UiComponents.secondaryButton("Close", prefix + "-cancel")
            : UiComponents.dangerButton("Cancel appointment", prefix + "-cancel");
    Button accept = UiComponents.primaryButton("Accept", prefix + "-accept");
    Button decline = UiComponents.dangerButton("Decline", prefix + "-decline");
    boolean decisionVisible =
        doctorActions
            && appointment != null
            && (appointment.status() == AppointmentStatus.PENDING
                || appointment.status() == AppointmentStatus.ACCEPTED);
    accept.setVisible(decisionVisible);
    accept.setManaged(decisionVisible);
    accept.setDisable(appointment != null && appointment.status() == AppointmentStatus.ACCEPTED);
    decline.setVisible(decisionVisible);
    decline.setManaged(decisionVisible);
    Stage dialog = new Stage();
    BooleanSupplier isActive = () -> isCurrent(prefix, generation) && dialog.isShowing();
    submit.setOnAction(
        event ->
            save(
                services,
                session,
                appointment,
                doctorId,
                patients,
                date,
                start,
                end,
                workspaceFeedback,
                feedback,
                onUpdated,
                dialog,
                taskRunner,
                isActive));
    cancel.setOnAction(
        event -> {
          if (appointment == null) {
            dialog.close();
          } else {
            cancel(
                services,
                session,
                appointment,
                workspaceFeedback,
                feedback,
                onUpdated,
                dialog,
                taskRunner,
                isActive);
          }
        });
    accept.setOnAction(
        event ->
            decide(
                services,
                session,
                appointment,
                AppointmentStatus.ACCEPTED,
                workspaceFeedback,
                feedback,
                onUpdated,
                dialog,
                taskRunner,
                isActive));
    decline.setOnAction(
        event ->
            decide(
                services,
                session,
                appointment,
                AppointmentStatus.DECLINED,
                workspaceFeedback,
                feedback,
                onUpdated,
                dialog,
                taskRunner,
                isActive));

    GridPane interval = new GridPane();
    interval.setHgap(8);
    interval.setVgap(8);
    interval.addRow(0, UiComponents.fieldGroup("Date", date));
    interval.addRow(1, UiComponents.fieldGroup("Start", start.view()));
    interval.addRow(2, UiComponents.fieldGroup("End", end.view()));
    HBox actions = new HBox(8, submit, cancel);
    actions.setAlignment(Pos.CENTER_RIGHT);
    VBox content =
        new VBox(
            12,
            UiComponents.sectionHeading(title),
            UiComponents.fieldGroup(PATIENT_LABEL, patients),
            patientDetails,
            assignedDoctor,
            status,
            interval,
            new VBox(8, new HBox(8, accept, decline), actions));
    content.setId(prefix + "-content");
    content.setPadding(new Insets(18));
    ScrollPane scroll = new ScrollPane(content);
    scroll.setId(prefix + "-scroll");
    scroll.setFitToWidth(true);
    scroll.setPannable(true);
    Window owner = ownerFor(workspaceFeedback);
    if (owner != null) {
      dialog.initOwner(owner);
      dialog.initModality(Modality.WINDOW_MODAL);
    }
    dialog.setTitle(title);
    Scene scene =
        new Scene(
            UiComponents.notificationOverlay(scroll, feedback),
            520,
            appointment == null ? 490 : 540);
    UiComponents.applyStylesheet(scene);
    dialog.setScene(scene);
    dialog.show();
  }

  private static void save(
      ClinicServices services,
      Session session,
      Appointment appointment,
      long doctorId,
      SearchSuggestionField<Patient> patients,
      DatePicker date,
      TimeFields start,
      TimeFields end,
      Label workspaceFeedback,
      Label feedback,
      Runnable onUpdated,
      Stage dialog,
      ClinicTaskRunner taskRunner,
      BooleanSupplier isActive) {
    try {
      Patient patient = patients.getValue();
      if (patient == null) {
        throw new ValidationException("Select a patient first");
      }
      LocalDateTime startsAt = parseDateTime(date, start, "Start time");
      LocalDateTime endsAt = parseDateTime(date, end, "End time");
      taskRunner.submit(
          () -> {
            if (appointment == null) {
              services.appointmentService().book(session, patient.id(), doctorId, startsAt, endsAt);
            } else {
              services.appointmentService().reschedule(session, appointment.id(), startsAt, endsAt);
            }
            return null;
          },
          ignored -> {
            if (!isActive.getAsBoolean()) {
              return;
            }
            if (appointment == null) {
              UiComponents.showMessage(
                  workspaceFeedback,
                  session.role() == nusynapxe.domain.Role.DOCTOR
                      ? "Appointment created and accepted"
                      : "Appointment booked and awaiting Doctor acceptance");
            } else {
              workspaceFeedback.setText("Appointment rescheduled");
            }
            onUpdated.run();
            dialog.close();
          },
          failure -> {
            if (isActive.getAsBoolean()) {
              showOperationError(
                  feedback, failure, "Appointment changes are temporarily unavailable");
            }
          });
    } catch (ValidationException exception) {
      showOperationError(feedback, exception, "Appointment changes are temporarily unavailable");
    }
  }

  private static void cancel(
      ClinicServices services,
      Session session,
      Appointment appointment,
      Label workspaceFeedback,
      Label feedback,
      Runnable onUpdated,
      Stage dialog,
      ClinicTaskRunner taskRunner,
      BooleanSupplier isActive) {
    taskRunner.submit(
        () -> {
          services.appointmentService().cancel(session, appointment.id());
          return null;
        },
        ignored -> {
          if (!isActive.getAsBoolean()) {
            return;
          }
          workspaceFeedback.setText("Appointment cancelled");
          onUpdated.run();
          dialog.close();
        },
        failure -> {
          if (isActive.getAsBoolean()) {
            showOperationError(
                feedback, failure, "Appointment cancellation is temporarily unavailable");
          }
        });
  }

  private static void decide(
      ClinicServices services,
      Session session,
      Appointment appointment,
      AppointmentStatus decision,
      Label workspaceFeedback,
      Label feedback,
      Runnable onUpdated,
      Stage dialog,
      ClinicTaskRunner taskRunner,
      BooleanSupplier isActive) {
    taskRunner.submit(
        () -> {
          if (decision == AppointmentStatus.ACCEPTED) {
            services.appointmentService().accept(session, appointment.id());
          } else {
            services.appointmentService().decline(session, appointment.id());
          }
          return null;
        },
        ignored -> {
          if (!isActive.getAsBoolean()) {
            return;
          }
          workspaceFeedback.setText(
              decision == AppointmentStatus.ACCEPTED
                  ? "Appointment accepted"
                  : "Appointment declined");
          onUpdated.run();
          dialog.close();
        },
        failure -> {
          if (isActive.getAsBoolean()) {
            showOperationError(
                feedback, failure, "Appointment decision is temporarily unavailable");
          }
        });
  }

  private static void showOperationError(Label feedback, Throwable failure, String fallback) {
    UiComponents.showError(
        feedback,
        failure instanceof ValidationException || failure instanceof AuthorizationException
            ? failure.getMessage()
            : fallback);
    feedback.setVisible(true);
    feedback.setManaged(true);
  }

  private static long nextGeneration(String prefix) {
    return EDITOR_GENERATIONS
        .computeIfAbsent(prefix, ignored -> new AtomicLong())
        .incrementAndGet();
  }

  private static boolean isCurrent(String prefix, long generation) {
    AtomicLong current = EDITOR_GENERATIONS.get(prefix);
    return current != null && current.get() == generation;
  }

  private static Label patientDetails(String prefix, Patient patient) {
    String name =
        patient == null
            ? "Choose an active patient for this appointment."
            : (value(patient.firstName()) + " " + value(patient.lastName())).trim();
    if (patient != null && prefix.startsWith("reception-")) {
      name = patient.displayedId() + " | " + name + " | " + value(patient.email());
    }
    Label details = new Label(name);
    details.setId(prefix + "-patient-details");
    details.setWrapText(true);
    return details;
  }

  private static void selectDateAndTime(DatePicker date, TimeFields fields, LocalDateTime value) {
    date.setValue(value.toLocalDate());
    selectTime(fields, value.toLocalTime());
  }

  private static LocalDateTime nearestHalfHour(LocalDateTime value) {
    int minute = value.getMinute() < 30 ? 0 : 30;
    if (value.getHour() == 23 && minute == 30) {
      minute = 0;
    }
    return value.withMinute(minute).withSecond(0).withNano(0);
  }

  private static Window ownerFor(Label workspaceFeedback) {
    return workspaceFeedback.getScene() == null ? null : workspaceFeedback.getScene().getWindow();
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }

  private static String message(Throwable exception) {
    return exception.getMessage() == null
        ? "The appointment operation could not be completed"
        : exception.getMessage();
  }

  private record EditorData(
      Appointment appointment,
      Patient currentPatient,
      List<Patient> availablePatients,
      String doctorName) {
    private EditorData {
      Objects.requireNonNull(availablePatients, "availablePatients");
      Objects.requireNonNull(doctorName, "doctorName");
    }
  }

  /** Shared half-hour controls for appointment forms. */
  record TimeFields(ComboBox<String> hours, ComboBox<String> minutes, HBox view) {
    TimeFields {
      Objects.requireNonNull(hours, "hours");
      Objects.requireNonNull(minutes, "minutes");
      Objects.requireNonNull(view, "view");
    }
  }
}
