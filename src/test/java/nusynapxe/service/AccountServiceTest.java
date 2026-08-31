package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import nusynapxe.domain.Account;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AccountServiceTest {
  @TempDir private Path temporaryDirectory;

  @Test
  void createsInitialAdministratorOnlyOnce() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountService service = new AccountService(new AccountRepository(database));

      Account admin = service.createInitialAdmin("admin", "secure-pass".toCharArray());

      assertEquals(Role.SYSTEM_ADMIN, admin.role());
      assertFalse(service.needsInitialSetup());
      assertThrows(
          ValidationException.class,
          () -> service.createInitialAdmin("second", "secure-pass".toCharArray()));
    }
  }

  @Test
  void systemAdminCreatesOnlyStaffRoles() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountService service = new AccountService(new AccountRepository(database));
      Account admin = service.createInitialAdmin("admin", "secure-pass".toCharArray());
      Session session = new Session(admin.id(), admin.username(), admin.role());

      assertEquals(
          Role.DOCTOR,
          service
              .createStaff(session, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray())
              .role());
      assertEquals(
          Role.RECEPTIONIST,
          service
              .createStaff(
                  session,
                  "reception",
                  "Reception",
                  Role.RECEPTIONIST,
                  "reception-pass".toCharArray())
              .role());
      assertThrows(
          ValidationException.class,
          () ->
              service.createStaff(
                  session, "other-admin", "Admin", Role.SYSTEM_ADMIN, "admin-pass".toCharArray()));
    }
  }

  @Test
  void rejectsInvalidAndUnauthorizedAccountRequests() throws SQLException {
    try (SqliteDatabase database = openDatabase()) {
      AccountService service = new AccountService(new AccountRepository(database));
      Account admin = service.createInitialAdmin("admin", "secure-pass".toCharArray());
      Session doctor = new Session(admin.id(), admin.username(), Role.DOCTOR);

      assertThrows(
          AuthorizationException.class,
          () ->
              service.createStaff(
                  doctor, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray()));
      assertThrows(
          ValidationException.class,
          () ->
              service.createStaff(
                  new Session(admin.id(), admin.username(), Role.SYSTEM_ADMIN),
                  "",
                  "Name",
                  Role.DOCTOR,
                  "doctor-pass".toCharArray()));
    }
  }

  private SqliteDatabase openDatabase() throws SQLException {
    SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("service.db"));
    database.open();
    return database;
  }
}
