package nusynapxe.domain;

import java.util.List;

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
}
