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
  }
}
