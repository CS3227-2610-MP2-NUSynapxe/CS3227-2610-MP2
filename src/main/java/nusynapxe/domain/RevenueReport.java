package nusynapxe.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Immutable receipt-backed revenue report.
 *
 * @param receipts successful receipts included in the report
 */
public record RevenueReport(List<Receipt> receipts) {
  /**
   * Creates an immutable report projection.
   *
   * @throws NullPointerException if {@code receipts} is {@code null}
   */
  public RevenueReport {
    receipts = List.copyOf(receipts);
  }

  /**
   * Returns the total number of successful receipts.
   *
   * @return receipt count
   */
  public long receiptCount() {
    return receipts.size();
  }

  /**
   * Returns the total amount in minor currency units.
   *
   * @return sum of receipt amounts
   */
  public long totalMinor() {
    return receipts.stream().mapToLong(Receipt::amountMinor).sum();
  }

  /**
   * Returns successful receipt counts grouped by payment method.
   *
   * @return payment methods mapped to their total amount in minor units
   */
  public Map<PaymentMethod, Long> byMethod() {
    return receipts.stream()
        .collect(
            Collectors.groupingBy(Receipt::method, Collectors.summingLong(Receipt::amountMinor)));
  }

  /**
   * Returns successful receipt totals grouped by Doctor name.
   *
   * @return doctor display names mapped to their total amount in minor units
   */
  public Map<String, Long> byDoctor() {
    return receipts.stream()
        .collect(
            Collectors.groupingBy(
                Receipt::doctorName, Collectors.summingLong(Receipt::amountMinor)));
  }
}
