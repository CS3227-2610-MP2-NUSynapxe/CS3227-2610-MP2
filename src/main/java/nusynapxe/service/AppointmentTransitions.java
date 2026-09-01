package nusynapxe.service;

import nusynapxe.domain.AppointmentStatus;

/** Defines the allowed appointment lifecycle transitions. */
public final class AppointmentTransitions {
  private AppointmentTransitions() {
    throw new AssertionError("Utility class");
  }

  /** Requires that an appointment may move from one state to another. */
  public static void requireAllowed(AppointmentStatus from, AppointmentStatus to) {
    if (!isAllowed(from, to)) {
      throw new ValidationException("The appointment cannot move from " + from + " to " + to);
    }
  }

  /** Reports whether the lifecycle transition is valid. */
  public static boolean isAllowed(AppointmentStatus from, AppointmentStatus to) {
    if (from == null || to == null) {
      return false;
    }
    return switch (from) {
      case PENDING -> to == AppointmentStatus.ACCEPTED || to == AppointmentStatus.CANCELLED;
      case ACCEPTED -> to == AppointmentStatus.CHECKED_IN || to == AppointmentStatus.CANCELLED;
      case CHECKED_IN -> to == AppointmentStatus.COMPLETED;
      case COMPLETED -> to == AppointmentStatus.CHECKED_OUT;
      case CHECKED_OUT, CANCELLED -> false;
    };
  }
}
