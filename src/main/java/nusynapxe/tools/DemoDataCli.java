package nusynapxe.tools;

import java.nio.file.Path;
import java.sql.SQLException;

/** Command-line entry point used by the demo database PowerShell scripts. */
public final class DemoDataCli {
  private static final String RESET_COMMAND = "reset";
  private static final String SEED_COMMAND = "seed";
  private static final String FORCE_FLAG = "--force";
  private static final String RESET_FLAG = "--reset";
  private static final int COMMAND_ARGUMENT_COUNT = 2;
  private static final String USAGE =
      "Usage: reset <database-path> --force | seed <database-path> [--reset]";

  private DemoDataCli() {
    throw new AssertionError("Utility class");
  }

  /**
   * Runs one reset or seed operation.
   *
   * @param arguments command name, database path, and the command's confirmation flag
   * @throws SQLException if the requested database operation fails
   */
  public static void main(String[] arguments) throws SQLException {
    if (arguments == null || arguments.length < COMMAND_ARGUMENT_COUNT) {
      throw new IllegalArgumentException(USAGE);
    }

    String command = arguments[0];
    Path databasePath = Path.of(arguments[1]);
    if (RESET_COMMAND.equals(command)) {
      requireExactFlag(arguments, FORCE_FLAG);
      DemoDataSeeder.reset(databasePath);
      return;
    }
    if (SEED_COMMAND.equals(command)) {
      if (arguments.length == COMMAND_ARGUMENT_COUNT) {
        DemoDataSeeder.seed(databasePath);
        return;
      }
      requireExactFlag(arguments, RESET_FLAG);
      DemoDataSeeder.reset(databasePath);
      DemoDataSeeder.seed(databasePath);
      return;
    }
    throw new IllegalArgumentException(USAGE);
  }

  private static void requireExactFlag(String[] arguments, String expectedFlag) {
    if (arguments.length != 3 || !expectedFlag.equals(arguments[2])) {
      throw new IllegalArgumentException(USAGE);
    }
  }
}
