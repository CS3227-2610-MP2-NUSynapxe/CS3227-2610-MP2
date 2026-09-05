package nusynapxe.ui;

import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Shared patient, interval, and appointment-action dialog used by staff views. */
final class AppointmentDialog {
  private static final String PATIENT_LABEL = "Patient";

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
    LocalDateTime start =
        initialStart == null
            ? LocalDateTime.now(CalendarService.CLINIC_ZONE).withSecond(0).withNano(0)
            : initialStart;
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
        onUpdated);
  }

  /** Opens a shared appointment editor for a Doctor-owned appointment. */
  static void showDoctorEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showEdit(
        services,
        session,
        appointmentId,
        "doctor-calendar-appointment-dialog",
        "Appointment details",
        true,
        workspaceFeedback,
        onUpdated);
  }

  /** Opens the shared receptionist rescheduling and cancellation dialog. */
  static void showReceptionistEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showEdit(
        services,
        session,
        appointmentId,
        "reception-reschedule-dialog",
        "Reschedule appointment",
        false,
        workspaceFeedback,
        onUpdated);
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
      Runnable onUpdated) {
    try {
      Appointment appointment = services.appointmentService().get(appointmentId);
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
          onUpdated);
    } catch (SQLException | ValidationException | AuthorizationException exception) {
      workspaceFeedback.setText(message(exception));
    }
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
      Runnable onUpdated) {
    Objects.requireNonNull(services, "services");
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(workspaceFeedback, "workspaceFeedback");
    Objects.requireNonNull(onUpdated, "onUpdated");
    try {
      Patient currentPatient =
          appointment == null
              ? null
              : services.patientService().getAdministrative(session, appointment.patientId());
      ComboBox<Patient> patients = UiComponents.compactSelector();
      patients.setId(prefix + "-patient");
      patients.setItems(
          FXCollections.observableArrayList(
              services.patientService().searchAdministrative(session, "").stream()
                  .filter(
                      patient ->
                          patient.active()
                              || currentPatient != null && patient.id() == currentPatient.id())
                  .toList()));
      PatientDirectoryView.makePatientSearchable(patients);
      if (currentPatient != null) {
        patients.getSelectionModel().select(currentPatient);
      } else if (!patients.getItems().isEmpty()) {
        patients.getSelectionModel().selectFirst();
      }
      patients.setDisable(appointment != null);

      DatePicker date = new DatePicker(initialStart.toLocalDate());
      date.setId(prefix + "-date");
      TimeFields start = timeSelector(prefix + "-start");
      TimeFields end = timeSelector(prefix + "-end");
      selectTime(start, initialStart.toLocalTime());
      LocalDateTime initialEnd =
          appointment == null ? initialStart.plusMinutes(30) : appointment.endsAt();
      selectDateAndTime(date, end, initialEnd);

      Label patientDetails = patientDetails(prefix, currentPatient);
      Label assignedDoctor = new Label("Assigned Doctor ID: " + doctorId);
      assignedDoctor.setId(prefix + "-doctor");
      Label status =
          appointment == null
              ? new Label("New appointments created by a Doctor are accepted immediately.")
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
                  dialog));
      cancel.setOnAction(
          event -> {
            if (appointment == null) {
              dialog.close();
            } else {
              cancel(
                  services, session, appointment, workspaceFeedback, feedback, onUpdated, dialog);
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
                  dialog));
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
                  dialog));

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
              new VBox(8, new HBox(8, accept, decline), actions),
              feedback);
      content.setId(prefix + "-content");
      content.setPadding(new Insets(18));
      Window owner = ownerFor(workspaceFeedback);
      if (owner != null) {
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
      }
      dialog.setTitle(title);
      Scene scene = new Scene(content, 520, appointment == null ? 490 : 540);
      UiComponents.applyStylesheet(scene);
      dialog.setScene(scene);
      dialog.show();
    } catch (SQLException | ValidationException | AuthorizationException exception) {
      workspaceFeedback.setText(message(exception));
    }
  }

  private static void save(
      ClinicServices services,
      Session session,
      Appointment appointment,
      long doctorId,
      ComboBox<Patient> patients,
      DatePicker date,
      TimeFields start,
      TimeFields end,
      Label workspaceFeedback,
      Label feedback,
      Runnable onUpdated,
      Stage dialog) {
    try {
      Patient patient = patients.getValue();
      if (patient == null) {
        throw new ValidationException("Select a patient first");
      }
      LocalDateTime startsAt = parseDateTime(date, start, "Start time");
      LocalDateTime endsAt = parseDateTime(date, end, "End time");
      if (appointment == null) {
        services.appointmentService().book(session, patient.id(), doctorId, startsAt, endsAt);
        workspaceFeedback.setText("Appointment created and accepted");
      } else {
        services.appointmentService().reschedule(session, appointment.id(), startsAt, endsAt);
        workspaceFeedback.setText("Appointment rescheduled");
      }
      onUpdated.run();
      dialog.close();
    } catch (ValidationException | AuthorizationException exception) {
      feedback.setText(message(exception));
      feedback.setVisible(true);
      feedback.setManaged(true);
    } catch (SQLException exception) {
      feedback.setText("Appointment changes are temporarily unavailable");
      feedback.setVisible(true);
      feedback.setManaged(true);
    }
  }

  private static void cancel(
      ClinicServices services,
      Session session,
      Appointment appointment,
      Label workspaceFeedback,
      Label feedback,
      Runnable onUpdated,
      Stage dialog) {
    try {
      services.appointmentService().cancel(session, appointment.id());
      workspaceFeedback.setText("Appointment cancelled");
      onUpdated.run();
      dialog.close();
    } catch (ValidationException | AuthorizationException exception) {
      feedback.setText(message(exception));
      feedback.setVisible(true);
      feedback.setManaged(true);
    } catch (SQLException exception) {
      feedback.setText("Appointment cancellation is temporarily unavailable");
      feedback.setVisible(true);
      feedback.setManaged(true);
    }
  }

  private static void decide(
      ClinicServices services,
      Session session,
      Appointment appointment,
      AppointmentStatus decision,
      Label workspaceFeedback,
      Label feedback,
      Runnable onUpdated,
      Stage dialog) {
    try {
      if (decision == AppointmentStatus.ACCEPTED) {
        services.appointmentService().accept(session, appointment.id());
        workspaceFeedback.setText("Appointment accepted");
      } else {
        services.appointmentService().decline(session, appointment.id());
        workspaceFeedback.setText("Appointment declined");
      }
      onUpdated.run();
      dialog.close();
    } catch (ValidationException | AuthorizationException exception) {
      feedback.setText(message(exception));
      feedback.setVisible(true);
      feedback.setManaged(true);
    } catch (SQLException exception) {
      feedback.setText("Appointment decision is temporarily unavailable");
      feedback.setVisible(true);
      feedback.setManaged(true);
    }
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

  private static String message(Exception exception) {
    return exception.getMessage() == null
        ? "The appointment operation could not be completed"
        : exception.getMessage();
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
