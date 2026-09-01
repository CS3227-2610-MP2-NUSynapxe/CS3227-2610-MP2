package nusynapxe.domain;

/** Ephemeral identity and role for the currently authenticated user. */
public record Session(long accountId, String username, Role role) {
  // Session state is intentionally kept in memory only.
}
