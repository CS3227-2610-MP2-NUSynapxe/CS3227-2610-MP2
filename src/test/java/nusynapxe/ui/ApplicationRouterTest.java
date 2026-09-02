package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
  private Stage stage;

  @Override
  public void start(Stage stage) throws SQLException {
    this.stage = stage;
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
    Scene initialScene = stage.getScene();
    assertTrue(stage.isResizable());
    assertEquals(1200.0, initialScene.getWidth());
    assertEquals(760.0, initialScene.getHeight());
    assertEquals(980.0, stage.getMinWidth());
    assertEquals(640.0, stage.getMinHeight());
    assertTrue(
        stage.getScene().getStylesheets().stream()
            .anyMatch(stylesheet -> stylesheet.endsWith("ui.css")));
    interact(
        () -> {
          stage.setWidth(1000);
          stage.setHeight(660);
        });
    assertTrue(stage.getWidth() >= stage.getMinWidth());
    assertTrue(stage.getHeight() >= stage.getMinHeight());
    verifyThat("#setup-view", isVisible());
    enterSetupCredentials();
    fire("#setup-submit");
    waitForNode("#login-view");

    assertSame(initialScene, stage.getScene());
    verifyThat("#login-view", isVisible());
    verifyThat("#login-submit", hasText("Log in"));
    interact(
        () -> {
          Label feedback = lookup("#login-feedback").queryAs(Label.class);
          assertFalse(feedback.isVisible());
          assertFalse(feedback.isManaged());
        });
  }

  @Test
  void invalidLoginShowsGenericFeedbackAndValidLoginRoutesToWorkspace() {
    Scene initialScene = stage.getScene();
    verifyThat("#setup-view", isVisible());
    enterSetupCredentials();
    fire("#setup-submit");
    waitForNode("#login-view");
    assertSame(initialScene, stage.getScene());

    setText("#login-username", "admin");
    setText("#login-password", "wrong-pass");
    fire("#login-submit");
    verifyThat("#login-feedback", hasText("Invalid username or password"));
    interact(
        () -> {
          Label feedback = lookup("#login-feedback").queryAs(Label.class);
          assertTrue(feedback.isVisible());
          assertTrue(feedback.isManaged());
        });

    setText("#login-password", "secure-pass");
    fire("#login-submit");
    waitForNode("#system-admin-workspace");
    assertSame(initialScene, stage.getScene());
    verifyThat("#system-admin-workspace", isVisible());
    verifyThat("#workspace-header", isVisible());
    verifyThat("#app-brand", hasText("NUSynapxe"));
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
