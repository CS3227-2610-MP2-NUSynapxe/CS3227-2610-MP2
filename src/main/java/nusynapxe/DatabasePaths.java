package nusynapxe;

import java.nio.file.Path;

/** Resolves the locations used by NUSynapxe for local SQLite storage. */
public final class DatabasePaths {
  private static final String DATABASE_DIRECTORY = ".nusynapxe";
  private static final String DATABASE_FILE_NAME = "nusynapxe.db";
  private static final String DATABASE_PROPERTY = "nusynapxe.database";

  private DatabasePaths() {
    throw new AssertionError("Utility class");
  }

  /**
   * Returns the default per-user database location.
   *
   * @return the default SQLite database path
   */
  public static Path defaultDatabasePath() {
    return Path.of(System.getProperty("user.home"), DATABASE_DIRECTORY, DATABASE_FILE_NAME);
  }

  /**
   * Returns the configured database location, or the default when none was supplied.
   *
   * @return the normalized configured database path
   */
  public static Path configuredDatabasePath() {
    String configuredPath = System.getProperty(DATABASE_PROPERTY);
    if (configuredPath == null || configuredPath.isBlank()) {
      return defaultDatabasePath();
    }
    return resolve(Path.of(configuredPath));
  }

  /**
   * Resolves a database path to an absolute normalized path.
   *
   * @param requestedPath path supplied by a caller, or {@code null} for the default
   * @return an absolute normalized database path
   */
  public static Path resolve(Path requestedPath) {
    Path path = requestedPath == null ? defaultDatabasePath() : requestedPath;
    return path.toAbsolutePath().normalize();
  }
}
