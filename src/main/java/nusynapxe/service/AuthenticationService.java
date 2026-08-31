package nusynapxe.service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.AccountCredential;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;

/** Authenticates enabled staff accounts and owns the current in-memory session. */
@SuppressWarnings("PMD.UseVarargs")
public final class AuthenticationService {
  private final AccountRepository accounts;
  private final PasswordHasher passwordHasher;
  private final SessionManager sessions;

  /** Creates an authentication service with a new in-memory session manager. */
  public AuthenticationService(AccountRepository accounts) {
    this(accounts, new PasswordHasher(), new SessionManager());
  }

  AuthenticationService(
      AccountRepository accounts, PasswordHasher passwordHasher, SessionManager sessions) {
    this.accounts = Objects.requireNonNull(accounts, "accounts");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
  }

  /** Attempts login and returns an empty result for every invalid credential case. */
  public Optional<Session> login(String username, char[] password) throws SQLException {
    Objects.requireNonNull(password, "password");
    try {
      if (username == null || username.isBlank()) {
        sessions.clear();
        return Optional.empty();
      }
      Optional<AccountCredential> credentials = accounts.findCredentials(username.trim());
      if (credentials.isEmpty() || !credentials.orElseThrow().account().enabled()) {
        sessions.clear();
        return Optional.empty();
      }
      AccountCredential credential = credentials.orElseThrow();
      if (!passwordHasher.matches(password, credential.salt(), credential.verifier())) {
        sessions.clear();
        return Optional.empty();
      }
      Session session =
          new Session(
              credential.account().id(),
              credential.account().username(),
              credential.account().role());
      sessions.start(session);
      return Optional.of(session);
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  /** Returns the current authenticated session, if one exists. */
  public Optional<Session> currentSession() {
    return sessions.current();
  }

  /** Ends the current session. */
  public void logout() {
    sessions.clear();
  }
}
