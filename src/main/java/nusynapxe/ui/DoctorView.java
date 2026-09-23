package nusynapxe.ui;

import java.time.Clock;
import javafx.scene.Parent;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/** Composition shell for the Doctor workspace. Feature behavior lives in the workspace panes. */
public final class DoctorView {
  private DoctorView() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the Doctor workspace using deterministic compatibility defaults.
   *
   * @param services services used by the workspace
   * @param session authenticated Doctor session
   * @param onLogout callback invoked when the Doctor logs out
   * @return root node for the Doctor workspace
   * @throws NullPointerException if an argument is {@code null}
   */
  public static Parent create(ClinicServices services, Session session, Runnable onLogout) {
    return create(services, session, onLogout, ClinicClock.system(), ClinicTaskRunner.immediate());
  }

  static Parent create(ClinicServices services, Session session, Runnable onLogout, Clock clock) {
    return create(services, session, onLogout, clock, ClinicTaskRunner.immediate());
  }

  static Parent create(
      ClinicServices services,
      Session session,
      Runnable onLogout,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    return DoctorWorkspace.create(services, session, onLogout, clock, taskRunner);
  }
}
