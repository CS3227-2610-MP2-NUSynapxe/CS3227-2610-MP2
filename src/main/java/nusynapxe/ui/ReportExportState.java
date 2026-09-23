package nusynapxe.ui;

import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.RevenueReport;

/** Tracks whether a revenue report is safe to export while a replacement loads. */
final class ReportExportState {
  private Optional<RevenueReport> currentReport = Optional.empty();

  /** Marks the previous report as stale before starting a replacement load. */
  void begin() {
    currentReport = Optional.empty();
  }

  /** Publishes a successfully loaded report for export. */
  void complete(RevenueReport report) {
    currentReport = Optional.of(Objects.requireNonNull(report, "report"));
  }

  /** Returns whether an exportable report is available. */
  boolean isReady() {
    return currentReport.isPresent();
  }

  /** Returns the current report, if the latest generation completed successfully. */
  Optional<RevenueReport> current() {
    return currentReport;
  }
}
