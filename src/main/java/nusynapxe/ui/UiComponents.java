package nusynapxe.ui;

import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Small presentation-only factories shared by the JavaFX views. */
final class UiComponents {
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
    label
        .textProperty()
        .addListener(
            (observable, previousText, currentText) -> {
              boolean hasMessage = currentText != null && !currentText.isBlank();
              label.setVisible(hasMessage);
              label.setManaged(hasMessage);
            });
    return label;
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
