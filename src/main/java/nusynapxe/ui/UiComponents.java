package nusynapxe.ui;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Small presentation-only factories shared by the JavaFX views. */
final class UiComponents {
  private static final String ERROR_FEEDBACK_STYLE = "error-feedback";
  private static final String SHOW_PASSWORD = "Show password";

  private UiComponents() {
    throw new AssertionError("Utility class");
  }

  /** Creates a page-level heading with the shared visual style. */
  static Label pageTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("page-title");
    label.setWrapText(true);
    return label;
  }

  /** Creates a short explanatory paragraph for a page or card. */
  static Label supportingText(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("supporting-text");
    label.setWrapText(true);
    label.setMaxWidth(Double.MAX_VALUE);
    return label;
  }

  /** Creates a section heading for a focused operational area. */
  static Label sectionHeading(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("section-heading");
    label.setWrapText(true);
    return label;
  }

  /** Creates a bordered content surface containing the supplied nodes. */
  static VBox card(String id, Node... content) {
    VBox card = new VBox(12);
    card.getStyleClass().add("card");
    if (id != null && !id.isBlank()) {
      card.setId(id);
    }
    card.getChildren().addAll(content);
    return card;
  }

  /** Loads the shared stylesheet onto a JavaFX scene. */
  static void applyStylesheet(Scene scene) {
    scene
        .getStylesheets()
        .add(
            Objects.requireNonNull(
                    UiComponents.class.getResource("/nusynapxe/ui.css"),
                    "Shared UI stylesheet is missing")
                .toExternalForm());
  }

  /** Creates a labelled field group and associates the label with its control. */
  static VBox fieldGroup(String labelText, Node control) {
    Label label = new Label(labelText);
    label.getStyleClass().add("field-label");
    if (control != null) {
      label.setLabelFor(control);
      if (control instanceof Region region && !(control instanceof DatePicker)) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
    }
    VBox group = new VBox(6, label, control);
    group.getStyleClass().add("field-group");
    return group;
  }

  /** Creates a compact labelled row for forms where horizontal space is available. */
  static HBox inlineField(String labelText, Node control) {
    Label label = new Label(labelText);
    label.getStyleClass().add("field-label");
    if (control != null) {
      label.setLabelFor(control);
    }
    HBox group = new HBox(12, label, control);
    group.getStyleClass().add("inline-field-group");
    if (control instanceof Region region) {
      HBox.setHgrow(region, Priority.ALWAYS);
    }
    return group;
  }

  /** Creates a horizontal action group with consistent spacing and alignment. */
  static HBox actionBar(Node... actions) {
    HBox bar = new HBox(10, actions);
    bar.getStyleClass().add("action-bar");
    bar.setAlignment(Pos.CENTER_LEFT);
    return bar;
  }

  /** Creates a primary action button. */
  static Button primaryButton(String text, String id) {
    return actionButton(text, id, "primary-action");
  }

  /** Creates a secondary action button. */
  static Button secondaryButton(String text, String id) {
    return actionButton(text, id, "secondary-action");
  }

  /** Creates a destructive or state-changing action button. */
  static Button dangerButton(String text, String id) {
    return actionButton(text, id, "danger-action");
  }

  /** Creates a ComboBox using the compact selector style shared by application forms. */
  static <T> ComboBox<T> compactSelector() {
    ComboBox<T> selector = new ComboBox<>();
    applyCompactSelector(selector);
    return selector;
  }

  /** Creates a DatePicker using the shared minimal field style. */
  static DatePicker compactDatePicker() {
    return compactDatePicker(null);
  }

  /** Creates an optionally prefilled DatePicker using the shared minimal field style. */
  static DatePicker compactDatePicker(LocalDate initialValue) {
    DatePicker picker = initialValue == null ? new DatePicker() : new DatePicker(initialValue);
    applyCompactDatePicker(picker);
    return picker;
  }

  /** Applies the shared minimal field style to an existing DatePicker. */
  static void applyCompactDatePicker(DatePicker picker) {
    Objects.requireNonNull(picker, "picker");
    if (!picker.getStyleClass().contains("compact-date-picker")) {
      picker.getStyleClass().add("compact-date-picker");
    }
  }

  /** Applies the shared compact selector style to an existing ComboBox. */
  static void applyCompactSelector(ComboBox<?> selector) {
    Objects.requireNonNull(selector, "selector");
    if (!selector.getStyleClass().contains("compact-selector")) {
      selector.getStyleClass().add("compact-selector");
    }
  }

  /** Creates a feedback banner that can be associated with nearby actions. */
  static Label feedback(String id) {
    Label label = new Label();
    label.setId(id);
    label.getStyleClass().add("feedback-banner");
    label.setWrapText(true);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setVisible(false);
    label.setManaged(false);
    PauseTransition dismiss = new PauseTransition(Duration.seconds(6));
    FadeTransition fade = new FadeTransition(Duration.millis(350), label);
    fade.setFromValue(1);
    fade.setToValue(0);
    dismiss.setOnFinished(event -> fade.playFromStart());
    fade.setOnFinished(
        event -> {
          label.setText("");
          label.setOpacity(1);
        });
    label
        .textProperty()
        .addListener(
            (observable, previousText, currentText) -> {
              boolean hasMessage = currentText != null && !currentText.isBlank();
              label.setVisible(hasMessage);
              label.setManaged(hasMessage);
              if (hasMessage) {
                fade.stop();
                label.setOpacity(1);
                dismiss.playFromStart();
              } else {
                dismiss.stop();
                fade.stop();
                label.setOpacity(1);
                label.getStyleClass().remove(ERROR_FEEDBACK_STYLE);
              }
            });
    return label;
  }

  /** Presents a feedback label as a centered notification at the top window edge. */
  static StackPane notificationArea(Label feedback) {
    StackPane area = new StackPane(feedback);
    area.getStyleClass().add("notification-area");
    area.setPickOnBounds(false);
    StackPane.setAlignment(feedback, Pos.TOP_CENTER);
    return area;
  }

  /** Places transient feedback above page content at the top centre of its window. */
  static StackPane notificationOverlay(Node content, Label feedback) {
    StackPane root = new StackPane(content, notificationArea(feedback));
    StackPane.setAlignment(content, Pos.CENTER);
    return root;
  }

  /** Shows an error using the shared red notification treatment. */
  static void showError(Label feedback, String message) {
    if (!feedback.getStyleClass().contains(ERROR_FEEDBACK_STYLE)) {
      feedback.getStyleClass().add(ERROR_FEEDBACK_STYLE);
    }
    feedback.setText(message);
  }

  /** Shows a non-error operation notice. */
  static void showMessage(Label feedback, String message) {
    feedback.getStyleClass().remove(ERROR_FEEDBACK_STYLE);
    feedback.setText(message);
  }

  /** Creates a password field with an accessible show/hide control. */
  static PasswordInput passwordInput(String id, String promptText) {
    PasswordField hidden = new PasswordField();
    hidden.setId(id);
    hidden.setPromptText(promptText);
    TextField visible = new TextField();
    visible.setId(id + "-visible");
    visible.setPromptText(promptText);
    visible.textProperty().bindBidirectional(hidden.textProperty());
    visible.setVisible(false);
    visible.setManaged(false);

    StackPane fields = new StackPane(hidden, visible);
    Button toggle = new Button("\ud83d\udc41");
    toggle.setId(id + "-toggle");
    toggle.getStyleClass().add("password-visibility-toggle");
    toggle.setAccessibleText(SHOW_PASSWORD);
    toggle.setTooltip(new Tooltip(SHOW_PASSWORD));
    toggle.setOnAction(
        event -> {
          boolean reveal = !visible.isVisible();
          visible.setVisible(reveal);
          visible.setManaged(reveal);
          hidden.setVisible(!reveal);
          hidden.setManaged(!reveal);
          toggle.setAccessibleText(reveal ? "Hide password" : SHOW_PASSWORD);
          toggle.getTooltip().setText(reveal ? "Hide password" : SHOW_PASSWORD);
          (reveal ? visible : hidden).requestFocus();
          (reveal ? visible : hidden).positionCaret(hidden.getText().length());
        });
    StackPane.setAlignment(toggle, Pos.CENTER_RIGHT);
    StackPane.setMargin(toggle, new javafx.geometry.Insets(0, 7, 0, 0));
    fields.getChildren().add(toggle);
    fields.getStyleClass().add("password-input");
    fields.setMaxWidth(Double.MAX_VALUE);
    return new PasswordInput(hidden, fields);
  }

  /** Password value control and its combined show/hide presentation. */
  record PasswordInput(PasswordField field, StackPane view) {
    // Groups the semantic password control with its presentation node.
  }

  /** Creates a visible empty-state message for a list or result surface. */
  static Label emptyState(String id, String text) {
    Label label = new Label(text);
    label.setId(id);
    label.getStyleClass().add("empty-state");
    label.setWrapText(true);
    label.setMaxWidth(Double.MAX_VALUE);
    return label;
  }

  /** Creates the common authenticated-workspace header. */
  static HBox workspaceHeader(String roleText, String username, Button logout) {
    Label brand = new Label("NUSynapxe");
    brand.setId("app-brand");
    brand.getStyleClass().add("brand-name");
    Label role = new Label(roleText);
    role.setId("workspace-title");
    role.getStyleClass().add("workspace-role");
    VBox branding = new VBox(3, brand, role);
    branding.getStyleClass().add("brand-block");

    Label identity = new Label("Signed in as " + username);
    identity.setId("workspace-identity");
    identity.getStyleClass().add("workspace-identity");
    identity.setWrapText(true);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    logout.getStyleClass().add("logout-action");
    HBox header = new HBox(16, branding, spacer, identity, logout);
    header.setId("workspace-header");
    header.getStyleClass().add("workspace-header");
    header.setAlignment(Pos.CENTER_LEFT);
    return header;
  }

  /** Creates a status badge with readable text and a semantic style class. */
  static Label statusBadge(String status) {
    Label badge = new Label(humanizeStatus(status));
    badge.getStyleClass().add("status-badge");
    addStatusClass(badge, status);
    return badge;
  }

  /** Updates the text and semantic style class of an existing status badge. */
  static void updateStatusBadge(Label badge, String status) {
    badge.setText(humanizeStatus(status));
    badge.getStyleClass().removeIf(style -> style.startsWith("status-"));
    addStatusClass(badge, status);
  }

  private static Button actionButton(String text, String id, String styleClass) {
    Button button = new Button(text);
    button.setId(id);
    button.getStyleClass().add(styleClass);
    return button;
  }

  private static void addStatusClass(Label badge, String status) {
    String normalized =
        status == null
            ? "unknown"
            : status.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    badge.getStyleClass().add("status-" + normalized);
  }

  /** Converts an enum-like status into a readable title-cased label. */
  static String humanizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "Unknown status";
    }
    String[] words = status.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
    StringBuilder readable = new StringBuilder();
    for (String word : words) {
      if (!word.isBlank()) {
        if (readable.length() > 0) {
          readable.append(' ');
        }
        readable.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return readable.toString();
  }
}
