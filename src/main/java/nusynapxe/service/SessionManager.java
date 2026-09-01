package nusynapxe.service;

import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Session;

/** Holds the current authenticated session in application memory only. */
public final class SessionManager {
  private Session currentSession;

  /** Returns the current session, when a user is authenticated. */
  public Optional<Session> current() {
    return Optional.ofNullable(currentSession);
  }

  /** Starts a session for an authenticated account. */
  public void start(Session session) {
    currentSession = Objects.requireNonNull(session, "session");
  }

  /** Requires and returns the current authenticated session. */
  public Session requireCurrent() {
    return currentSession == null ? throwNotAuthenticated() : currentSession;
  }

  /** Clears the session so no protected operation can reuse it. */
  @SuppressWarnings("PMD.NullAssignment")
  public void clear() {
    currentSession = null;
  }

  private static Session throwNotAuthenticated() {
    throw new AuthorizationException("Authentication is required");
  }
}
