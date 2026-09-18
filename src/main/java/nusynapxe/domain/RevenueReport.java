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
   * @throws ArithmeticException if the total exceeds the supported {@code long} range
   */
  public long totalMinor() {
    return receipts.stream().mapToLong(Receipt::amountMinor).reduce(0L, Math::addExact);
  }

  /**
   * Returns successful receipt counts grouped by payment method.
   *
   * @return payment methods mapped to their total amount in minor units
   * @throws ArithmeticException if a grouped total exceeds the supported {@code long} range
   */
  public Map<PaymentMethod, Long> byMethod() {
    return receipts.stream()
        .collect(Collectors.toMap(Receipt::method, Receipt::amountMinor, Math::addExact));
  }

  /**
   * Returns successful receipt totals grouped by Doctor name.
   *
   * @return doctor display names mapped to their total amount in minor units
   * @throws ArithmeticException if a grouped total exceeds the supported {@code long} range
   */
  public Map<String, Long> byDoctor() {
    return receipts.stream()
        .collect(Collectors.toMap(Receipt::doctorName, Receipt::amountMinor, Math::addExact));
  }
}
