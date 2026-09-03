package nusynapxe.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A non-empty schedule section containing appointments that start on one date. */
public record CalendarScheduleGroup(LocalDate date, List<CalendarAppointment> appointments) {

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
