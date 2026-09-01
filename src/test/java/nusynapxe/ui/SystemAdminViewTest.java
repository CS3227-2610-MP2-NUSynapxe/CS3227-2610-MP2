package nusynapxe.ui;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import javafx.scene.control.Button;
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

    clickOn("#admin-account-username").write("doctor");
    clickOn("#admin-account-display-name").write("Dr. Ada");
    clickOn("#admin-account-password").write("doctor-pass");
    fire("#admin-account-submit");

    verifyThat("#admin-account-feedback", hasText("Account created"));
    verifyThat("#admin-account-list", isVisible());

    fire("#admin-account-submit");
    verifyThat("#admin-account-feedback", hasText("Username is required"));

    clickOn("#admin-account-username").write("doctor");
    clickOn("#admin-account-display-name").write("Dr. Duplicate");
    clickOn("#admin-account-password").write("doctor-pass");
    fire("#admin-account-submit");
    verifyThat(
        "#admin-account-feedback",
        hasText("The username is already in use or the account could not be created"));
  }

  private void createInitialAdminAndLogin() {
    clickOn("#setup-username").write("admin");
    clickOn("#setup-password").write("secure-pass");
    clickOn("#setup-confirm-password").write("secure-pass");
    fire("#setup-submit");
    WaitForAsyncUtils.waitForFxEvents();
    verifyThat("#login-view", isVisible());
    clickOn("#login-username").write("admin");
    clickOn("#login-password").write("secure-pass");
    fire("#login-submit");
    WaitForAsyncUtils.waitForFxEvents();
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }
}
