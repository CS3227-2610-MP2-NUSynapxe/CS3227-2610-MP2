package nusynapxe.domain;

/**
 * Clinical consultation information owned by the assigned doctor.
 *
 * @param id clinical record identifier
 * @param patientId patient identifier
 * @param appointmentId associated appointment identifier
 * @param doctorId assigned doctor identifier
 * @param diagnosis diagnosis text
 * @param consultationNotes consultation notes
 * @param followUpNotes follow-up instructions
 */
public record ClinicalRecord(
    long id,
    long patientId,
    long appointmentId,
    long doctorId,
    String diagnosis,
    String consultationNotes,
    String followUpNotes) {
  // Immutable clinical projection.
}
