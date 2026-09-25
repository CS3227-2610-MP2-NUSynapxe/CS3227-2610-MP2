package nusynapxe.ui;

import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.RevenueReport;

/** Tracks whether a revenue report is safe to export while a replacement loads. */
final class ReportExportState {
  private Optional<RevenueReport> currentReport = Optional.empty();
  private long generation;

  /** Marks the previous report as stale before starting a replacement load. */
  long begin() {
    currentReport = Optional.empty();
    generation++;
    return generation;
  }

  /** Publishes a successfully loaded report for export. */
  boolean complete(long reportGeneration, RevenueReport report) {
    if (reportGeneration != generation) {
      return false;
    }
    currentReport = Optional.of(Objects.requireNonNull(report, "report"));
    return true;
  }

  /** Returns whether a callback belongs to the latest requested report. */
  boolean isCurrent(long reportGeneration) {
    return reportGeneration == generation;
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
