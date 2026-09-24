package nusynapxe.tools;

import java.sql.SQLException;
import nusynapxe.domain.Account;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.AccountService;

/** Creates the staff accounts required by the local demo dataset. */
final class DemoDataAccountSeeder {
  private static final String ADMIN_USERNAME = "admin.demo";
  private static final String ADMIN_PASSWORD = "DemoAdmin123!";
  private static final String ADA_USERNAME = "ada";
  private static final String ADA_PASSWORD = "ada1234!";
  private static final String GRACE_USERNAME = "grace";
  private static final String GRACE_PASSWORD = "grace123!";
  private static final String RECEPTION_USERNAME = "reception";
  private static final String RECEPTION_PASSWORD = "recept123!";

  private DemoDataAccountSeeder() {
    throw new AssertionError("Utility class");
  }

  static SeedAccounts seed(AccountService accountService) throws SQLException {
    Account admin =
        accountService.createInitialAdmin(
            ADMIN_USERNAME, "Demo Administrator", ADMIN_PASSWORD.toCharArray());
    Session adminSession = new Session(admin.id(), admin.username(), admin.role());
    Account ada =
        accountService.createStaff(
            adminSession,
            ADA_USERNAME,
            "Dr. Ada Lovelace",
            Role.DOCTOR,
            ADA_PASSWORD.toCharArray());
    Account grace =
        accountService.createStaff(
            adminSession,
            GRACE_USERNAME,
            "Dr. Grace Hopper",
            Role.DOCTOR,
            GRACE_PASSWORD.toCharArray());
    accountService.createStaff(
        adminSession,
        RECEPTION_USERNAME,
        "Demo Receptionist",
        Role.RECEPTIONIST,
        RECEPTION_PASSWORD.toCharArray());
    return new SeedAccounts(ada, grace);
  }

  record SeedAccounts(Account ada, Account grace) {}
}
