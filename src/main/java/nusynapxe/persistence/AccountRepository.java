package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Account;
import nusynapxe.domain.AccountCredential;
import nusynapxe.domain.Role;

/** Persists staff accounts and their credential material. */
public final class AccountRepository {
  private static final String ACCOUNT_COLUMNS = "id, username, display_name, role, enabled";
  private final SqliteDatabase database;

  /** Creates an account repository backed by an opened database. */
  public AccountRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /** Reports whether at least one account exists. */
  public boolean hasAccounts() throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement("SELECT 1 FROM users LIMIT 1")) {
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  /** Finds credential material by case-insensitive username. */
  public Optional<AccountCredential> findCredentials(String username) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT "
                    + ACCOUNT_COLUMNS
                    + ", password_salt, password_verifier FROM users WHERE username = ?")) {
      statement.setString(1, username);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(readCredential(resultSet));
      }
    }
  }

  /** Finds public account information by identifier. */
  public Optional<Account> findById(long id) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement("SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE id = ?")) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readAccount(resultSet)) : Optional.empty();
      }
    }
  }

  /** Returns all public account information in username order. */
  public List<Account> findAll() throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement("SELECT " + ACCOUNT_COLUMNS + " FROM users ORDER BY username")) {
      return SqliteQueries.readAll(statement, AccountRepository::readAccount);
    }
  }

  /** Creates an enabled account and returns its public information. */
  public Account create(
      String username, String displayName, Role role, byte[] salt, byte[] verifier)
      throws SQLException {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(salt, "salt");
    Objects.requireNonNull(verifier, "verifier");
    return SqliteTransactions.execute(
        database, connection -> insert(connection, username, displayName, role, salt, verifier));
  }

  /** Creates the first administrator only when the account table is empty. */
  public Optional<Account> createInitial(
      String username, String displayName, Role role, byte[] salt, byte[] verifier)
      throws SQLException {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(salt, "salt");
    Objects.requireNonNull(verifier, "verifier");
    return SqliteTransactions.execute(
        database,
        connection ->
            hasAccounts(connection)
                ? Optional.empty()
                : Optional.of(insert(connection, username, displayName, role, salt, verifier)));
  }

  /** Changes whether an account is enabled. */
  public void setEnabled(long id, boolean enabled) throws SQLException {
    SqliteTransactions.execute(
        database,
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement("UPDATE users SET enabled = ? WHERE id = ?")) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setLong(2, id);
            statement.executeUpdate();
          }
          return null;
        });
  }

  private static boolean hasAccounts(java.sql.Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users LIMIT 1")) {
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static Account insert(
      java.sql.Connection connection,
      String username,
      String displayName,
      Role role,
      byte[] salt,
      byte[] verifier)
      throws SQLException {
    String sql =
        "INSERT INTO users(username, display_name, role, enabled, password_salt, "
            + "password_verifier, created_at) VALUES (?, ?, ?, 1, ?, ?, ?)";
    try (PreparedStatement statement =
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setString(1, username);
      statement.setString(2, displayName);
      statement.setString(3, role.name());
      statement.setBytes(4, salt.clone());
      statement.setBytes(5, verifier.clone());
      statement.setString(6, SqliteQueries.formatTimestamp(LocalDateTime.now()));
      statement.executeUpdate();
      try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
        if (!generatedKeys.next()) {
          throw new SQLException("SQLite did not return the new account identifier");
        }
        return new Account(generatedKeys.getLong(1), username, displayName, role, true);
      }
    }
  }

  private static AccountCredential readCredential(ResultSet resultSet) throws SQLException {
    return new AccountCredential(
        readAccount(resultSet),
        resultSet.getBytes("password_salt"),
        resultSet.getBytes("password_verifier"));
  }

  private static Account readAccount(ResultSet resultSet) throws SQLException {
    return new Account(
        resultSet.getLong("id"),
        resultSet.getString("username"),
        resultSet.getString("display_name"),
        Role.valueOf(resultSet.getString("role")),
        resultSet.getInt("enabled") == 1);
  }
}
