package nusynapxe.domain;

import java.time.LocalDateTime;

/** A scheduled patient visit assigned to one doctor. */
public record Appointment(
    long id,
    long patientId,
    long doctorId,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    AppointmentStatus status) {
  // Immutable appointment projection.
}
