package nusynapxe;

import java.sql.SQLException;
import javafx.application.Application;
import javafx.stage.Stage;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.ui.ApplicationRouter;
import nusynapxe.ui.ClinicTaskRunner;
import nusynapxe.ui.SerializedClinicTaskRunner;

/** JavaFX application entry point for NUSynapxe. */
public final class NUSynapxeApp extends Application {
  private SqliteDatabase database;
  private ApplicationRouter router;
  private ClinicTaskRunner taskRunner;

  /** Creates an application instance for the JavaFX runtime. */
  public NUSynapxeApp() {
    super();
  }

  /**
   * Opens local storage and displays the initial maximized application window.
   *
   * @param stage stage supplied by the JavaFX runtime
   * @throws SQLException if the local database cannot be opened or initialized
   */
  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(DatabasePaths.configuredDatabasePath());
    database.open();
    taskRunner = new SerializedClinicTaskRunner();

    stage.setTitle("NUSynapxe");
    router = new ApplicationRouter(stage, database, ClinicClock.system(), taskRunner);
    router.showInitial();
    stage.setMaximized(true);
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
      if (router != null) {
        router.clearSession();
      }
      if (taskRunner != null) {
        taskRunner.close();
      }
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
