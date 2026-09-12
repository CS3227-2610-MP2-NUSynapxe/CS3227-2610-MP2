package nusynapxe.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Authorized data returned for one Doctor Calendar week.
 *
 * @param doctorId owning doctor identifier
 * @param weekStart first date in the returned week
 * @param settings doctor's calendar settings
 * @param appointments non-clinical appointments visible in the week
 * @param timeOff unavailable intervals visible in the range
 */
public record DoctorCalendarWeek(
    long doctorId,
    LocalDate weekStart,
    DoctorCalendarSettings settings,
    List<CalendarAppointment> appointments,
    List<DoctorTimeOff> timeOff) {
  /**
   * Validates and freezes the week projection.
   *
   * @throws IllegalArgumentException if the doctor identifier is invalid or the settings belong to
   *     another doctor
   * @throws NullPointerException if a required component is {@code null}
   */
  public DoctorCalendarWeek {
    if (doctorId <= 0) {
      throw new IllegalArgumentException("Doctor identifier must be positive");
    }
    Objects.requireNonNull(weekStart, "weekStart");
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(appointments, "appointments");
    Objects.requireNonNull(timeOff, "timeOff");
    if (settings.doctorId() != doctorId) {
      throw new IllegalArgumentException("Calendar settings belong to another Doctor");
    }
    appointments = List.copyOf(appointments);
    timeOff = List.copyOf(timeOff);
  }
}
