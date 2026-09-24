package nusynapxe.ui;

import java.time.Clock;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;

/** Routes the JavaFX stage between setup, authentication, and role workspaces. */
public final class ApplicationRouter {
  private static final double INITIAL_WIDTH = 1200;
  private static final double INITIAL_HEIGHT = 760;
  private static final double MINIMUM_WIDTH = 980;
  private static final double MINIMUM_HEIGHT = 640;

  private final Stage stage;
  private final ClinicServices services;
  private final Clock clock;
  private final ClinicTaskRunner taskRunner;

  /**
   * Creates a router for one opened database and JavaFX stage.
   *
   * @param stage JavaFX stage to control
   * @param database opened application database
   * @throws NullPointerException if either argument is {@code null}
   */
  public ApplicationRouter(Stage stage, SqliteDatabase database) {
    this(stage, database, ClinicClock.system());
  }

  ApplicationRouter(Stage stage, ClinicServices services) {
    this(stage, services, ClinicClock.system());
  }

  ApplicationRouter(Stage stage, SqliteDatabase database, Clock clock) {
    this(stage, database, clock, ClinicTaskRunner.immediate());
  }

  /**
   * Creates a router with an application-owned task runner.
   *
   * @param stage JavaFX stage to control
   * @param database opened application database
   * @param clock clock used for clinic-local dates
   * @param taskRunner runner used for blocking work
   * @throws NullPointerException if an argument is {@code null}
   */
  public ApplicationRouter(
      Stage stage, SqliteDatabase database, Clock clock, ClinicTaskRunner taskRunner) {
    this(
        stage,
        ClinicServices.forDatabase(Objects.requireNonNull(database, "database"), clock),
        clock,
        taskRunner);
  }

  ApplicationRouter(Stage stage, ClinicServices services, Clock clock) {
    this(stage, services, clock, ClinicTaskRunner.immediate());
  }

  ApplicationRouter(
      Stage stage, ClinicServices services, Clock clock, ClinicTaskRunner taskRunner) {
    this.stage = Objects.requireNonNull(stage, "stage");
    this.services = Objects.requireNonNull(services, "services");
    this.clock = Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
  }

  /** Shows first-run setup or login depending on persisted account state. */
  public void showInitial() {
    taskRunner.submit(
        () -> services.accountService().needsInitialSetup(),
        needsSetup -> {
          if (needsSetup) {
            showSetup();
          } else {
            showLogin();
          }
        },
        failure -> showStorageError());
  }

  /** Shows the login page and ensures no previous session remains. */
  public void showLogin() {
    services.authenticationService().logout();
    setContent(LoginView.create(services.authenticationService(), this::showWorkspace, taskRunner));
  }

  /** Clears the ephemeral session without changing the stage during application shutdown. */
  public void clearSession() {
    services.authenticationService().logout();
  }

  /** Shows the first-run administrator setup page. */
  public void showSetup() {
    setContent(SetupView.create(services.accountService(), this::showLogin, taskRunner));
  }

  /**
   * Shows the initial role marker until the full role workspace is constructed.
   *
   * @param session authenticated session that determines the destination
   * @throws NullPointerException if {@code session} is {@code null}
   */
  public void showWorkspace(Session session) {
    Objects.requireNonNull(session, "session");
    if (session.role() == Role.SYSTEM_ADMIN) {
      setContent(
          SystemAdminView.create(services.accountService(), session, this::showLogin, taskRunner));
      return;
    }
    if (session.role() == Role.RECEPTIONIST) {
      setContent(ReceptionistView.create(services, session, this::showLogin, clock, taskRunner));
      return;
    }
    if (session.role() == Role.DOCTOR) {
      setContent(DoctorView.create(services, session, this::showLogin, clock, taskRunner));
      return;
    }
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(event -> showLogin());
    VBox root =
        new VBox(
            12,
            UiComponents.workspaceHeader(
                session.role().name().replace('_', ' ') + " workspace",
                session.username(),
                logout));
    root.setId(workspaceId(session.role()));
    root.getStyleClass().add("workspace-shell");
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(32));
    setContent(root);
  }

  private void setContent(Parent root) {
    Scene scene = stage.getScene();
    if (scene == null) {
      scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
      UiComponents.applyStylesheet(scene);
      stage.setScene(scene);
    } else {
      scene.setRoot(root);
    }
    stage.setMinWidth(MINIMUM_WIDTH);
    stage.setMinHeight(MINIMUM_HEIGHT);
    stage.setResizable(true);
  }

  private void showStorageError() {
    Label feedback = UiComponents.feedback("storage-feedback");
    VBox content =
        new VBox(
            12,
            UiComponents.pageTitle("Storage unavailable"),
            UiComponents.supportingText("The clinic database could not be read. Please try again."),
            feedback);
    UiComponents.showError(feedback, "Clinic storage is temporarily unavailable");
    setContent(UiComponents.notificationOverlay(content, feedback));
  }

  private static String workspaceId(Role role) {
    return switch (role) {
      case DOCTOR -> "doctor-workspace";
      case RECEPTIONIST -> "receptionist-workspace";
      case SYSTEM_ADMIN -> "system-admin-workspace";
    };
  }
}
