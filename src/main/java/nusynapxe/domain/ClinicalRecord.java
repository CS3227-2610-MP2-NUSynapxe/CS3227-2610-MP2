package nusynapxe.domain;

/** Clinical consultation information owned by the assigned doctor. */
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
