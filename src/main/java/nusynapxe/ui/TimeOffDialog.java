package nusynapxe.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Doctor-owned create and remove dialogs for explicit Calendar time off. */
final class TimeOffDialog {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
  private static final String PREFIX = "doctor-calendar-time-off-dialog";

  private TimeOffDialog() {
    throw new AssertionError("Utility class");
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showCreate(
        services,
        session,
        initialStart,
        workspaceFeedback,
        onUpdated,
        ClinicTaskRunner.immediate());
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    showCreate(
        services, session, initialStart, workspaceFeedback, onUpdated, taskRunner, () -> true);
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    Objects.requireNonNull(initialStart, "initialStart");
    Objects.requireNonNull(workspaceActive, "workspaceActive");
    DatePicker date = UiComponents.compactDatePicker(initialStart.toLocalDate());
    date.setId(PREFIX + "-date");
    AppointmentDialog.TimeFields start = AppointmentDialog.timeSelector(PREFIX + "-start");
    AppointmentDialog.TimeFields end = AppointmentDialog.timeSelector(PREFIX + "-end");
    AppointmentDialog.selectTime(start, initialStart.toLocalTime());
    AppointmentDialog.selectTime(end, initialStart.plusMinutes(30).toLocalTime());
    Label feedback = UiComponents.feedback(PREFIX + "-feedback");
    Button submit = UiComponents.primaryButton("Block time", PREFIX + "-submit");
    Button cancel = UiComponents.secondaryButton("Cancel", PREFIX + "-cancel");
    Stage dialog = dialogStage(workspaceFeedback, "Block time");
    boolean[] pending = {false};
    dialog.setOnCloseRequest(
        event -> {
          if (pending[0]) {
            event.consume();
          }
        });
    submit.setOnAction(
        event -> {
          try {
            LocalDateTime startsAt = AppointmentDialog.parseDateTime(date, start, "Start time");
            LocalDateTime endsAt = AppointmentDialog.parseDateTime(date, end, "End time");
            if (!endsAt.isAfter(startsAt)) {
              throw new ValidationException("End time must be after start time");
            }
            pending[0] = true;
            submit.setDisable(true);
            cancel.setDisable(true);
            taskRunner.submit(
                () -> {
                  services.appointmentService().blockTimeOff(session, startsAt, endsAt);
                  return null;
                },
                ignored -> {
                  pending[0] = false;
                  dialog.close();
                  if (workspaceActive.getAsBoolean()) {
                    UiComponents.showMessage(workspaceFeedback, "Time blocked");
                    onUpdated.run();
                  }
                },
                failure -> {
                  pending[0] = false;
                  submit.setDisable(false);
                  cancel.setDisable(false);
                  showError(
                      feedback,
                      failure instanceof ValidationException
                              || failure instanceof AuthorizationException
                          ? failure.getMessage()
                          : null,
                      "Time off is temporarily unavailable");
                });
          } catch (ValidationException exception) {
            showError(feedback, exception.getMessage(), "The interval could not be blocked");
          }
        });
    cancel.setOnAction(
        event -> {
          if (!pending[0]) {
            dialog.close();
          }
        });
    HBox actions = new HBox(8, cancel, submit);
    actions.setAlignment(Pos.CENTER_RIGHT);
    VBox content =
        new VBox(
            12,
            UiComponents.sectionHeading("Block time"),
            UiComponents.supportingText(
                "Blocked time prevents appointments from being scheduled in this interval."),
            UiComponents.fieldGroup("Date", date),
            UiComponents.fieldGroup("Start", start.view()),
            UiComponents.fieldGroup("End", end.view()),
            feedback,
            actions);
    content.setId(PREFIX + "-content");
    content.setPadding(new Insets(18));
    dialog.setScene(new Scene(content, 420, 430));
    UiComponents.applyStylesheet(dialog.getScene());
    dialog.show();
  }

