package nusynapxe.domain;

/** Medication instructions attached to a clinical consultation. */
public record Prescription(
    long id,
    long clinicalRecordId,
    String medication,
    String dosage,
    String frequency,
    String duration,
    String instructions) {
  // Immutable prescription projection.
}
