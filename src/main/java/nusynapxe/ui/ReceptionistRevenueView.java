package nusynapxe.ui;

import java.util.Map;
import java.util.stream.Collectors;
import nusynapxe.domain.RevenueReport;

/** Presentation helpers for the Receptionist revenue feature. */
final class ReceptionistRevenueView {
  private ReceptionistRevenueView() {
    throw new AssertionError("Utility class");
  }

  static String formatSummary(RevenueReport report) {
    return "Successful payments: "
        + report.receiptCount()
        + "    Total: $"
        + ReportExporter.formatMinor(report.totalMinor())
        + "\nPayment methods: "
        + formatTotals(report.byMethod())
        + "\nDoctors: "
        + formatTotals(report.byDoctor());
  }

  private static String formatTotals(Map<?, Long> totals) {
    if (totals.isEmpty()) {
      return "None";
    }
    return totals.entrySet().stream()
        .sorted(
            Map.Entry.comparingByKey((left, right) -> left.toString().compareTo(right.toString())))
        .map(
            entry ->
                UiComponents.humanizeStatus(entry.getKey().toString())
                    + " $"
                    + ReportExporter.formatMinor(entry.getValue()))
        .collect(Collectors.joining(", "));
  }
}
