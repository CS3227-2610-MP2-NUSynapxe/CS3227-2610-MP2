package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import nusynapxe.domain.AppointmentStatus;
import org.junit.jupiter.api.Test;

final class AppointmentTransitionsTest {

  @Test
  void allowsDecisionCancellationAndOperationalTransitions() {
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.PENDING, AppointmentStatus.ACCEPTED));
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.PENDING, AppointmentStatus.DECLINED));
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.PENDING, AppointmentStatus.CANCELLED));
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.ACCEPTED, AppointmentStatus.DECLINED));
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.ACCEPTED, AppointmentStatus.CHECKED_IN));
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.ACCEPTED, AppointmentStatus.CANCELLED));
    assertTrue(
        AppointmentTransitions.isAllowed(AppointmentStatus.DECLINED, AppointmentStatus.CANCELLED));
    assertTrue(
        AppointmentTransitions.isAllowed(
            AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED));
    assertTrue(
        AppointmentTransitions.isAllowed(
            AppointmentStatus.COMPLETED, AppointmentStatus.CHECKED_OUT));
  }

  @Test
  void rejectsInvalidAndTerminalTransitions() {
    assertFalse(
        AppointmentTransitions.isAllowed(AppointmentStatus.DECLINED, AppointmentStatus.ACCEPTED));
    assertFalse(
        AppointmentTransitions.isAllowed(AppointmentStatus.DECLINED, AppointmentStatus.CHECKED_IN));
    assertFalse(
        AppointmentTransitions.isAllowed(
            AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED));
    assertFalse(
        AppointmentTransitions.isAllowed(
            AppointmentStatus.CHECKED_OUT, AppointmentStatus.ACCEPTED));
    assertFalse(
        AppointmentTransitions.isAllowed(AppointmentStatus.CANCELLED, AppointmentStatus.PENDING));
    assertFalse(AppointmentTransitions.isAllowed(null, AppointmentStatus.ACCEPTED));
    assertFalse(AppointmentTransitions.isAllowed(AppointmentStatus.PENDING, null));
    assertThrows(
        ValidationException.class,
        () ->
            AppointmentTransitions.requireAllowed(
                AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED));
  }
}
