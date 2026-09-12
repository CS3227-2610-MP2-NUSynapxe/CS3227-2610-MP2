package nusynapxe.ui;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
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
    Objects.requireNonNull(initialStart, "initialStart");
    DatePicker date = new DatePicker(initialStart.toLocalDate());
    date.setId(PREFIX + "-date");
    AppointmentDialog.TimeFields start = AppointmentDialog.timeSelector(PREFIX + "-start");
    AppointmentDialog.TimeFields end = AppointmentDialog.timeSelector(PREFIX + "-end");
    AppointmentDialog.selectTime(start, initialStart.toLocalTime());
    AppointmentDialog.selectTime(end, initialStart.plusMinutes(30).toLocalTime());
    Label feedback = UiComponents.feedback(PREFIX + "-feedback");
    Button submit = UiComponents.primaryButton("Block time", PREFIX + "-submit");
    Button cancel = UiComponents.secondaryButton("Cancel", PREFIX + "-cancel");
    Stage dialog = dialogStage(workspaceFeedback, "Block time");
    submit.setOnAction(
        event -> {
          try {
            LocalDateTime startsAt = AppointmentDialog.parseDateTime(date, start, "Start time");
            LocalDateTime endsAt = AppointmentDialog.parseDateTime(date, end, "End time");
            if (!endsAt.isAfter(startsAt)) {
              throw new ValidationException("End time must be after start time");
            }
            services.appointmentService().blockTimeOff(session, startsAt, endsAt);
            UiComponents.showMessage(workspaceFeedback, "Time blocked");
            onUpdated.run();
            dialog.close();
          } catch (ValidationException | AuthorizationException exception) {
            showError(feedback, exception.getMessage(), "The interval could not be blocked");
          } catch (SQLException exception) {
            showError(feedback, null, "Time off is temporarily unavailable");
          }
        });
    cancel.setOnAction(event -> dialog.close());
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
    Stage dialog = dialogStage(workspaceFeedback, "Blocked time");
    Label interval =
        new Label(
            timeOff.startsAt().toLocalDate()
                + "\n"
                + timeOff.startsAt().toLocalTime()
                + " – "
                + timeOff.endsAt().toLocalTime());
    interval.setId("doctor-calendar-time-off-details-interval");
    Button remove =
        UiComponents.dangerButton("Remove blocked time", "doctor-calendar-time-off-remove");
    Button close = UiComponents.secondaryButton("Close", "doctor-calendar-time-off-close");
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
          try {
            services.appointmentService().removeTimeOff(session, timeOff.id());
            UiComponents.showMessage(workspaceFeedback, "Blocked time removed");
            onUpdated.run();
            dialog.close();
          } catch (SQLException | ValidationException | AuthorizationException exception) {
            showError(
                workspaceFeedback, exception.getMessage(), "Blocked time could not be removed");
          }
        });
    close.setOnAction(event -> dialog.close());
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
