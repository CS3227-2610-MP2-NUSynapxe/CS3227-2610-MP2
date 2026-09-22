package nusynapxe.domain;

import java.util.Objects;

/** Read model containing the appointment-table data needed by the Receptionist workspace. */
public record AppointmentListRow(
    Appointment appointment, String patientDisplayName, String doctorDisplayName) {
  public AppointmentListRow {
    Objects.requireNonNull(appointment, "appointment");
    patientDisplayName = displayName(patientDisplayName, "Patient unavailable");
    doctorDisplayName = displayName(doctorDisplayName, "Doctor unavailable");
  }

  private static String displayName(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
