package nusynapxe.ui;

import java.sql.SQLException;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthenticationService;

/** Builds the application login form. */
public final class LoginView {
  private LoginView() {
    throw new AssertionError("Utility class");
  }

  /** Creates a login view connected to an authentication service and success callback. */
  public static Parent create(AuthenticationService authentication, LoginSuccess onSuccess) {
    TextField username = new TextField();
    username.setId("login-username");
    username.setPromptText("Username");
    PasswordField password = new PasswordField();
    password.setId("login-password");
    password.setPromptText("Password");
    Label feedback = new Label();
    feedback.setId("login-feedback");
    Button submit = new Button("Log in");
    submit.setId("login-submit");
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

    VBox root =
        new VBox(
            10,
            new Label("NUSynapxe"),
            new Label("Clinic staff login"),
            username,
            password,
            submit,
            feedback);
    root.setId("login-view");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(32));
    root.setMaxWidth(360);
    return root;
  }

  /** Receives a successful authenticated session. */
  @FunctionalInterface
  public interface LoginSuccess {
    /** Handles the authenticated session. */
    void accept(Session session);
  }
}
