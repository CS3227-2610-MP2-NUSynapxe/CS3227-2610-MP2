package nusynapxe.domain;

import java.util.List;
import java.util.Objects;

/**
 * One bounded, chronologically ordered page in a doctor's future schedule.
 *
 * @param appointments appointments returned by the page
 * @param nextCursor keyset position for the next page, or {@code null} at the end
 * @param hasMore whether another page is available
 */
public record CalendarSchedulePage(
    List<CalendarAppointment> appointments, CalendarScheduleCursor nextCursor, boolean hasMore) {

  /** Default number of appointments requested by the schedule view. */
  public static final int DEFAULT_PAGE_SIZE = 25;

  /** Maximum page size accepted by the schedule query. */
  public static final int MAX_PAGE_SIZE = 100;

  /**
   * Validates and defensively copies a schedule page.
   *
   * @throws NullPointerException if {@code appointments} is {@code null}
   * @throws IllegalArgumentException if the cursor state is inconsistent with the page contents
   */
  public CalendarSchedulePage {
    Objects.requireNonNull(appointments, "appointments");
    appointments = List.copyOf(appointments);

    if (hasMore != (nextCursor != null)) {
      throw new IllegalArgumentException("hasMore must match nextCursor");
    }
    if (hasMore && appointments.isEmpty()) {
      throw new IllegalArgumentException("a page with more results must contain appointments");
    }
    if (nextCursor != null && !appointments.isEmpty()) {
      CalendarAppointment lastAppointment = appointments.get(appointments.size() - 1);
      if (!nextCursor.startsAt().equals(lastAppointment.startsAt())
          || nextCursor.appointmentId() != lastAppointment.appointmentId()) {
        throw new IllegalArgumentException("nextCursor must identify the last appointment");
      }
    }
  }

  /**
   * Validates a bounded schedule page size.
   *
   * @param pageSize requested number of appointments
   * @throws IllegalArgumentException if the size is outside the supported range
   */
  public static void validatePageSize(int pageSize) {
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
    }
  }
}
