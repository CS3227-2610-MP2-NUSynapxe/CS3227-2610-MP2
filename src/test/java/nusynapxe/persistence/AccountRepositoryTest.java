package nusynapxe.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import nusynapxe.domain.Account;
import nusynapxe.domain.AccountCredential;
import nusynapxe.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AccountRepositoryTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void persistsPublicAccountAndCredentialFields() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository repository = new AccountRepository(database);
      byte[] salt = {1, 2, 3};
      byte[] verifier = {4, 5, 6};

      Account created = repository.create("doctor", "Dr. Ada", Role.DOCTOR, salt, verifier);

      assertTrue(repository.hasAccounts());
      Optional<AccountCredential> found = repository.findCredentials("DOCTOR");
      assertTrue(found.isPresent());
      assertEquals(created, found.orElseThrow().account());
      assertArrayEquals(salt, found.orElseThrow().salt());
      assertArrayEquals(verifier, found.orElseThrow().verifier());
      assertEquals(created, repository.findById(created.id()).orElseThrow());
    }
  }

  @Test
  void rejectsDuplicateUsernames() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository repository = new AccountRepository(database);
      repository.create("same", "First", Role.DOCTOR, new byte[] {1}, new byte[] {2});

      assertThrows(
          SQLException.class,
          () ->
              repository.create(
                  "SAME", "Second", Role.RECEPTIONIST, new byte[] {3}, new byte[] {4}));
      assertEquals(1, repository.findAll().size());
    }
  }

  @Test
  void canDisableAnAccount() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountRepository repository = new AccountRepository(database);
      Account account =
          repository.create(
              "reception", "Reception", Role.RECEPTIONIST, new byte[] {1}, new byte[] {2});

      repository.setEnabled(account.id(), false);

      assertFalse(repository.findById(account.id()).orElseThrow().enabled());
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("accounts.db"));
    database.open();
    return database;
  }
}
