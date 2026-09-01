package nusynapxe.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Runs repository work atomically against the application's SQLite connection. */
final class SqliteTransactions {
  private SqliteTransactions() {
    throw new AssertionError("Utility class");
  }

  /** Work that may use a transaction connection. */
  @FunctionalInterface
  interface Work<T> {
    /** Executes the work. */
    T execute(Connection connection) throws SQLException;
  }

  /** Commits successful work and rolls back SQL or runtime failures. */
  @SuppressWarnings({"PMD.CloseResource", "PMD.AvoidCatchingGenericException"})
  static <T> T execute(SqliteDatabase database, Work<T> work) throws SQLException {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(work, "work");
    Connection connection = database.connection();
    boolean originalAutoCommit = connection.getAutoCommit();
    try {
      connection.setAutoCommit(false);
      T result = work.execute(connection);
      connection.commit();
      return result;
    } catch (SQLException exception) {
      rollback(connection, exception);
      throw exception;
    } catch (RuntimeException exception) {
      rollback(connection, exception);
      throw exception;
    } finally {
      connection.setAutoCommit(originalAutoCommit);
    }
  }

  private static void rollback(Connection connection, Exception failure) {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      failure.addSuppressed(rollbackFailure);
    }
  }
}
