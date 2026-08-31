package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import nusynapxe.domain.Account;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AuthenticationServiceTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void logsInEnabledAccountAndLogsOut() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = new AccountRepository(database);
      Account account =
          new AccountService(accounts).createInitialAdmin("admin", "secure-pass".toCharArray());
      AuthenticationService authentication = new AuthenticationService(accounts);

      Optional<Session> session = authentication.login("admin", "secure-pass".toCharArray());

      assertTrue(session.isPresent());
      assertEquals(account.id(), session.orElseThrow().accountId());
      assertEquals(Role.SYSTEM_ADMIN, session.orElseThrow().role());
      authentication.logout();
      assertFalse(authentication.currentSession().isPresent());
    }
  }

  @Test
  void rejectsUnknownWrongAndDisabledAccountsWithoutSession() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = new AccountRepository(database);
      Account account =
          new AccountService(accounts).createInitialAdmin("admin", "secure-pass".toCharArray());
      AuthenticationService authentication = new AuthenticationService(accounts);

      assertFalse(authentication.login("unknown", "secure-pass".toCharArray()).isPresent());
      assertFalse(authentication.login("admin", "wrong-pass".toCharArray()).isPresent());
      accounts.setEnabled(account.id(), false);
      assertFalse(authentication.login("admin", "secure-pass".toCharArray()).isPresent());
      assertFalse(authentication.currentSession().isPresent());
    }
  }

  @Test
  void newAuthenticationServiceStartsWithoutThePreviousSession() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository accounts = new AccountRepository(database);
      new AccountService(accounts).createInitialAdmin("admin", "secure-pass".toCharArray());
      AuthenticationService first = new AuthenticationService(accounts);
      first.login("admin", "secure-pass".toCharArray());

      AuthenticationService reopened = new AuthenticationService(accounts);

      assertFalse(reopened.currentSession().isPresent());
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("authentication.db"));
    database.open();
    return database;
  }
}
