package nusynapxe.service;

import java.util.Objects;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;

/** Centralizes session, role, and ownership checks for application services. */
public final class Authorization {
  private Authorization() {
    throw new AssertionError("Utility class");
  }

  /**
   * Requires a non-null authenticated session.
   *
   * @param session session to validate
   * @throws AuthorizationException if {@code session} is {@code null}
   */
  public static void requireAuthenticated(Session session) {
    if (session == null) {
      throw new AuthorizationException("Authentication is required");
    }
  }

  /**
   * Requires an authenticated session with one specific role.
   *
   * @param session session to validate
   * @param requiredRole role required by the operation
   * @throws AuthorizationException if the session is missing or has another role
   * @throws NullPointerException if {@code requiredRole} is {@code null}
   */
  public static void requireRole(Session session, Role requiredRole) {
    requireAuthenticated(session);
    Objects.requireNonNull(requiredRole, "requiredRole");
    if (session.role() != requiredRole) {
      throw new AuthorizationException("You are not allowed to perform this operation");
    }
  }

  /**
   * Requires an authenticated Doctor or Receptionist for administrative patient operations.
   *
   * @param session session to validate
   * @throws AuthorizationException if the session is missing or has another role
   */
  public static void requirePatientAdministration(Session session) {
    requireAuthenticated(session);
    if (session.role() != Role.DOCTOR && session.role() != Role.RECEPTIONIST) {
      throw new AuthorizationException("You are not allowed to perform this operation");
    }
  }

  /**
   * Requires that a Doctor session owns the referenced doctor resource.
   *
   * @param session session to validate
   * @param doctorId doctor identifier that must match the session owner
   * @throws AuthorizationException if the session is not the owning Doctor
   */
  public static void requireDoctorOwnership(Session session, long doctorId) {
    requireRole(session, Role.DOCTOR);
    if (session.accountId() != doctorId) {
      throw new AuthorizationException("You are not allowed to access another doctor's schedule");
    }
  }
}
