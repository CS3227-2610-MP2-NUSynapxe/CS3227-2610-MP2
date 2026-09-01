package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Small helpers for consistent SQLite result-set and timestamp handling. */
final class SqliteQueries {
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private SqliteQueries() {
    throw new AssertionError("Utility class");
  }

  /** Maps every row returned by a prepared statement. */
  static <T> List<T> readAll(PreparedStatement statement, RowMapper<T> mapper) throws SQLException {
    List<T> values = new ArrayList<>();
    try (ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        values.add(mapper.map(resultSet));
      }
    }
    return List.copyOf(values);
  }

  /** Formats a local timestamp for SQLite text storage. */
  static String formatTimestamp(LocalDateTime timestamp) {
    return TIMESTAMP_FORMATTER.format(timestamp);
  }

  /** Parses a local timestamp stored by {@link #formatTimestamp(LocalDateTime)}. */
  static LocalDateTime parseTimestamp(String timestamp) {
    return LocalDateTime.parse(timestamp, TIMESTAMP_FORMATTER);
  }

  /** Binds a local timestamp to a prepared statement. */
  static void bindTimestamp(PreparedStatement statement, int index, LocalDateTime timestamp)
      throws SQLException {
    statement.setString(index, formatTimestamp(timestamp));
  }

  /** Maps one result-set row to a value. */
  @FunctionalInterface
  interface RowMapper<T> {
    /** Maps the current result-set row. */
    T map(ResultSet resultSet) throws SQLException;
  }
}
