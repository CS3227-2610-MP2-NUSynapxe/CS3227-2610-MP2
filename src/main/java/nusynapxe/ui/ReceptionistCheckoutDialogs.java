package nusynapxe.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ValidationException;

/** Owns check-in and checkout modal lifecycles for the Receptionist workspace. */
final class ReceptionistCheckoutDialogs {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final ReceptionistDataLoader dataLoader;
  private final Label workspaceFeedback;
  private final Clock clinicClock;
  private final Consumer<Receipt> showReceipt;

  ReceptionistCheckoutDialogs(
      ReceptionistDataLoader dataLoader,
      Label workspaceFeedback,
      Clock clinicClock,
      Consumer<Receipt> showReceipt) {
    this.dataLoader = dataLoader;
    this.workspaceFeedback = workspaceFeedback;
    this.clinicClock = clinicClock;
    this.showReceipt = showReceipt;
  }

  void showCheckInDetailsDialog(long appointmentId, Runnable onUpdated) {
    dataLoader.loadCheckInDetails(
        appointmentId,
        loaded -> showCheckInDetailsDialog(loaded, appointmentId, onUpdated),
        failure ->
            showTaskError(
                workspaceFeedback, failure, "Check-in details are temporarily unavailable"));
  }

  private void showCheckInDetailsDialog(
      ReceptionistDataLoader.AppointmentDetails loaded, long appointmentId, Runnable onUpdated) {
    Appointment appointment = loaded.appointment();
    Label details = detailsLabel(loaded, "reception-check-in-details");
    Label feedback = label("reception-check-in-feedback");
    Button checkIn = button("Check in patient", "reception-check-in-submit");
    Runnable updateEligibility =
        () ->
            checkIn.setDisable(
                appointment.status() != AppointmentStatus.ACCEPTED
                    || clinicClock
                        .instant()
                        .atZone(clinicClock.getZone())
                        .toLocalDateTime()
                        .isBefore(appointment.startsAt()));
    updateEligibility.run();
    Timeline ticker =
        new Timeline(new KeyFrame(Duration.seconds(1), event -> updateEligibility.run()));
    ticker.setCycleCount(Timeline.INDEFINITE);
    Stage dialog = new Stage();
    checkIn.setOnAction(
        event -> {
          updateEligibility.run();
          if (checkIn.isDisable()) {
            return;
          }
          checkIn.setDisable(true);
          ticker.stop();
          dataLoader.checkIn(
              appointmentId,
              () -> {
                UiComponents.showMessage(workspaceFeedback, "Patient checked in");
                onUpdated.run();
                dialog.close();
              },
              failure -> {
                updateEligibility.run();
                ticker.play();
                showTaskError(feedback, failure, "Check-in is temporarily unavailable");
              });
        });
    VBox content = new VBox(12, new Label("Appointment details"), details, checkIn, feedback);
    showDialog(dialog, content, "Check-in details", 300);
    dialog.setOnHidden(event -> ticker.stop());
    ticker.play();
  }

  void showCheckoutDetailsDialog(long appointmentId, Runnable onUpdated) {
    dataLoader.loadCheckoutDetails(
        appointmentId,
        loaded -> showCheckoutDetailsDialog(loaded, appointmentId, onUpdated),
        failure ->
            showTaskError(
                workspaceFeedback, failure, "Checkout details are temporarily unavailable"));
  }

  private void showCheckoutDetailsDialog(
      ReceptionistDataLoader.AppointmentDetails loaded, long appointmentId, Runnable onUpdated) {
    Label details = detailsLabel(loaded, "reception-checkout-details");
    TextField charge = field("reception-charge", "Amount");
    ComboBox<PaymentMethod> method = UiComponents.compactSelector();
    method.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
    method.setId("reception-method");
    method.getSelectionModel().select(PaymentMethod.CASH);
    Button checkout = button("Complete checkout", "reception-checkout");
    Label feedback = label("reception-checkout-feedback");
    GridPane paymentForm = new GridPane();
    paymentForm.setHgap(8);
    paymentForm.setVgap(8);
    paymentForm.addRow(0, new Label("Amount"), charge);
    paymentForm.addRow(1, new Label("Method"), method);
    Stage dialog = new Stage();
    checkout.setOnAction(
        event -> {
          if (checkout.isDisable()) {
            return;
          }
          try {
            long amountMinor = parseMinor(charge.getText());
            PaymentMethod paymentMethod = method.getValue();
            setCheckoutBusy(checkout, charge, method, true);
            dataLoader.checkout(
                appointmentId,
                amountMinor,
                paymentMethod,
                receipt -> {
                  receipt.ifPresent(showReceipt);
                  UiComponents.showMessage(workspaceFeedback, "Checkout completed");
                  onUpdated.run();
                  dialog.close();
                },
                failure -> {
                  setCheckoutBusy(checkout, charge, method, false);
                  showTaskError(feedback, failure, "Checkout is temporarily unavailable");
                });
          } catch (ValidationException exception) {
            setCheckoutBusy(checkout, charge, method, false);
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
    VBox content =
        new VBox(12, new Label("Checkout appointment"), details, paymentForm, checkout, feedback);
    showDialog(dialog, content, "Checkout details", 400);
  }

  private Label detailsLabel(ReceptionistDataLoader.AppointmentDetails loaded, String id) {
    Appointment appointment = loaded.appointment();
    Patient patient = loaded.patient();
    Label details =
        new Label(
            valueOrEmpty(patient.firstName())
                + " "
                + valueOrEmpty(patient.lastName())
                + "\nEmail: "
                + valueOrEmpty(patient.email())
                + "\nPhone: "
                + valueOrEmpty(patient.phone())
                + "\nDoctor: "
                + loaded.doctorName()
                + "\nScheduled: "
                + appointment.startsAt().format(DATE_TIME_FORMAT)
                + " - "
                + appointment.endsAt().toLocalTime()
                + "\nStatus: "
                + appointment.status());
    details.setId(id);
    return details;
  }

  private void showDialog(Stage dialog, VBox content, String title, double height) {
    content.setPadding(new Insets(18));
    dialog.initOwner(workspaceFeedback.getScene().getWindow());
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle(title);
    dialog.setScene(new Scene(content, 500, height));
    UiComponents.applyStylesheet(dialog.getScene());
    dialog.show();
  }

  private static void setCheckoutBusy(
      Button checkout, TextField charge, ComboBox<PaymentMethod> method, boolean busy) {
    checkout.setDisable(busy);
    charge.setDisable(busy);
    method.setDisable(busy);
  }

  private static long parseMinor(String value) {
    if (value == null) {
      throw new ValidationException("Amount must be a positive number with at most two decimals");
    }
    try {
      long amount =
          new BigDecimal(value.trim())
              .setScale(2, RoundingMode.UNNECESSARY)
              .movePointRight(2)
              .longValueExact();
      if (amount <= 0) {
        throw new ValidationException("Amount must be a positive number with at most two decimals");
      }
      return amount;
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new ValidationException(
          "Amount must be a positive number with at most two decimals", exception);
    }
  }

  private static Label label(String id) {
    Label label = new Label();
    label.setId(id);
    return label;
  }

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
  }

  private static Button button(String text, String id) {
    Button button = new Button(text);
    button.setId(id);
    return button;
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private static void showTaskError(Label feedback, Throwable failure, String fallback) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, fallback);
    }
  }
}
