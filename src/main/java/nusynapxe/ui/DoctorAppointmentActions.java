package nusynapxe.ui;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Owns Doctor appointment actions, selection rendering, and stale-selection guards. */
final class DoctorAppointmentActions {
  private static final String APPOINTMENT_REQUIRED = "Select an appointment first";
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private DoctorAppointmentActions() {
    throw new AssertionError("Utility class");
  }

  static void configure(
      Button accept,
      Button decline,
      Button checkIn,
      Button reschedule,
      Button complete,
      Button saveConsultation,
      Button addPrescription,
      ClinicServices services,
      Session session,
      SelectionState selection,
      Label feedback,
      ClinicTaskRunner taskRunner,
      Runnable refreshDashboard,
      WorkspaceLifecycle lifecycle) {
    Button[] actions = {accept, decline, checkIn, reschedule, complete};
    accept.setOnAction(
        event ->
            runAppointmentAction(
                selection,
                feedback,
                taskRunner,
                id -> services.appointmentService().accept(session, id),
                "Appointment accepted",
                refreshDashboard,
                lifecycle,
                false,
                actions));
    decline.setOnAction(
        event ->
            runAppointmentAction(
                selection,
                feedback,
                taskRunner,
                id -> services.appointmentService().decline(session, id),
                "Appointment declined",
                refreshDashboard,
                lifecycle,
                false,
                actions));
    checkIn.setOnAction(
        event ->
            runAppointmentAction(
                selection,
                feedback,
                taskRunner,
                id -> services.appointmentService().checkIn(session, id),
                "Patient checked in",
                refreshDashboard,
                lifecycle,
                false,
                actions));
    reschedule.setOnAction(
        event -> {
          DoctorAppointmentTarget target;
          try {
            target = captureSelection(selection);
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, APPOINTMENT_REQUIRED);
            return;
          }
          AppointmentDialog.showDoctorEdit(
              services,
              session,
              target.appointmentId(),
              feedback,
              () -> {
                if (target.isCurrent(selection.appointmentId, selection.generation)) {
                  refreshDashboard.run();
                }
              },
              taskRunner,
              lifecycle::isActive);
        });
    complete.setOnAction(
        event ->
            runAppointmentAction(
                selection,
                feedback,
                taskRunner,
                id -> services.appointmentService().complete(session, id),
                "Appointment marked completed",
                refreshDashboard,
                lifecycle,
                true,
                accept,
                decline,
                checkIn,
                reschedule,
                complete,
                saveConsultation,
                addPrescription));
  }

  static void updateSelectionState(
      SelectionState selection,
      SelectionNodes nodes,
      Appointment appointment,
      Patient patient,
      Clock clock) {
    boolean selected = appointment != null && patient != null && selection.appointmentId != 0;
    nodes.summary().setVisible(selected);
    nodes.summary().setManaged(selected);
    nodes.noSelection().setVisible(!selected);
    nodes.noSelection().setManaged(!selected);
    nodes.selectedContent().getChildren().clear();
    setVisibleManaged(nodes.patientDetailsCard(), selected);
    setVisibleManaged(nodes.appointmentActionsCard(), false);
    setVisibleManaged(nodes.terminalStatusCard(), false);
    setActionVisibility(nodes, false);
    if (!selected) {
      nodes.patientDetailsCard().getChildren().clear();
      DoctorConsultationView.clearClinical(
          nodes.diagnosis(),
          nodes.consultationNotes(),
          nodes.followUpNotes(),
          nodes.prescriptions());
      setClinicalEditable(nodes, false);
      nodes.summary().getStyleClass().removeIf(style -> style.startsWith("status-"));
      return;
    }

    String readableStatus = displayStatus(appointment.status().name());
    nodes
        .summary()
        .setText(
            patientDisplayName(patient)
                + "  •  "
                + appointment.startsAt().format(DATE_TIME_FORMAT)
                + "  •  "
                + readableStatus);
    nodes
        .summary()
        .setAccessibleText(
            "Appointment for " + patientDisplayName(patient) + ", " + readableStatus);
    updateSummaryStatus(nodes.summary(), appointment.status());
    setClinicalEditable(nodes, false);
    GridPane patientDetails = PatientDirectoryView.patientDetailsGrid(patient);
    patientDetails.setId("doctor-patient-details");
    nodes
        .patientDetailsCard()
        .getChildren()
        .setAll(
            UiComponents.sectionHeading("Patient details"),
            patientDetails,
            UiComponents.actionBar(nodes.viewPatientHistory()));
    nodes.selectedContent().getChildren().add(nodes.patientDetailsCard());

    switch (appointment.status()) {
      case PENDING -> {
        setActionVisibility(nodes, true);
        nodes.checkIn().setVisible(false);
        nodes.checkIn().setManaged(false);
        nodes.accept().setDisable(false);
        nodes.decline().setDisable(false);
        nodes.reschedule().setDisable(false);
        nodes
            .appointmentActionsCard()
            .getChildren()
            .setAll(
                UiComponents.sectionHeading("Appointment actions"),
                UiComponents.actionBar(nodes.accept(), nodes.decline(), nodes.reschedule()));
        setVisibleManaged(nodes.appointmentActionsCard(), true);
        nodes.selectedContent().getChildren().add(nodes.appointmentActionsCard());
      }
      case ACCEPTED -> {
        setActionVisibility(nodes, true);
        nodes.accept().setVisible(false);
        nodes.accept().setManaged(false);
        nodes.decline().setDisable(false);
        nodes.reschedule().setDisable(false);
        boolean checkInEligible = !LocalDateTime.now(clock).isBefore(appointment.startsAt());
        nodes.checkIn().setDisable(!checkInEligible);
        nodes
            .appointmentActionsCard()
            .getChildren()
            .setAll(
                UiComponents.sectionHeading("Appointment actions"),
                UiComponents.actionBar(nodes.decline(), nodes.reschedule(), nodes.checkIn()));
        setVisibleManaged(nodes.appointmentActionsCard(), true);
        nodes.selectedContent().getChildren().add(nodes.appointmentActionsCard());
      }
      case CHECKED_IN -> {
        setClinicalEditable(nodes, true);
        nodes
            .selectedContent()
            .getChildren()
            .addAll(nodes.consultationCard(), nodes.prescriptionCard(), nodes.completionCard());
      }
      case COMPLETED, CHECKED_OUT -> {
        setClinicalEditable(nodes, false);
        nodes
            .selectedContent()
            .getChildren()
            .addAll(nodes.clinicalReadOnly(), nodes.consultationCard(), nodes.prescriptionCard());
      }
      case DECLINED, CANCELLED -> {
        nodes
            .terminalStatusMessage()
            .setText(
                "No Doctor actions are available for this " + readableStatus + " appointment.");
        setVisibleManaged(nodes.terminalStatusCard(), true);
        nodes.selectedContent().getChildren().add(nodes.terminalStatusCard());
      }
    }
  }

  static void selectDashboardAppointment(
      ClinicServices services,
      Session session,
      CalendarAppointment calendarAppointment,
      SelectionState selection,
      SelectionNodes nodes,
      DoctorConsultationPanel consultationPanel,
      Label feedback,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    selection.generation++;
    long generation = selection.generation;
    selection.appointmentId = 0;
    selection.patientId = 0;
    updateSelectionState(selection, nodes, null, null, clock);
    if (calendarAppointment == null) {
      applyDashboardSelection(selection, nodes, consultationPanel, clock, generation, null, null);
      return;
    }
    taskRunner.submit(
        () -> {
          Appointment appointment =
              services.appointmentService().get(calendarAppointment.appointmentId());
          if (appointment.doctorId() != session.accountId()) {
            throw new AuthorizationException("You are not allowed to view this appointment");
          }
          Patient patient =
              services.patientService().getAdministrative(session, appointment.patientId());
          return new DashboardSelection(appointment, patient);
        },
        resolved -> {
          if (generation == selection.generation) {
            applyDashboardSelection(
                selection,
                nodes,
                consultationPanel,
                clock,
                generation,
                resolved.appointment(),
                resolved.patient());
          }
        },
        failure -> {
          if (generation != selection.generation) {
            return;
          }
          UiComponents.showError(
              feedback, userMessage(failure, "Appointment is temporarily unavailable"));
          applyDashboardSelection(
              selection, nodes, consultationPanel, clock, generation, null, null);
        });
  }

  static void applyDashboardSelection(
      SelectionState selection,
      SelectionNodes nodes,
      DoctorConsultationPanel consultationPanel,
      Clock clock,
      long generation,
      Appointment appointment,
      Patient patient) {
    if (generation != selection.generation) {
      return;
    }
    selection.appointmentId = appointment == null || patient == null ? 0 : appointment.id();
    selection.patientId = patient == null ? 0 : patient.id();
    updateSelectionState(selection, nodes, appointment, patient, clock);
    consultationPanel.setOnClinicalLoaded(
        clinicalLoaded -> {
          if (!clinicalLoaded
              && appointment != null
              && appointment.status() == AppointmentStatus.CHECKED_IN) {
            setClinicalEditable(nodes, false);
          }
        });
    consultationPanel.load(appointment, generation);
  }

  static void setClinicalEditable(SelectionNodes nodes, boolean editable) {
    DoctorPatientFormView.setEditable(nodes.diagnosis(), editable);
    DoctorPatientFormView.setEditable(nodes.consultationNotes(), editable);
    DoctorPatientFormView.setEditable(nodes.followUpNotes(), editable);
    setVisibleManaged(nodes.consultationActions(), editable);
    setVisibleManaged(nodes.prescriptionForm(), editable);
    setVisibleManaged(nodes.prescriptionActions(), editable);
    setVisibleManaged(nodes.completionActions(), editable);
    setVisibleManaged(nodes.saveConsultation(), editable);
    setVisibleManaged(nodes.addPrescription(), editable);
    setVisibleManaged(nodes.complete(), editable);
  }

  private static void runAppointmentAction(
      SelectionState selection,
      Label feedback,
      ClinicTaskRunner taskRunner,
      AppointmentOperation operation,
      String successMessage,
      Runnable refreshDashboard,
      WorkspaceLifecycle lifecycle,
      boolean keepDisabledAfterSuccess,
      Button... actionButtons) {
    try {
      DoctorAppointmentTarget target = captureSelection(selection);
      setActionsDisabled(true, actionButtons);
      taskRunner.submit(
          () -> {
            operation.run(target.appointmentId());
            return null;
          },
          ignored -> {
            if (!lifecycle.isActive()
                || !target.isCurrent(selection.appointmentId, selection.generation)) {
              return;
            }
            if (!keepDisabledAfterSuccess) {
              setActionsDisabled(false, actionButtons);
            }
            feedback.setText(successMessage);
            refreshDashboard.run();
          },
          failure -> {
            if (!lifecycle.isActive()
                || !target.isCurrent(selection.appointmentId, selection.generation)) {
              return;
            }
            setActionsDisabled(false, actionButtons);
            showOperationError(feedback, failure);
          });
    } catch (ValidationException exception) {
      setActionsDisabled(false, actionButtons);
      UiComponents.showError(feedback, exception.getMessage());
    } catch (RejectedExecutionException exception) {
      setActionsDisabled(false, actionButtons);
      showOperationError(feedback, exception);
    }
  }

  private static void setActionsDisabled(boolean disabled, Button... buttons) {
    for (Button button : buttons) {
      if (button != null) {
        button.setDisable(disabled);
      }
    }
  }

  private static DoctorAppointmentTarget captureSelection(SelectionState selection) {
    try {
      return DoctorAppointmentTarget.capture(selection.appointmentId, selection.generation);
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(APPOINTMENT_REQUIRED, exception);
    }
  }

  private static void showOperationError(Label feedback, Throwable failure) {
    UiComponents.showError(
        feedback,
        failure instanceof ValidationException || failure instanceof AuthorizationException
            ? failure.getMessage()
            : "The requested operation is temporarily unavailable");
  }

  @FunctionalInterface
  private interface AppointmentOperation {
    void run(long appointmentId) throws Exception;
  }

  private static String displayStatus(String status) {
    return UiComponents.humanizeStatus(status);
  }

  private static String patientDisplayName(Patient patient) {
    String firstName = patient.firstName() == null ? "" : patient.firstName().trim();
    String lastName = patient.lastName() == null ? "" : patient.lastName().trim();
    String name = (firstName + " " + lastName).trim();
    return name.isBlank() ? "Patient" : name;
  }

  private static void updateSummaryStatus(Label summary, AppointmentStatus status) {
    summary.getStyleClass().removeIf(style -> style.startsWith("status-"));
    summary
        .getStyleClass()
        .add("status-" + status.name().toLowerCase(Locale.ROOT).replace('_', '-'));
  }

  private static void setActionVisibility(SelectionNodes nodes, boolean visible) {
    setVisibleManaged(nodes.accept(), visible);
    setVisibleManaged(nodes.decline(), visible);
    setVisibleManaged(nodes.checkIn(), visible);
    setVisibleManaged(nodes.reschedule(), visible);
  }

  private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
    node.setVisible(visible);
    node.setManaged(visible);
  }

  private static String userMessage(Throwable exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }

  record SelectionNodes(
      Label summary,
      Label noSelection,
      VBox selectedContent,
      VBox patientDetailsCard,
      VBox appointmentActionsCard,
      VBox terminalStatusCard,
      Label terminalStatusMessage,
      Label clinicalReadOnly,
      VBox consultationCard,
      VBox prescriptionCard,
      VBox completionCard,
      HBox consultationActions,
      VBox prescriptionForm,
      HBox prescriptionActions,
      HBox completionActions,
      Button accept,
      Button decline,
      Button checkIn,
      Button reschedule,
      Button saveConsultation,
      Button addPrescription,
      Button complete,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Button viewPatientHistory) {
    /* Record components define the complete selected-pane node bundle. */
  }

  static final class SelectionState {
    long appointmentId;
    long patientId;
    long generation;
  }

  private record DashboardSelection(Appointment appointment, Patient patient) {
    private DashboardSelection {
      Objects.requireNonNull(appointment, "appointment");
      Objects.requireNonNull(patient, "patient");
    }
  }
}
