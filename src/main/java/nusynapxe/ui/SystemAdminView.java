package nusynapxe.ui;

import java.sql.SQLException;
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
import javafx.scene.control.PasswordField;
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
    role.getStyleClass().add("compact-selector");
    role.setCellFactory(view -> roleCell());
    role.setButtonCell(roleCell());
    Label feedback = UiComponents.feedback("admin-account-feedback");
    TableView<Account> accountTable = accountTable();
    Button create = UiComponents.primaryButton("Create account", "admin-account-submit");
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
            refreshAccounts(accounts, session, accountTable, feedback);
          } catch (ValidationException | AuthorizationException exception) {
            feedback.setText(exception.getMessage());
          } catch (SQLException exception) {
            feedback.setText("Accounts are temporarily unavailable");
          }
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
            UiComponents.supportingText(
                "Create a Doctor or Receptionist account with the minimum access it needs."),
            UiComponents.inlineField("Username", username),
            UiComponents.inlineField("Display name", displayName),
            UiComponents.inlineField("Role", role),
            UiComponents.inlineField("Initial password", password),
            UiComponents.actionBar(create),
            feedback);
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
    refreshAccounts(accounts, session, accountTable, feedback);
    return root;
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
      AccountService accounts, Session session, TableView<Account> accountTable, Label feedback) {
    try {
      accountTable.setItems(FXCollections.observableArrayList(accounts.listAccounts(session)));
      accountTable.getSelectionModel().clearSelection();
      int visibleRows = Math.min(Math.max(accountTable.getItems().size(), 1), 5);
      accountTable.setPrefHeight(40 + visibleRows * 40);
    } catch (SQLException exception) {
      feedback.setText("Accounts are temporarily unavailable");
    }
  }
}
