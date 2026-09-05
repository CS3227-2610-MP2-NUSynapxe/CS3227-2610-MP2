package nusynapxe.domain;

/**
 * Ephemeral identity and role for the currently authenticated user.
 *
 * @param accountId authenticated account identifier
 * @param username authenticated login name
 * @param role authenticated authorization role
 */
public record Session(long accountId, String username, Role role) {
  // Session state is intentionally kept in memory only.
}
