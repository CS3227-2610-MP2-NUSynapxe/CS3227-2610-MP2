package nusynapxe.ui;

import java.sql.SQLException;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthenticationService;

/** Builds the application login form. */
public final class LoginView {
  private LoginView() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates a login view connected to an authentication service and success callback.
   *
   * @param authentication service used to authenticate submitted credentials
   * @param onSuccess callback invoked with a successful session
   * @return root node for the login form
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(AuthenticationService authentication, LoginSuccess onSuccess) {
    TextField username = new TextField();
    username.setId("login-username");
    username.setPromptText("Username");
    PasswordField password = new PasswordField();
    password.setId("login-password");
    password.setPromptText("Password");
    Label feedback = UiComponents.feedback("login-feedback");
    Button submit = UiComponents.primaryButton("Log in", "login-submit");
    submit.setDefaultButton(true);
    submit.setOnAction(
        event -> {
          try {
            Optional<Session> session =
                authentication.login(username.getText(), password.getText().toCharArray());
            if (session.isPresent()) {
              feedback.setText("");
              onSuccess.accept(session.orElseThrow());
            } else {
              feedback.setText("Invalid username or password");
            }
          } catch (SQLException exception) {
            feedback.setText("Login is temporarily unavailable");
          }
        });

    Label brand = new Label("NUSynapxe");
    brand.setId("app-brand");
    brand.getStyleClass().add("brand-name");
    Label tagline = new Label("Clinic staff login");
    tagline.getStyleClass().add("brand-tagline");
    VBox identity = new VBox(6, brand, tagline);
    identity.getStyleClass().add("auth-brand");
    VBox form =
        UiComponents.card(
            "login-form-card",
            UiComponents.pageTitle("Welcome back"),
            UiComponents.supportingText("Sign in to access your authorised clinic workspace."),
            UiComponents.fieldGroup("Username", username),
            UiComponents.fieldGroup("Password", password),
            UiComponents.actionBar(submit),
            feedback);
    VBox content = new VBox(24, identity, form);
    content.getStyleClass().add("auth-content");
    StackPane root = new StackPane(content);
    root.setId("login-view");
    root.getStyleClass().add("auth-screen");
    root.setPadding(new Insets(32));
    return root;
  }

  /** Receives a successful authenticated session. */
  @FunctionalInterface
  public interface LoginSuccess {
    /**
     * Handles the authenticated session.
     *
     * @param session newly authenticated session
     */
    void accept(Session session);
  }
}
