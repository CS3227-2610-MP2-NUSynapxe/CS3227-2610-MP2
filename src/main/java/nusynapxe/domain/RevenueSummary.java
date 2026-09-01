package nusynapxe.domain;

import java.time.LocalDate;

/** Successful checkout totals for one local clinic date. */
public record RevenueSummary(LocalDate date, long transactionCount, long totalMinor) {
  // Immutable revenue projection.
}
