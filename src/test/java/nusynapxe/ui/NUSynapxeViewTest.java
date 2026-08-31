package nusynapxe.ui;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class NUSynapxeViewTest extends ApplicationTest {
  @Override
  public void start(Stage stage) {
    stage.setScene(new Scene(NUSynapxeView.create(Path.of("test.db"))));
    stage.show();
  }

  @Test
  void displaysApplicationAndStorageMarkers() {
    verifyThat("#app-title", hasText("NUSynapxe"));
    verifyThat("#storage-status", hasText("SQLite storage: test.db"));
  }
}
