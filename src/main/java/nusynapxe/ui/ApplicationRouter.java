package nusynapxe.ui;

import java.sql.SQLException;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;

/** Routes the JavaFX stage between setup, authentication, and role workspaces. */
public final class ApplicationRouter {
  private final Stage stage;
  private final ClinicServices services;

  /** Creates a router for one opened database and JavaFX stage. */
  public ApplicationRouter(Stage stage, SqliteDatabase database) {
    this(stage, ClinicServices.forDatabase(Objects.requireNonNull(database, "database")));
  }

  ApplicationRouter(Stage stage, ClinicServices services) {
    this.stage = Objects.requireNonNull(stage, "stage");
    this.services = Objects.requireNonNull(services, "services");
  }

  /** Shows first-run setup or login depending on persisted account state. */
  public void showInitial() throws SQLException {
    if (services.accountService().needsInitialSetup()) {
      showSetup();
    } else {
      showLogin();
    }
  }

  /** Shows the login page and ensures no previous session remains. */
  public void showLogin() {
    services.authenticationService().logout();
    setContent(LoginView.create(services.authenticationService(), this::showWorkspace));
  }

  /** Clears the ephemeral session without changing the stage during application shutdown. */
  public void clearSession() {
    services.authenticationService().logout();
  }

  /** Shows the first-run administrator setup page. */
  public void showSetup() {
    setContent(SetupView.create(services.accountService(), this::showLogin));
  }

  /** Shows the initial role marker until the full role workspace is constructed. */
  public void showWorkspace(Session session) {
    Objects.requireNonNull(session, "session");
    if (session.role() == Role.SYSTEM_ADMIN) {
      setContent(SystemAdminView.create(services.accountService(), session, this::showLogin));
      return;
    }
    if (session.role() == Role.RECEPTIONIST) {
      setContent(ReceptionistView.create(services, session, this::showLogin));
      return;
    }
    if (session.role() == Role.DOCTOR) {
      setContent(DoctorView.create(services, session, this::showLogin));
      return;
    }
    Label title = new Label(session.role().name().replace('_', ' ') + " workspace");
    title.setId("workspace-title");
    Label identity = new Label("Signed in as " + session.username());
    identity.setId("workspace-identity");
    Button logout = new Button("Log out");
    logout.setId("logout-button");
    logout.setOnAction(event -> showLogin());
    VBox root = new VBox(12, title, identity, logout);
    root.setId(workspaceId(session.role()));
    root.setAlignment(Pos.CENTER);
    root.setPadding(new Insets(32));
    setContent(root);
  }

  private void setContent(Parent root) {
    stage.setScene(new javafx.scene.Scene(root, 760, 520));
  }

  private static String workspaceId(Role role) {
    return switch (role) {
      case DOCTOR -> "doctor-workspace";
      case RECEPTIONIST -> "receptionist-workspace";
      case SYSTEM_ADMIN -> "system-admin-workspace";
    };
  }
}
