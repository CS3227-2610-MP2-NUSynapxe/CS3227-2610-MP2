package nusynapxe.ui;

import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Builds the initial NUSynapxe JavaFX view. */
public final class NUSynapxeView {
  private NUSynapxeView() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the initial application content.
   *
   * @param databasePath path used by the local SQLite store
   * @return the root node for the initial scene
   */
  public static Parent create(Path databasePath) {
    Label title = new Label("NUSynapxe");
    title.setId("app-title");

    Label subtitle = new Label("Java 25 desktop application");
    Label storageStatus = new Label("SQLite storage: " + databasePath);
    storageStatus.setId("storage-status");

    VBox root = new VBox(12, title, subtitle, storageStatus);
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(24));
    return root;
  }
}
