package nusynapxe.domain;

import java.io.Serializable;
import java.util.List;

/** Non-sensitive relationship counts that explain why a patient cannot be deleted. */
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

  /** Validates the identifier and relationship counts. */
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

  /** Returns whether no known patient-related rows block deletion. */
  public boolean canDelete() {
    return totalCount() == 0;
  }

  /** Returns the total number of blocking relationship rows. */
  public long totalCount() {
    return appointments + clinicalRecords + prescriptions + payments + receipts + otherReferences;
  }

  /** Returns human-readable categories with non-zero counts for a blocking modal. */
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

  /** Returns a copy with additional fallback references detected by foreign-key enforcement. */
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

  /** A display-safe relationship category and count. */
  public record BlockingRelation(String label, long count) {
    /** Validates a blocking relation value. */
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
