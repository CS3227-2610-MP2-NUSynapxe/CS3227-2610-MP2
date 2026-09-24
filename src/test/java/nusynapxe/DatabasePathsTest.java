package nusynapxe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DatabasePathsTest {
  @Test
  void usesAStableDatabasePathInsideTheUserHomeDirectory() {
    Path expected = Path.of(System.getProperty("user.home"), ".nusynapxe", "nusynapxe.db");

    assertEquals(expected, DatabasePaths.defaultDatabasePath());
  }

  @Test
  void normalizesARequestedDatabasePath() {
    Path requestedPath = Path.of("data", "..", "nusynapxe.db");

    assertEquals(requestedPath.toAbsolutePath().normalize(), DatabasePaths.resolve(requestedPath));
    assertEquals(DatabasePaths.defaultDatabasePath(), DatabasePaths.resolve(null));
  }

  @Test
  void configuredDatabasePathResolvesFromSystemPropertyOrDefault() {
    String original = System.getProperty("nusynapxe.database");
    try {
      System.clearProperty("nusynapxe.database");
      assertEquals(DatabasePaths.defaultDatabasePath(), DatabasePaths.configuredDatabasePath());

      System.setProperty("nusynapxe.database", "   ");
      assertEquals(DatabasePaths.defaultDatabasePath(), DatabasePaths.configuredDatabasePath());

      System.setProperty("nusynapxe.database", "custom/data/../custom.db");
      assertEquals(
          Path.of("custom/custom.db").toAbsolutePath().normalize(),
          DatabasePaths.configuredDatabasePath());
    } finally {
      if (original == null) {
        System.clearProperty("nusynapxe.database");
      } else {
        System.setProperty("nusynapxe.database", original);
      }
    }
  }
}
