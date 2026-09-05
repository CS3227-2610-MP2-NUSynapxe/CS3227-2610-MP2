package nusynapxe.domain;

/**
 * Public account information that is safe to expose to an authenticated user.
 *
 * @param id database identifier
 * @param username unique login name
 * @param displayName user-facing display name
 * @param role authorization role
 * @param enabled whether the account may authenticate
 */
public record Account(long id, String username, String displayName, Role role, boolean enabled) {
  // Immutable account projection.
}
