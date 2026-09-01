package nusynapxe.domain;

/** Public account information that is safe to expose to an authenticated user. */
public record Account(long id, String username, String displayName, Role role, boolean enabled) {
  // Immutable account projection.
}
