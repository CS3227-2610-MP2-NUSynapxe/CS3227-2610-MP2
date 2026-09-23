package nusynapxe.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.service.AccountService;
import nusynapxe.service.ValidationException;

/** Builds the first-run System Admin setup form. */
public final class SetupView {
  private SetupView() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates a first-run setup view connected to account creation.
   *
   * @param accounts service used to create the initial administrator
   * @param onSuccess callback invoked after setup succeeds
   * @return root node for the setup form
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(AccountService accounts, SetupSuccess onSuccess) {
    return create(accounts, onSuccess, ClinicTaskRunner.immediate());
  }

  /**
   * Creates a setup view whose account mutation runs through the supplied task runner.
   *
   * @param accounts service used to create the initial administrator
   * @param onSuccess callback invoked after setup succeeds
   * @param taskRunner runner used for account creation
   * @return root node for the setup form
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(
      AccountService accounts, SetupSuccess onSuccess, ClinicTaskRunner taskRunner) {
    TextField username = new TextField();
    username.setId("setup-username");
    username.setPromptText("Admin username");
    UiComponents.PasswordInput passwordInput =
        UiComponents.passwordInput("setup-password", "Password (8+ non-blank characters)");
    var password = passwordInput.field();
    UiComponents.PasswordInput confirmationInput =
        UiComponents.passwordInput("setup-confirm-password", "Confirm password");
    var confirmation = confirmationInput.field();
    Label feedback = UiComponents.feedback("setup-feedback");
    Button submit = UiComponents.primaryButton("Create System Admin", "setup-submit");
    submit.setDefaultButton(true);
    submit.setOnAction(
        event -> {
          if (!password.getText().equals(confirmation.getText())) {
            UiComponents.showError(feedback, "Passwords do not match");
            return;
          }
          taskRunner.submit(
              () -> {
                accounts.createInitialAdmin(username.getText(), password.getText().toCharArray());
                return null;
              },
              ignored -> {
                feedback.setText("");
                onSuccess.accept();
              },
              failure -> {
                if (failure instanceof ValidationException) {
                  UiComponents.showError(feedback, failure.getMessage());
                } else {
                  UiComponents.showError(feedback, "Account setup is temporarily unavailable");
                }
              });
        });

    Label brand = new Label("NUSynapxe");
    brand.setId("app-brand");
    brand.getStyleClass().add("brand-name");
    Label tagline = new Label("Create the first System Admin account");
    tagline.getStyleClass().add("brand-tagline");
    VBox identity = new VBox(6, brand, tagline);
    identity.getStyleClass().add("auth-brand");
    VBox form =
        UiComponents.card(
            "setup-form-card",
            UiComponents.pageTitle("Set up NUSynapxe"),
            UiComponents.supportingText(
                "Create the administrator account for this clinic installation."),
            UiComponents.fieldGroup("Admin username", username),
            UiComponents.fieldGroup("Password", passwordInput.view()),
            UiComponents.fieldGroup("Confirm password", confirmationInput.view()),
            UiComponents.actionBar(submit));
    VBox content = new VBox(24, identity, form);
    content.getStyleClass().add("auth-content");
    StackPane root = UiComponents.notificationOverlay(content, feedback);
    root.setId("setup-view");
    root.getStyleClass().add("auth-screen");
    StackPane.setMargin(content, new Insets(32));
    return root;
  }

  /** Receives a successful initial account creation. */
  @FunctionalInterface
  public interface SetupSuccess {
    /** Handles completion of first-run setup. */
    void accept();
  }
}
