package nusynapxe.domain;

/**
 * Medication instructions attached to a clinical consultation.
 *
 * @param id prescription identifier
 * @param clinicalRecordId associated clinical record identifier
 * @param medication medication name
 * @param dosage dosage instructions
 * @param frequency administration frequency
 * @param duration treatment duration
 * @param instructions additional usage instructions
 */
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
