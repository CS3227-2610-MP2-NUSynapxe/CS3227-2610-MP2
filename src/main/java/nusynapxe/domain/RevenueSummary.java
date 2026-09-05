package nusynapxe.domain;

import java.time.LocalDate;

/**
 * Successful checkout totals for one local clinic date.
 *
 * @param date Singapore-local clinic date
 * @param transactionCount number of successful transactions
 * @param totalMinor total amount in minor currency units
 */
public record RevenueSummary(LocalDate date, long transactionCount, long totalMinor) {
  // Immutable revenue projection.
}