  static void showDetails(
      ClinicServices services,
      Session session,
      DoctorTimeOff timeOff,
      Label workspaceFeedback,
      Runnable onUpdated) {
    showDetails(
        services, session, timeOff, workspaceFeedback, onUpdated, ClinicTaskRunner.immediate());
  }

  static void showDetails(
      ClinicServices services,
      Session session,
      DoctorTimeOff timeOff,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner) {
    showDetails(services, session, timeOff, workspaceFeedback, onUpdated, taskRunner, () -> true);
  }

  static void showDetails(
      ClinicServices services,
      Session session,
      DoctorTimeOff timeOff,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    Objects.requireNonNull(workspaceActive, "workspaceActive");
    Stage dialog = dialogStage(workspaceFeedback, "Blocked time");
    Label interval =
        new Label(
            DATE_TIME_FORMAT.format(timeOff.startsAt())
                + " – "
                + DATE_TIME_FORMAT.format(timeOff.endsAt()));
    interval.setId("doctor-calendar-time-off-details-interval");
    Button remove =
        UiComponents.dangerButton("Remove blocked time", "doctor-calendar-time-off-remove");
    Button close = UiComponents.secondaryButton("Close", "doctor-calendar-time-off-close");
    boolean[] pending = {false};
    dialog.setOnCloseRequest(
        event -> {
          if (pending[0]) {
            event.consume();
          }
        });
    remove.setOnAction(
        event -> {
          Alert confirmation =
              new Alert(
                  Alert.AlertType.CONFIRMATION,
                  "Remove this blocked-time interval?",
                  ButtonType.CANCEL,
                  ButtonType.OK);
          confirmation.setTitle("Remove blocked time");
          confirmation.setHeaderText("Make this interval available again?");
          confirmation.getDialogPane().setId("doctor-calendar-time-off-remove-confirmation");
          confirmation
              .getDialogPane()
              .lookupButton(ButtonType.OK)
              .setId("doctor-calendar-time-off-remove-confirm");
          confirmation
              .getDialogPane()
              .lookupButton(ButtonType.CANCEL)
              .setId("doctor-calendar-time-off-remove-cancel");
          if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
          }
          pending[0] = true;
          remove.setDisable(true);
          close.setDisable(true);
          taskRunner.submit(
              () -> {
                services.appointmentService().removeTimeOff(session, timeOff.id());
                return null;
              },
              ignored -> {
                pending[0] = false;
                dialog.close();
                if (workspaceActive.getAsBoolean()) {
                  UiComponents.showMessage(workspaceFeedback, "Blocked time removed");
                  onUpdated.run();
                }
              },
              failure -> {
                pending[0] = false;
                remove.setDisable(false);
                close.setDisable(false);
                showError(
                    workspaceFeedback,
                    failure instanceof ValidationException
                            || failure instanceof AuthorizationException
                        ? failure.getMessage()
                        : null,
                    "Blocked time could not be removed");
              });
        });
    close.setOnAction(
        event -> {
          if (!pending[0]) {
            dialog.close();
          }
        });
    HBox actions = new HBox(8, close, remove);
    actions.setAlignment(Pos.CENTER_RIGHT);
    VBox content = new VBox(12, UiComponents.sectionHeading("Blocked time"), interval, actions);
    content.setId("doctor-calendar-time-off-details");
    content.setPadding(new Insets(18));
    dialog.setScene(new Scene(content, 400, 230));
    UiComponents.applyStylesheet(dialog.getScene());
    dialog.show();
  }

  private static Stage dialogStage(Label feedback, String title) {
    Stage dialog = new Stage();
    Window owner = feedback.getScene() == null ? null : feedback.getScene().getWindow();
    if (owner != null) {
      dialog.initOwner(owner);
      dialog.initModality(Modality.WINDOW_MODAL);
    }
    dialog.setTitle(title);
    return dialog;
  }

  private static void showError(Label feedback, String detail, String fallback) {
    UiComponents.showError(feedback, detail == null || detail.isBlank() ? fallback : detail);
    feedback.setVisible(true);
    feedback.setManaged(true);
  }
}
