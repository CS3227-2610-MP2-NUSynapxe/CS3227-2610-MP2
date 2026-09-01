package nusynapxe.ui;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class ApplicationRouterTest extends ApplicationTest {
  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("router.db"));
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
  void firstLaunchCreatesAdminAndRoutesToLogin() {
    verifyThat("#setup-view", isVisible());
    enterSetupCredentials();
    fire("#setup-submit");
    waitForNode("#login-view");

    verifyThat("#login-view", isVisible());
    verifyThat("#login-submit", hasText("Log in"));
  }

  @Test
  void invalidLoginShowsGenericFeedbackAndValidLoginRoutesToWorkspace() {
    verifyThat("#setup-view", isVisible());
    enterSetupCredentials();
    fire("#setup-submit");
    waitForNode("#login-view");

    setText("#login-username", "admin");
    setText("#login-password", "wrong-pass");
    fire("#login-submit");
    verifyThat("#login-feedback", hasText("Invalid username or password"));

    setText("#login-password", "secure-pass");
    fire("#login-submit");
    waitForNode("#system-admin-workspace");
    verifyThat("#system-admin-workspace", isVisible());
    verifyThat("#workspace-title", hasText("SYSTEM ADMIN workspace"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  private void waitForNode(String selector) {
    try {
      WaitForAsyncUtils.waitFor(
          60, TimeUnit.SECONDS, () -> lookup(selector).tryQuery().isPresent());
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for " + selector, exception);
    }
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  private void enterSetupCredentials() {
    interact(
        () -> {
          lookup("#setup-username").queryAs(TextInputControl.class).setText("admin");
          lookup("#setup-password").queryAs(TextInputControl.class).setText("secure-pass");
          lookup("#setup-confirm-password").queryAs(TextInputControl.class).setText("secure-pass");
        });
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }
}
