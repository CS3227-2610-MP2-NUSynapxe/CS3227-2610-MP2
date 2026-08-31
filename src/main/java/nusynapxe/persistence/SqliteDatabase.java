package nusynapxe.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import nusynapxe.DatabasePaths;

/** Manages the application's local SQLite connection and bootstrap schema. */
public final class SqliteDatabase implements AutoCloseable {
  private static final String JDBC_PREFIX = "jdbc:sqlite:";

  private final Path databasePath;
  private Connection jdbcConnection;

  /**
   * Creates a database manager for a requested path.
   *
   * @param requestedPath requested database path, or {@code null} for the default
   */
  public SqliteDatabase(Path requestedPath) {
    databasePath = DatabasePaths.resolve(requestedPath);
  }

  /**
   * Returns the normalized database path.
   *
   * @return database path
   */
  public Path path() {
    return databasePath;
  }

  /**
   * Opens the database and creates the initial metadata table when necessary.
   *
   * @throws SQLException if the database cannot be opened or initialized
   */
  public void open() throws SQLException {
    if (jdbcConnection != null && !jdbcConnection.isClosed()) {
      return;
    }

    createParentDirectory();
    jdbcConnection = DriverManager.getConnection(JDBC_PREFIX + databasePath);
    try (var statement = jdbcConnection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = ON");
      SchemaInitializer.initialize(jdbcConnection);
    } catch (SQLException exception) {
      close();
      throw exception;
    }
  }

  /**
   * Reports whether the database has an open JDBC connection.
   *
   * @return {@code true} when the connection is open
   * @throws SQLException if JDBC cannot determine the connection state
   */
  public boolean isOpen() throws SQLException {
    return jdbcConnection != null && !jdbcConnection.isClosed();
  }

  /**
   * Returns the connection for package-level persistence components.
   *
   * @return the open JDBC connection
   * @throws IllegalStateException if the database has not been opened
   */
  Connection connection() {
    if (jdbcConnection == null) {
      throw new IllegalStateException("The SQLite database is not open");
    }
    return jdbcConnection;
  }

  /**
   * Closes the JDBC connection when one is open.
   *
   * @throws SQLException if JDBC cannot close the connection
   */
  @Override
  @SuppressWarnings("PMD.NullAssignment")
  public void close() throws SQLException {
    if (jdbcConnection != null) {
      try {
        jdbcConnection.close();
      } finally {
        jdbcConnection = null;
      }
    }
  }

  private void createParentDirectory() throws SQLException {
    Path parent = databasePath.getParent();
    if (parent == null) {
      return;
    }
    try {
      Files.createDirectories(parent);
    } catch (IOException exception) {
      throw new SQLException("Could not create database directory: " + parent, exception);
    }
  }
}
