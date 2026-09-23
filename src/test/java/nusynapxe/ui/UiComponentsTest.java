package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/** Covers shared JavaFX component factory branches and presentation state changes. */
final class UiComponentsTest extends ApplicationTest {
  @Override
  public void start(Stage stage) {
    stage.setScene(new Scene(new StackPane(), 800, 600));
    stage.show();
  }

  @Test
  void componentFactoriesHandleOptionalControlsAndExistingStyles() {
    VBox card = UiComponents.card("component-card", new Label("content"));
    VBox cardWithoutId = UiComponents.card(null, new Label("content"));
    VBox blankIdCard = UiComponents.card(" ", new Label("content"));
    assertEquals("component-card", card.getId());
    assertEquals(null, cardWithoutId.getId());
    assertEquals(null, blankIdCard.getId());

    VBox nullField = UiComponents.fieldGroup("Optional", null);
    VBox dateField = UiComponents.fieldGroup("Date", new DatePicker());
    TextField textField = new TextField();
    VBox textFieldGroup = UiComponents.fieldGroup("Text", textField);
    VBox labelField = UiComponents.fieldGroup("Label", new Label("value"));
    assertEquals(1, nullField.getChildren().size());
    assertSame(textField, textFieldGroup.getChildren().get(1));
    assertEquals(Double.MAX_VALUE, textField.getMaxWidth());
    assertTrue(dateField.getChildren().get(1) instanceof DatePicker);
    assertTrue(labelField.getChildren().get(1) instanceof Label);

    HBox nullInline = UiComponents.inlineField("Optional", null);
    HBox regionInline = UiComponents.inlineField("Text", new TextField());
    assertEquals(1, nullInline.getChildren().size());
    assertTrue(regionInline.getChildren().get(1) instanceof Region);

    ComboBox<String> selector = UiComponents.compactSelector();
    UiComponents.applyCompactSelector(selector);
    assertEquals(1, selector.getStyleClass().stream().filter("compact-selector"::equals).count());
    DatePicker emptyDate = UiComponents.compactDatePicker();
    DatePicker initialDate = UiComponents.compactDatePicker(java.time.LocalDate.of(2026, 9, 23));
    UiComponents.applyCompactDatePicker(emptyDate);
    assertEquals(
        1, emptyDate.getStyleClass().stream().filter("compact-date-picker"::equals).count());
    assertEquals(java.time.LocalDate.of(2026, 9, 23), initialDate.getValue());

    assertEquals("Unknown status", UiComponents.humanizeStatus(null));
    assertEquals("Unknown status", UiComponents.humanizeStatus("  "));
    assertEquals("Checked In", UiComponents.humanizeStatus("CHECKED_IN"));
    assertEquals("Checked In", UiComponents.humanizeStatus("checked  in"));
  }

  @Test
  void feedbackPasswordAndStatusComponentsUpdateTheirState() {
    interact(
        () -> {
          Label feedback = UiComponents.feedback("feedback");
          UiComponents.showError(feedback, "Error");
          assertTrue(feedback.getStyleClass().contains("error-feedback"));
          assertTrue(feedback.isVisible());
          UiComponents.showMessage(feedback, "Message");
          assertFalse(feedback.getStyleClass().contains("error-feedback"));
          feedback.setText("");
          assertFalse(feedback.isVisible());
          UiComponents.showError(feedback, null);
          assertFalse(feedback.isVisible());

          UiComponents.PasswordInput password = UiComponents.passwordInput("password", "Secret");
          Button toggle = (Button) password.view().lookup("#password-toggle");
          assertFalse(password.view().lookup("#password-visible").isVisible());
          toggle.fire();
          assertTrue(password.view().lookup("#password-visible").isVisible());
          toggle.fire();
          assertFalse(password.view().lookup("#password-visible").isVisible());

          Label unknown = UiComponents.statusBadge(null);
          assertTrue(unknown.getStyleClass().contains("status-unknown"));
          UiComponents.updateStatusBadge(unknown, "CHECKED_IN");
          assertEquals("Checked In", unknown.getText());
          assertTrue(unknown.getStyleClass().contains("status-checked-in"));
        });
  }
}
