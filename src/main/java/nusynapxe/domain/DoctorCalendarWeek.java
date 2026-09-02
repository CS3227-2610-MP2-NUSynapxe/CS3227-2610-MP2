package nusynapxe.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Authorized data returned for one Doctor Calendar week. */
public record DoctorCalendarWeek(
    long doctorId,
    LocalDate weekStart,
    DoctorCalendarSettings settings,
    List<CalendarAppointment> appointments) {
  /** Validates and freezes the week projection. */
  public DoctorCalendarWeek {
    if (doctorId <= 0) {
      throw new IllegalArgumentException("Doctor identifier must be positive");
    }
    Objects.requireNonNull(weekStart, "weekStart");
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(appointments, "appointments");
    if (settings.doctorId() != doctorId) {
      throw new IllegalArgumentException("Calendar settings belong to another Doctor");
    }
    appointments = List.copyOf(appointments);
  }
}
