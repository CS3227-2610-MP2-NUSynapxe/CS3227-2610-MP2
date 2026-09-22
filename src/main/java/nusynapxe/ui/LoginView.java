package nusynapxe.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
    return create(authentication, onSuccess, ClinicTaskRunner.immediate());
  }

  /** Creates a login view whose authentication runs through the supplied task runner. */
  public static Parent create(
      AuthenticationService authentication, LoginSuccess onSuccess, ClinicTaskRunner taskRunner) {
    TextField username = new TextField();
    username.setId("login-username");
    username.setPromptText("Username");
    UiComponents.PasswordInput passwordInput =
        UiComponents.passwordInput("login-password", "Password");
    var password = passwordInput.field();
    Label feedback = UiComponents.feedback("login-feedback");
    Button submit = UiComponents.primaryButton("Log in", "login-submit");
    submit.setDefaultButton(true);
    submit.setOnAction(
        event -> {
          taskRunner.submit(
              () -> authentication.login(username.getText(), password.getText().toCharArray()),
              session -> {
                if (session.isPresent()) {
                  feedback.setText("");
                  onSuccess.accept(session.orElseThrow());
                } else {
                  UiComponents.showError(feedback, "Invalid username or password");
                }
              },
              failure -> UiComponents.showError(feedback, "Login is temporarily unavailable"));
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
            UiComponents.fieldGroup("Password", passwordInput.view()),
            UiComponents.actionBar(submit));
    VBox content = new VBox(24, identity, form);
    content.getStyleClass().add("auth-content");
    StackPane root = UiComponents.notificationOverlay(content, feedback);
    root.setId("login-view");
    root.getStyleClass().add("auth-screen");
    StackPane.setMargin(content, new Insets(32));
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
