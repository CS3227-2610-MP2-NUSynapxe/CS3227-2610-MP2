package nusynapxe.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Immutable receipt-backed revenue report. */
public record RevenueReport(List<Receipt> receipts) {
  public RevenueReport {
    receipts = List.copyOf(receipts);
  }

  /** Returns the total number of successful receipts. */
  public long receiptCount() {
    return receipts.size();
  }

  /** Returns the total amount in minor currency units. */
  public long totalMinor() {
    return receipts.stream().mapToLong(Receipt::amountMinor).sum();
  }

  /** Returns successful receipt counts grouped by payment method. */
  public Map<PaymentMethod, Long> byMethod() {
    return receipts.stream()
        .collect(
            Collectors.groupingBy(Receipt::method, Collectors.summingLong(Receipt::amountMinor)));
  }

  /** Returns successful receipt totals grouped by Doctor name. */
  public Map<String, Long> byDoctor() {
    return receipts.stream()
        .collect(
            Collectors.groupingBy(
                Receipt::doctorName, Collectors.summingLong(Receipt::amountMinor)));
  }
}
