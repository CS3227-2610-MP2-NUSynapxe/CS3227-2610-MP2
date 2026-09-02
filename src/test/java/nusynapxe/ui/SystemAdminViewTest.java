package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class SystemAdminViewTest extends ApplicationTest {
  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("admin-ui.db"));
    database.open();
    new ApplicationRouter(stage, database).showInitial();
    stage.show();
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void systemAdminCreatesStaffAccountAndRefreshesList() {
    createInitialAdminAndLogin();
    verifyThat("#system-admin-workspace", isVisible());
    assertTrue(lookup("#admin-account-form-card").tryQuery().isPresent());
    assertTrue(lookup("#admin-account-list-card").tryQuery().isPresent());
    verifyThat("#app-brand", hasText("NUSynapxe"));
    ComboBox<?> role = lookup("#admin-account-role").queryAs(ComboBox.class);
    assertTrue(role.getStyleClass().contains("compact-selector"));
    assertEquals("Doctor", role.getButtonCell().getText());
    TableView<?> accountTable = lookup("#admin-account-list").queryAs(TableView.class);
    assertEquals(4, accountTable.getColumns().size());
    assertEquals("Username", accountTable.getColumns().get(0).getText());
    assertEquals("Display Name", accountTable.getColumns().get(1).getText());
    assertEquals("Role", accountTable.getColumns().get(2).getText());
    assertEquals("Status", accountTable.getColumns().get(3).getText());

    setText("#admin-account-username", "doctor");
    setText("#admin-account-display-name", "Dr. Ada");
    setText("#admin-account-password", "doctor-pass");
    fire("#admin-account-submit");

    verifyThat("#admin-account-feedback", hasText("Account created"));
    verifyThat("#admin-account-list", isVisible());
    assertEquals(2, accountTable.getItems().size());

    fire("#admin-account-submit");
    verifyThat("#admin-account-feedback", hasText("Username is required"));

    setText("#admin-account-username", "doctor");
    setText("#admin-account-display-name", "Dr. Duplicate");
    setText("#admin-account-password", "doctor-pass");
    fire("#admin-account-submit");
    verifyThat(
        "#admin-account-feedback",
        hasText("The username is already in use or the account could not be created"));
  }

  private void createInitialAdminAndLogin() {
    setText("#setup-username", "admin");
    setText("#setup-password", "secure-pass");
    setText("#setup-confirm-password", "secure-pass");
    fire("#setup-submit");
    WaitForAsyncUtils.waitForFxEvents();
    verifyThat("#login-view", isVisible());
    setText("#login-username", "admin");
    setText("#login-password", "secure-pass");
    fire("#login-submit");
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }
}
