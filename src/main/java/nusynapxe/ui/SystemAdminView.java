package nusynapxe.ui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
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

  /**
   * Creates the account-management workspace.
   *
   * @param accounts service used to list and create staff accounts
   * @param session authenticated System Admin session
   * @param onLogout callback invoked when the administrator logs out
   * @return root node for the System Admin workspace
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(AccountService accounts, Session session, Runnable onLogout) {
    return create(accounts, session, onLogout, ClinicTaskRunner.immediate());
  }

  /**
   * Creates the account workspace with serialized background database work.
   *
   * @param accounts service used to list and create staff accounts
   * @param session authenticated System Admin session
   * @param onLogout callback invoked when the administrator logs out
   * @param taskRunner runner used for account mutations
   * @return root node for the System Admin workspace
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(
      AccountService accounts, Session session, Runnable onLogout, ClinicTaskRunner taskRunner) {
    TextField username = new TextField();
    username.setId("admin-account-username");
    TextField displayName = new TextField();
    displayName.setId("admin-account-display-name");
    UiComponents.PasswordInput passwordInput =
        UiComponents.passwordInput("admin-account-password", "Initial password");
    var password = passwordInput.field();
    UiComponents.PasswordInput confirmationInput =
        UiComponents.passwordInput("admin-account-confirm-password", "Confirm initial password");
    var confirmation = confirmationInput.field();
    ComboBox<Role> role = UiComponents.compactSelector();
    role.setItems(FXCollections.observableArrayList(Role.DOCTOR, Role.RECEPTIONIST));
    role.setId("admin-account-role");
    role.getSelectionModel().select(Role.DOCTOR);
    role.setCellFactory(view -> roleCell());
    role.setButtonCell(roleCell());
    Label feedback = UiComponents.feedback("admin-account-feedback");
    TableView<Account> accountTable = accountTable();
    Button create = UiComponents.primaryButton("Create account", "admin-account-submit");
    create.setOnAction(
        event -> {
          if (!password.getText().equals(confirmation.getText())) {
            UiComponents.showError(feedback, "Passwords do not match");
            return;
          }
          String submittedUsername = username.getText();
          String submittedDisplayName = displayName.getText();
          Role submittedRole = role.getValue();
          String submittedPasswordText = password.getText();
          char[] submittedPassword = submittedPasswordText.toCharArray();
          create.setDisable(true);
          taskRunner.submit(
              () -> {
                try {
                  accounts.createStaff(
                      session,
                      submittedUsername,
                      submittedDisplayName,
                      submittedRole,
                      submittedPassword);
                  return null;
                } finally {
                  Arrays.fill(submittedPassword, '\0');
                }
              },
              ignored -> {
                create.setDisable(false);
                UiComponents.showMessage(feedback, "Account created");
                if (username.getText().equals(submittedUsername)) {
                  username.clear();
                }
                if (displayName.getText().equals(submittedDisplayName)) {
                  displayName.clear();
                }
                if (password.getText().equals(submittedPasswordText)) {
                  password.clear();
                }
                if (confirmation.getText().equals(submittedPasswordText)) {
                  confirmation.clear();
                }
                refreshAccounts(accounts, session, accountTable, feedback, taskRunner);
              },
              failure -> {
                create.setDisable(false);
                showAccountError(feedback, failure);
              });
        });
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(event -> onLogout.run());
    HBox header =
        UiComponents.workspaceHeader("SYSTEM ADMIN workspace", session.username(), logout);
    BorderPane root = new BorderPane();
    root.setId("system-admin-workspace");
    root.getStyleClass().add("workspace-shell");
    root.setPadding(new Insets(24));
    root.setTop(header);
    VBox createCard =
        UiComponents.card(
            "admin-account-form-card",
            UiComponents.pageTitle("Staff accounts"),
            UiComponents.supportingText("Create a Doctor or Receptionist account."),
            UiComponents.inlineField("Username", username),
            UiComponents.inlineField("Display name", displayName),
            UiComponents.inlineField("Role", role),
            UiComponents.inlineField("Initial password", passwordInput.view()),
            UiComponents.inlineField("Confirm password", confirmationInput.view()),
            UiComponents.actionBar(create));
    createCard.getStyleClass().add("compact-form-card");
    VBox listCard =
        UiComponents.card(
            "admin-account-list-card",
            UiComponents.sectionHeading("Current staff accounts"),
            accountTable);
    VBox content = new VBox(18, createCard, listCard);
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    root.setCenter(scroll);
    refreshAccounts(accounts, session, accountTable, feedback, taskRunner);
    return UiComponents.notificationOverlay(root, feedback);
  }

  private static TableView<Account> accountTable() {
    TableView<Account> table = new TableView<>();
    table.setId("admin-account-list");
    table.getStyleClass().add("account-table");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setFixedCellSize(40);
    table.setPrefHeight(80);
    table.setMinHeight(80);
    table.setMaxHeight(260);
    table.setPlaceholder(
        UiComponents.emptyState("admin-account-empty", "No staff accounts are available yet."));

    TableColumn<Account, String> username = textColumn("Username", Account::username);
    TableColumn<Account, String> displayName = textColumn("Display Name", Account::displayName);
    TableColumn<Account, String> role =
        textColumn("Role", account -> UiComponents.humanizeStatus(account.role().name()));
    TableColumn<Account, String> status =
        textColumn("Status", account -> account.enabled() ? "Active" : "Disabled");
    status.setCellFactory(
        column ->
            new TableCell<>() {
              @Override
              protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                  setText(null);
                  setGraphic(null);
                } else {
                  setText(null);
                  setGraphic(UiComponents.statusBadge(value));
                }
              }
            });
    table.getColumns().addAll(List.of(username, displayName, role, status));
    return table;
  }

  private static TableColumn<Account, String> textColumn(
      String title, Function<Account, String> valueProvider) {
    TableColumn<Account, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
    return column;
  }

  private static ListCell<Role> roleCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(Role value, boolean empty) {
        super.updateItem(value, empty);
        setText(empty || value == null ? null : UiComponents.humanizeStatus(value.name()));
      }
    };
  }

  private static void refreshAccounts(
      AccountService accounts,
      Session session,
      TableView<Account> accountTable,
      Label feedback,
      ClinicTaskRunner taskRunner) {
    taskRunner.submit(
        () -> accounts.listAccounts(session),
        accountsList -> {
          accountTable.setItems(FXCollections.observableArrayList(accountsList));
          accountTable.getSelectionModel().clearSelection();
          int visibleRows = Math.min(Math.max(accountTable.getItems().size(), 1), 5);
          accountTable.setPrefHeight(40 + visibleRows * 40);
        },
        failure -> showAccountError(feedback, failure));
  }

  private static void showAccountError(Label feedback, Throwable failure) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, "Accounts are temporarily unavailable");
    }
  }
}
