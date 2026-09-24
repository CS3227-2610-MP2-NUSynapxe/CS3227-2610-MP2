package nusynapxe.ui;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Shared patient, interval, and appointment-action dialog used by staff views. */
final class AppointmentDialog {
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
        ClinicTaskRunner.immediate(),
        () -> true);
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
        taskRunner,
        () -> true);
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      Clock clock,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    LocalDateTime start =
        initialStart == null ? ClinicClock.now(clock).withSecond(0).withNano(0) : initialStart;
    showEditor(
        services,
        session,
        null,
        doctorId,
        nearestHalfHour(start),
        "doctor-calendar-appointment-dialog",
        "Add appointment",
        "Create appointment",
        false,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        workspaceActive);
  }

  static void showCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    showCreate(
        services,
        session,
        doctorId,
        initialStart,
        workspaceFeedback,
        onUpdated,
        clock,
        taskRunner,
        () -> true);
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
    showReceptionistCreate(
        services,
        session,
        doctorId,
        initialStart,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        () -> true);
  }

  static void showReceptionistCreate(
      ClinicServices services,
      Session session,
      long doctorId,
      LocalDateTime initialStart,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    showEditor(
        services,
        session,
        null,
        doctorId,
        nearestHalfHour(initialStart),
        "reception-calendar-appointment-dialog",
        "Book appointment",
        "Book appointment",
        false,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        workspaceActive);
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
    showDoctorEdit(
        services, session, appointmentId, workspaceFeedback, onUpdated, taskRunner, () -> true);
  }

  static void showDoctorEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    showEdit(
        services,
        session,
        appointmentId,
        "doctor-calendar-appointment-dialog",
        "Appointment details",
        true,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        workspaceActive);
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
    showReceptionistEdit(
        services, session, appointmentId, workspaceFeedback, onUpdated, taskRunner, () -> true);
  }

  static void showReceptionistEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    showEdit(
        services,
        session,
        appointmentId,
        "reception-reschedule-dialog",
        "Reschedule appointment",
        false,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        workspaceActive);
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
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    AppointmentDialogLoader.showEdit(
        services,
        session,
        appointmentId,
        prefix,
        title,
        doctorActions,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        workspaceActive);
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
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    AppointmentDialogLoader.showEditor(
        services,
        session,
        appointment,
        doctorId,
        initialStart,
        prefix,
        title,
        submitText,
        doctorActions,
        workspaceFeedback,
        onUpdated,
        taskRunner,
        workspaceActive);
  }

  private static LocalDateTime nearestHalfHour(LocalDateTime value) {
    int minute = value.getMinute() < 30 ? 0 : 30;
    if (value.getHour() == 23 && minute == 30) {
      minute = 0;
    }
    return value.withMinute(minute).withSecond(0).withNano(0);
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
