package nusynapxe.domain;

import java.io.Serializable;
import java.util.List;

/**
 * Non-sensitive relationship counts that explain why a patient cannot be deleted.
 *
 * @param patientId patient identifier
 * @param appointments number of related appointments
 * @param clinicalRecords number of related clinical records
 * @param prescriptions number of related prescriptions
 * @param payments number of related payments
 * @param receipts number of related receipts
 * @param otherReferences number of other related rows
 */
public record PatientDeletionBlockers(
    long patientId,
    long appointments,
    long clinicalRecords,
    long prescriptions,
    long payments,
    long receipts,
    long otherReferences)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Validates the identifier and relationship counts.
   *
   * @throws IllegalArgumentException if the patient identifier is not positive or a count is
   *     negative
   */
  public PatientDeletionBlockers {
    if (patientId <= 0) {
      throw new IllegalArgumentException("Patient ID must be positive");
    }
    requireNonNegative(appointments, "appointments");
    requireNonNegative(clinicalRecords, "clinical records");
    requireNonNegative(prescriptions, "prescriptions");
    requireNonNegative(payments, "payments");
    requireNonNegative(receipts, "receipts");
    requireNonNegative(otherReferences, "other references");
  }

  /**
   * Returns whether no known patient-related rows block deletion.
   *
   * @return {@code true} when every relationship count is zero
   */
  public boolean canDelete() {
    return totalCount() == 0;
  }

  /**
   * Returns the total number of blocking relationship rows.
   *
   * @return sum of all relationship counts
   */
  public long totalCount() {
    return appointments + clinicalRecords + prescriptions + payments + receipts + otherReferences;
  }

  /**
   * Returns human-readable categories with non-zero counts for a blocking modal.
   *
   * @return immutable non-zero blocking categories in display order
   */
  public List<BlockingRelation> blockingRelations() {
    return List.of(
            new BlockingRelation("Appointments", appointments),
            new BlockingRelation("Clinical records", clinicalRecords),
            new BlockingRelation("Prescriptions", prescriptions),
            new BlockingRelation("Payments", payments),
            new BlockingRelation("Receipts", receipts),
            new BlockingRelation("Other patient-related records", otherReferences))
        .stream()
        .filter(relation -> relation.count() > 0)
        .toList();
  }

  /**
   * Returns a copy with additional fallback references detected by foreign-key enforcement.
   *
   * @param additionalReferences additional references to add
   * @return a new blocker projection with the additional count
   * @throws IllegalArgumentException if {@code additionalReferences} is negative
   */
  public PatientDeletionBlockers withAdditionalOtherReferences(long additionalReferences) {
    if (additionalReferences < 0) {
      throw new IllegalArgumentException("Additional references cannot be negative");
    }
    return new PatientDeletionBlockers(
        patientId,
        appointments,
        clinicalRecords,
        prescriptions,
        payments,
        receipts,
        otherReferences + additionalReferences);
  }

  private static void requireNonNegative(long value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " cannot be negative");
    }
  }

  /**
   * A display-safe relationship category and count.
   *
   * @param label user-facing relationship label
   * @param count number of related rows
   */
  public record BlockingRelation(String label, long count) {
    /**
     * Validates a blocking relation value.
     *
     * @throws IllegalArgumentException if the label is blank or the count is negative
     */
    public BlockingRelation {
      if (label == null || label.isBlank()) {
        throw new IllegalArgumentException("Blocking relation label is required");
      }
      if (count < 0) {
        throw new IllegalArgumentException("Blocking relation count cannot be negative");
      }
    }
  }
}
