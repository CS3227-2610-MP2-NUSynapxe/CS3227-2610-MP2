package nusynapxe.service;

import nusynapxe.domain.AppointmentStatus;

/** Defines the allowed appointment lifecycle transitions. */
public final class AppointmentTransitions {
  private AppointmentTransitions() {
    throw new AssertionError("Utility class");
  }

  /**
   * Requires that an appointment may move from one state to another.
   *
   * @param from current lifecycle state
   * @param to requested lifecycle state
   * @throws ValidationException if the transition is not allowed
   */
  public static void requireAllowed(AppointmentStatus from, AppointmentStatus to) {
    if (!isAllowed(from, to)) {
      throw new ValidationException("The appointment cannot move from " + from + " to " + to);
    }
  }

  /**
   * Reports whether the lifecycle transition is valid.
   *
   * @param from current lifecycle state
   * @param to requested lifecycle state
   * @return {@code true} when the transition is permitted
   */
  public static boolean isAllowed(AppointmentStatus from, AppointmentStatus to) {
    if (from == null || to == null) {
      return false;
    }
    return switch (from) {
      case PENDING ->
          to == AppointmentStatus.ACCEPTED
              || to == AppointmentStatus.DECLINED
              || to == AppointmentStatus.CANCELLED;
      case ACCEPTED ->
          to == AppointmentStatus.DECLINED
              || to == AppointmentStatus.CHECKED_IN
              || to == AppointmentStatus.CANCELLED;
      case DECLINED -> to == AppointmentStatus.CANCELLED;
      case CHECKED_IN -> to == AppointmentStatus.COMPLETED;
      case COMPLETED -> to == AppointmentStatus.CHECKED_OUT;
      case CHECKED_OUT, CANCELLED -> false;
    };
  }
}
