package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import nusynapxe.domain.RevenueReport;
import org.junit.jupiter.api.Test;

final class ReportExportStateTest {
  @Test
  void reportIsUnavailableWhileAReplacementIsLoading() {
    ReportExportState state = new ReportExportState();
    RevenueReport report = new RevenueReport(List.of());

    state.complete(report);
    assertTrue(state.isReady());
    assertEquals(Optional.of(report), state.current());

    state.begin();

    assertFalse(state.isReady());
    assertFalse(state.current().isPresent());
  }
}
