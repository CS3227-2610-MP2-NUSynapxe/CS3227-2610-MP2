package nusynapxe.ui;

import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Renders and manages the controls for a loaded appointment editor. */
final class AppointmentEditorView {
  private static final String PATIENT_LABEL = "Patient";

  private AppointmentEditorView() {
    throw new AssertionError("Utility class");
  }

  /** Builds and displays the editor after its patient and Doctor data has loaded. */
  static void show(
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
      BooleanSupplier workspaceActive,
      long generation,
      AppointmentDialogLoader.EditorData loaded) {
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
    AppointmentDialog.TimeFields start = AppointmentDialog.timeSelector(prefix + "-start");
    AppointmentDialog.TimeFields end = AppointmentDialog.timeSelector(prefix + "-end");
    AppointmentDialog.selectTime(start, initialStart.toLocalTime());
    LocalDateTime initialEnd =
        appointment == null ? initialStart.plusMinutes(30) : appointment.endsAt();
    selectDateAndTime(date, end, initialEnd);

    Label patientDetails = patientDetails(prefix, currentPatient);
    Label assignedDoctor = new Label("Doctor: " + loaded.doctorName());
    assignedDoctor.setId(prefix + "-doctor");
    Label status = statusLabel(session, appointment);
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
    BooleanSupplier isActive =
        () ->
            workspaceActive.getAsBoolean()
                && AppointmentDialogLoader.isCurrent(prefix, generation)
                && dialog.isShowing();
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

  private static Label statusLabel(Session session, Appointment appointment) {
    if (appointment != null) {
      return UiComponents.statusBadge(appointment.status().name());
    }
    return new Label(
        session.role() == Role.DOCTOR
            ? "New appointments created by a Doctor are accepted immediately."
            : "New appointments await the assigned Doctor's acceptance.");
  }

  private static void save(
      ClinicServices services,
      Session session,
      Appointment appointment,
      long doctorId,
      SearchSuggestionField<Patient> patients,
      DatePicker date,
      AppointmentDialog.TimeFields start,
      AppointmentDialog.TimeFields end,
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
      LocalDateTime startsAt = AppointmentDialog.parseDateTime(date, start, "Start time");
      LocalDateTime endsAt = AppointmentDialog.parseDateTime(date, end, "End time");
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
                  session.role() == Role.DOCTOR
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

  private static void selectDateAndTime(
      DatePicker date, AppointmentDialog.TimeFields fields, LocalDateTime value) {
    date.setValue(value.toLocalDate());
    AppointmentDialog.selectTime(fields, value.toLocalTime());
  }

  private static Window ownerFor(Label workspaceFeedback) {
    return workspaceFeedback.getScene() == null ? null : workspaceFeedback.getScene().getWindow();
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
