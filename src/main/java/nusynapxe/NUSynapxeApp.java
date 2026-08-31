package nusynapxe;

import java.sql.SQLException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.ui.NUSynapxeView;

/** JavaFX application entry point for NUSynapxe. */
public final class NUSynapxeApp extends Application {
  private SqliteDatabase database;

  /** Creates an application instance for the JavaFX runtime. */
  public NUSynapxeApp() {
    super();
  }

  /**
   * Opens local storage and displays the initial application window.
   *
   * @param stage stage supplied by the JavaFX runtime
   * @throws SQLException if the local database cannot be opened or initialized
   */
  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(DatabasePaths.configuredDatabasePath());
    database.open();

    stage.setTitle("NUSynapxe");
    stage.setScene(new Scene(NUSynapxeView.create(database.path()), 640, 360));
    stage.show();
  }

  /**
   * Closes local storage before JavaFX exits.
   *
   * @throws SQLException if the database cannot be closed
   */
  @Override
  public void stop() throws SQLException {
    if (database != null) {
      database.close();
    }
  }

  /**
   * Launches NUSynapxe.
   *
   * @param arguments command-line arguments forwarded to JavaFX
   */
  public static void main(String[] arguments) {
    launch(arguments);
  }
}
