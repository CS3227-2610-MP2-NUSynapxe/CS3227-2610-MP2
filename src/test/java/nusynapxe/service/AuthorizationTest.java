package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import org.junit.jupiter.api.Test;

final class AuthorizationTest {
  @Test
  void allowsRoleAndOwnershipMatches() {
    Session doctor = new Session(7, "doctor", Role.DOCTOR);
    Session receptionist = new Session(8, "reception", Role.RECEPTIONIST);

    assertDoesNotThrow(() -> Authorization.requireRole(doctor, Role.DOCTOR));
    assertDoesNotThrow(() -> Authorization.requireDoctorOwnership(doctor, 7));
    assertDoesNotThrow(() -> Authorization.requirePatientAdministration(doctor));
    assertDoesNotThrow(() -> Authorization.requirePatientAdministration(receptionist));
    assertDoesNotThrow(
        () ->
            Authorization.requireRole(
                new Session(9, "admin", Role.SYSTEM_ADMIN), Role.SYSTEM_ADMIN));
  }

  @Test
  void deniesMissingRoleAndOtherDoctorOwnership() {
    Session doctor = new Session(7, "doctor", Role.DOCTOR);
    Session receptionist = new Session(8, "reception", Role.RECEPTIONIST);

    assertThrows(
        AuthorizationException.class, () -> Authorization.requireRole(receptionist, Role.DOCTOR));
    assertThrows(
        AuthorizationException.class, () -> Authorization.requireDoctorOwnership(doctor, 8));
    assertThrows(
        AuthorizationException.class,
        () ->
            Authorization.requirePatientAdministration(new Session(9, "admin", Role.SYSTEM_ADMIN)));
    assertThrows(
        AuthorizationException.class, () -> Authorization.requirePatientAdministration(null));
    assertThrows(AuthorizationException.class, () -> Authorization.requireAuthenticated(null));
  }
}
