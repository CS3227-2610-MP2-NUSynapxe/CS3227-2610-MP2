package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReceptionistArchitectureTest extends ReceptionistViewTestSupport {
  @Test
  void appointmentTableUsesPreloadedDisplayNamesInsteadOfCellQueries() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/nusynapxe/ui/ReceptionistWorkspace.java"));
    String panelSource =
        Files.readString(Path.of("src/main/java/nusynapxe/ui/ReceptionistAppointmentPanel.java"));
    String tableSource =
        Files.readString(Path.of("src/main/java/nusynapxe/ui/ReceptionistAppointmentView.java"));
    assertFalse(source.contains("services.appointmentService().searchAppointmentRows"));
    assertTrue(panelSource.contains("ReceptionistAppointmentView.appointmentTable"));
    assertFalse(tableSource.contains("patientDisplayName(services"));
    assertFalse(tableSource.contains("doctorDisplayName(services"));
    assertTrue(tableSource.contains("AppointmentListRow::patientDisplayName"));
    assertTrue(tableSource.contains("AppointmentListRow::doctorDisplayName"));
  }

  @Test
  void receptionistWorkspaceDelegatesBlockingRefreshesToTheDataLoader() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/nusynapxe/ui/ReceptionistWorkspace.java"));
    assertTrue(source.contains("ReceptionistDataLoader"));
    assertFalse(source.contains("services.appointmentService().searchAppointmentRows"));
    assertFalse(source.contains("services.appointmentService().book"));
    assertFalse(source.contains("services.appointmentService().cancel"));
    assertFalse(source.contains("services.appointmentService().checkIn"));
    assertFalse(source.contains("services.billingService().receiptHistory"));
    assertFalse(source.contains("services.billingService().revenueReport"));
    assertFalse(source.contains("services.billingService().dailyRevenue"));
    assertFalse(source.contains("services.accountService().listDoctors"));
  }
}
