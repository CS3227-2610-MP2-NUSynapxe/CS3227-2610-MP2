package nusynapxe.ui;

import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Account;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.AccountService;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ValidationException;

/** Builds the System Admin staff-account workspace. */
public final class SystemAdminView {
  private SystemAdminView() {
    throw new AssertionError("Utility class");
  }

  /** Creates the account-management workspace. */
  public static Parent create(AccountService accounts, Session session, Runnable onLogout) {
    TextField username = new TextField();
    username.setId("admin-account-username");
    TextField displayName = new TextField();
    displayName.setId("admin-account-display-name");
    PasswordField password = new PasswordField();
    password.setId("admin-account-password");
    ComboBox<Role> role =
        new ComboBox<>(FXCollections.observableArrayList(Role.DOCTOR, Role.RECEPTIONIST));
    role.setId("admin-account-role");
    role.getSelectionModel().select(Role.DOCTOR);
    Label feedback = new Label();
    feedback.setId("admin-account-feedback");
    ListView<String> accountList = new ListView<>();
    accountList.setId("admin-account-list");
    Button create = new Button("Create account");
    create.setId("admin-account-submit");
    create.setOnAction(
        event -> {
          try {
            accounts.createStaff(
                session,
                username.getText(),
                displayName.getText(),
                role.getValue(),
                password.getText().toCharArray());
            feedback.setText("Account created");
            username.clear();
            displayName.clear();
            password.clear();
            refreshAccounts(accounts, session, accountList, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Accounts are temporarily unavailable");
          }
        });
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(event -> onLogout.run());
    Label title = new Label("SYSTEM ADMIN workspace");
    title.setId("workspace-title");
    Label identity = new Label("Signed in as " + session.username());
    identity.setId("workspace-identity");

    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);
    form.addRow(0, new Label("Username"), username);
    form.addRow(1, new Label("Display name"), displayName);
    form.addRow(2, new Label("Role"), role);
    form.addRow(3, new Label("Initial password"), password);
    form.add(create, 1, 4);
    HBox header = new HBox(12, title, identity, logout);
    BorderPane root = new BorderPane();
    root.setId("system-admin-workspace");
    root.setPadding(new Insets(24));
    root.setTop(header);
    root.setCenter(
        new VBox(
            12, new Label("Create Doctor or Receptionist account"), form, feedback, accountList));
    refreshAccounts(accounts, session, accountList, feedback);
    return root;
  }

  private static void refreshAccounts(
      AccountService accounts, Session session, ListView<String> accountList, Label feedback) {
    try {
      accountList.setItems(
          FXCollections.observableArrayList(
              accounts.listAccounts(session).stream().map(SystemAdminView::accountLabel).toList()));
    } catch (SQLException exception) {
      feedback.setText("Accounts are temporarily unavailable");
    }
  }

  private static String accountLabel(Account account) {
    return account.displayName()
        + " ("
        + account.role().name().replace('_', ' ')
        + ") - "
        + account.username();
  }
}
