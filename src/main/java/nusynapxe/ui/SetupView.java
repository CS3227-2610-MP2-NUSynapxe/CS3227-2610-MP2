package nusynapxe.ui;

import java.sql.SQLException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import nusynapxe.service.AccountService;
import nusynapxe.service.ValidationException;

/** Builds the first-run System Admin setup form. */
public final class SetupView {
  private SetupView() {
    throw new AssertionError("Utility class");
  }

  /** Creates a first-run setup view connected to account creation. */
  public static Parent create(AccountService accounts, SetupSuccess onSuccess) {
    TextField username = new TextField();
    username.setId("setup-username");
    username.setPromptText("Admin username");
    PasswordField password = new PasswordField();
    password.setId("setup-password");
    password.setPromptText("Password (8+ non-blank characters)");
    PasswordField confirmation = new PasswordField();
    confirmation.setId("setup-confirm-password");
    confirmation.setPromptText("Confirm password");
    Label feedback = new Label();
    feedback.setId("setup-feedback");
    Button submit = new Button("Create System Admin");
    submit.setId("setup-submit");
    submit.setDefaultButton(true);
    submit.setOnAction(
        event -> {
          if (!password.getText().equals(confirmation.getText())) {
            feedback.setText("Passwords do not match");
            return;
          }
          try {
            accounts.createInitialAdmin(username.getText(), password.getText().toCharArray());
            feedback.setText("");
            onSuccess.accept();
          } catch (ValidationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Account setup is temporarily unavailable");
          }
        });

    VBox root =
        new VBox(
            10,
            new Label("NUSynapxe"),
            new Label("Create the first System Admin account"),
            username,
            password,
            confirmation,
            submit,
            feedback);
    root.setId("setup-view");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(32));
    root.setMaxWidth(420);
    return root;
  }

  /** Receives a successful initial account creation. */
  @FunctionalInterface
  public interface SetupSuccess {
    /** Handles completion of first-run setup. */
    void accept();
  }
}
