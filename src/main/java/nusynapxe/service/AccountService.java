package nusynapxe.service;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import nusynapxe.domain.Account;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;

/** Application operations for bootstrapping and managing staff accounts. */
@SuppressWarnings("PMD.UseVarargs")
public final class AccountService {
  private final AccountRepository accounts;
  private final PasswordPolicy passwordPolicy;
  private final PasswordHasher passwordHasher;

  /**
   * Creates an account service with the default password policy and hasher.
   *
   * @param accounts repository used for account persistence
   * @throws NullPointerException if {@code accounts} is {@code null}
   */
  public AccountService(AccountRepository accounts) {
    this(accounts, new PasswordPolicy(), new PasswordHasher());
  }

  AccountService(
      AccountRepository accounts, PasswordPolicy passwordPolicy, PasswordHasher passwordHasher) {
    this.accounts = Objects.requireNonNull(accounts, "accounts");
    this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
  }

  /**
   * Reports whether the first administrator still needs to be created.
   *
   * @return {@code true} when no account has been persisted
   * @throws SQLException if the account query fails
   */
  public boolean needsInitialSetup() throws SQLException {
    return !accounts.hasAccounts();
  }

  /**
   * Creates the first enabled System Admin account atomically.
   *
   * @param username requested login name
   * @param password password to hash and store
   * @return the created public account
   * @throws SQLException if account persistence fails
   * @throws NullPointerException if {@code password} is {@code null}
   * @throws ValidationException if an input is invalid or an administrator already exists
   */
  public Account createInitialAdmin(String username, char[] password) throws SQLException {
    return createInitialAdmin(username, "System Admin", password);
  }

  /**
   * Creates the first enabled System Admin account with a display name.
   *
   * @param username requested login name
   * @param displayName user-facing account name
   * @param password password to hash and store
   * @return the created public account
   * @throws SQLException if account persistence fails
   * @throws NullPointerException if {@code password} is {@code null}
   * @throws ValidationException if an input is invalid or an administrator already exists
   */
  public Account createInitialAdmin(String username, String displayName, char[] password)
      throws SQLException {
    String normalizedUsername = required(username, "Username");
    String normalizedDisplayName = required(displayName, "Display name");
    PasswordHash hash = hashPassword(password);
    try {
      return accounts
          .createInitial(
              normalizedUsername,
              normalizedDisplayName,
              Role.SYSTEM_ADMIN,
              hash.salt(),
              hash.verifier())
          .orElseThrow(() -> new ValidationException("Initial administrator already exists"));
    } catch (ValidationException exception) {
      throw exception;
    } catch (SQLException exception) {
      throw new ValidationException("The administrator account could not be created", exception);
    }
  }

  /**
   * Creates a Doctor or Receptionist account as an authenticated System Admin.
   *
   * @param actor authenticated System Admin session
   * @param username requested login name
   * @param displayName user-facing account name
   * @param role account role; only Doctor and Receptionist are accepted
   * @param password password to hash and store
   * @return the created public account
   * @throws AuthorizationException if the actor is not a System Admin
   * @throws SQLException if account persistence fails
   * @throws ValidationException if an input or role is invalid, or the username is already used
   */
  public Account createStaff(
      Session actor, String username, String displayName, Role role, char[] password)
      throws SQLException {
    Authorization.requireRole(actor, Role.SYSTEM_ADMIN);
    if (role != Role.DOCTOR && role != Role.RECEPTIONIST) {
      throw new ValidationException("Only Doctor and Receptionist accounts can be created here");
    }
    String normalizedUsername = required(username, "Username");
    String normalizedDisplayName = required(displayName, "Display name");
    PasswordHash hash = hashPassword(password);
    try {
      return accounts.create(
          normalizedUsername, normalizedDisplayName, role, hash.salt(), hash.verifier());
    } catch (SQLException exception) {
      throw new ValidationException(
          "The username is already in use or the account could not be created", exception);
    }
  }

  /**
   * Returns public account information to an authenticated System Admin.
   *
   * @param actor authenticated System Admin session
   * @return immutable public account list
   * @throws AuthorizationException if the actor is not a System Admin
   * @throws SQLException if the account query fails
   */
  public List<Account> listAccounts(Session actor) throws SQLException {
    Authorization.requireRole(actor, Role.SYSTEM_ADMIN);
    return accounts.findAll();
  }

  /**
   * Returns Doctor accounts to a Receptionist or System Admin for scheduling.
   *
   * @param actor authenticated Receptionist or System Admin session
   * @return immutable list of Doctor accounts
   * @throws AuthorizationException if the actor is not permitted
   * @throws SQLException if the account query fails
   */
  public List<Account> listDoctors(Session actor) throws SQLException {
    if (actor == null || (actor.role() != Role.RECEPTIONIST && actor.role() != Role.SYSTEM_ADMIN)) {
      throw new AuthorizationException("You are not allowed to list Doctors");
    }
    return accounts.findAll().stream().filter(account -> account.role() == Role.DOCTOR).toList();
  }

  private PasswordHash hashPassword(char[] password) {
    Objects.requireNonNull(password, "password");
    if (!passwordPolicy.isValid(password)) {
      throw new ValidationException("Password must contain at least 8 non-blank characters");
    }
    try {
      return passwordHasher.hash(password);
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private static String required(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(fieldName + " is required");
    }
    return value.trim();
  }
}
