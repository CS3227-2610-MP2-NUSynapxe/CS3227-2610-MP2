package nusynapxe.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** Non-clinical appointment projection used by the Doctor Calendar. */
public record CalendarAppointment(
    long appointmentId,
    long patientId,
    String patientDisplayName,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    AppointmentStatus status) {
  /** Validates the administrative appointment projection. */
  public CalendarAppointment {
    if (appointmentId <= 0 || patientId <= 0) {
      throw new IllegalArgumentException("Appointment and patient identifiers must be positive");
    }
    if (patientDisplayName == null || patientDisplayName.isBlank()) {
      throw new IllegalArgumentException("Patient display name is required");
    }
    Objects.requireNonNull(startsAt, "startsAt");
    Objects.requireNonNull(endsAt, "endsAt");
    if (!endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("Appointment must end after it starts");
    }
    Objects.requireNonNull(status, "status");
  }
}
