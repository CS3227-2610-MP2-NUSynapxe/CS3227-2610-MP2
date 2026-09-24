package nusynapxe.ui;

import java.time.Clock;
import javafx.scene.Parent;
import nusynapxe.ClinicClock;
import nusynapxe.domain.RevenueReport;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/**
 * Composition shell for the Receptionist workspace. Feature behavior lives in the workspace panes.
 */
public final class ReceptionistView {
  private ReceptionistView() {
    throw new AssertionError("Utility class");
  }

  /**
   * Creates the Receptionist workspace using deterministic compatibility defaults.
   *
   * @param services services used by the workspace
   * @param session authenticated Receptionist session
   * @param onLogout callback invoked when the Receptionist logs out
   * @return root node for the Receptionist workspace
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
    return ReceptionistWorkspace.create(services, session, onLogout, clock, taskRunner);
  }

  static String reportCsv(RevenueReport report) {
    return ReportExporter.toCsv(report);
  }

  static String reportJson(RevenueReport report) {
    return ReportExporter.toJson(report);
  }
}
