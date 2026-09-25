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

    long firstGeneration = state.begin();
    state.complete(firstGeneration, report);
    assertTrue(state.isReady());
    assertEquals(Optional.of(report), state.current());

    state.begin();

    assertFalse(state.isReady());
    assertFalse(state.current().isPresent());
  }

  @Test
  void staleReportCannotReplaceOrEnableTheLatestRequest() {
    ReportExportState state = new ReportExportState();
    RevenueReport stale = new RevenueReport(List.of());
    RevenueReport latest = new RevenueReport(List.of());

    long staleGeneration = state.begin();
    long latestGeneration = state.begin();

    assertFalse(state.complete(staleGeneration, stale));
    assertFalse(state.isReady());
    assertTrue(state.complete(latestGeneration, latest));
    assertEquals(Optional.of(latest), state.current());
  }
}
