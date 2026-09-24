package nusynapxe.domain;

import java.util.Objects;

/**
 * Read model containing the appointment-table data needed by the Receptionist workspace.
 *
 * @param appointment appointment represented by the row
 * @param patientDisplayName display name loaded for the patient
 * @param doctorDisplayName display name loaded for the Doctor
 */
public record AppointmentListRow(
    Appointment appointment, String patientDisplayName, String doctorDisplayName) {
  /**
   * Creates a row and substitutes stable fallback labels for missing display names.
   *
   * @param appointment appointment represented by the row
   * @param patientDisplayName display name loaded for the patient
   * @param doctorDisplayName display name loaded for the Doctor
   */
  public AppointmentListRow {
    Objects.requireNonNull(appointment, "appointment");
    patientDisplayName = displayName(patientDisplayName, "Patient unavailable");
    doctorDisplayName = displayName(doctorDisplayName, "Doctor unavailable");
  }

  private static String displayName(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
