package nusynapxe.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A non-empty schedule section containing appointments that start on one date.
 *
 * @param date date shared by all appointments in the group
 * @param appointments appointments ordered for display
 */
public record CalendarScheduleGroup(LocalDate date, List<CalendarAppointment> appointments) {

  /**
   * Validates and defensively copies a schedule group.
   *
   * @throws NullPointerException if the date or appointment list is {@code null}
   * @throws IllegalArgumentException if the list is empty or contains an appointment from another
   *     date
   */
  public CalendarScheduleGroup {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(appointments, "appointments");
    appointments = List.copyOf(appointments);
    if (appointments.isEmpty()) {
      throw new IllegalArgumentException("a schedule group must contain appointments");
    }
    if (appointments.stream()
        .anyMatch(appointment -> !date.equals(appointment.startsAt().toLocalDate()))) {
      throw new IllegalArgumentException("all appointments must start on the group date");
    }
  }
}
