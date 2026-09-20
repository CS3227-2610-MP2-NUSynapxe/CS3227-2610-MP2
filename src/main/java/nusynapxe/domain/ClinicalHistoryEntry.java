package nusynapxe.domain;

import java.util.List;
import java.util.Objects;

/**
 * Read-only projection of one terminal consultation for Doctor history.
 *
 * @param appointment appointment metadata and lifecycle status
 * @param doctorName display name of the assigned Doctor
 * @param clinicalRecord saved consultation record
 * @param prescriptions prescriptions attached to the consultation
 */
public record ClinicalHistoryEntry(
    Appointment appointment,
    String doctorName,
    ClinicalRecord clinicalRecord,
    List<Prescription> prescriptions) {
  /** Creates a history entry with an immutable prescription list. */
  public ClinicalHistoryEntry {
    Objects.requireNonNull(appointment, "appointment");
    Objects.requireNonNull(doctorName, "doctorName");
    Objects.requireNonNull(clinicalRecord, "clinicalRecord");
    prescriptions = List.copyOf(Objects.requireNonNull(prescriptions, "prescriptions"));
  }
}
