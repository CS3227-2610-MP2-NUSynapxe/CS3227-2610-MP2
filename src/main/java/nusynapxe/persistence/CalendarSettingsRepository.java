package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.WorkingInterval;

/** Persists the complete Doctor-owned Calendar display preference snapshot. */
public final class CalendarSettingsRepository {
  private final SqliteDatabase database;

  /**
   * Creates a Calendar settings repository backed by an opened database.
   *
   * @param database database used for Calendar preference persistence
   * @throws NullPointerException if {@code database} is {@code null}
   */
  public CalendarSettingsRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /**
   * Finds saved settings for one Doctor.
   *
   * @param doctorId doctor identifier
   * @return saved settings, or empty when the Doctor has no saved profile
   * @throws IllegalArgumentException if {@code doctorId} is not positive
   * @throws SQLException if the settings query or stored-value conversion fails
   */
  public Optional<DoctorCalendarSettings> findByDoctor(long doctorId) throws SQLException {
    requireDoctorId(doctorId);
    String firstDay;
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT first_day_of_week FROM doctor_calendar_settings WHERE doctor_id = ?")) {
      statement.setLong(1, doctorId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        firstDay = resultSet.getString(1);
      }
    }

    Map<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      intervals.put(day, new ArrayList<>());
    }
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT day_of_week, start_minute, end_minute "
                    + "FROM doctor_working_intervals WHERE doctor_id = ? "
                    + "ORDER BY day_of_week, start_minute")) {
      statement.setLong(1, doctorId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          DayOfWeek day = parseDay(resultSet.getString("day_of_week"));
          intervals
              .get(day)
              .add(
                  new WorkingInterval(
                      resultSet.getInt("start_minute"), resultSet.getInt("end_minute")));
        }
      }
    }
    try {
      return Optional.of(new DoctorCalendarSettings(doctorId, parseDay(firstDay), intervals));
    } catch (IllegalArgumentException exception) {
      throw new SQLException("Stored Calendar settings are invalid", exception);
    }
  }

  /**
   * Atomically replaces one Doctor's complete Calendar settings snapshot.
   *
   * @param settings validated settings to persist
   * @return the persisted settings
   * @throws NullPointerException if {@code settings} is {@code null}
   * @throws SQLException if the replacement transaction fails
   */
  public DoctorCalendarSettings save(DoctorCalendarSettings settings) throws SQLException {
    Objects.requireNonNull(settings, "settings");
    return SqliteTransactions.execute(
        database,
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO doctor_calendar_settings(doctor_id, first_day_of_week) "
                      + "VALUES (?, ?) ON CONFLICT(doctor_id) DO UPDATE SET "
                      + "first_day_of_week = excluded.first_day_of_week")) {
            statement.setLong(1, settings.doctorId());
            statement.setString(2, settings.firstDayOfWeek().name());
            statement.executeUpdate();
          }
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "DELETE FROM doctor_working_intervals WHERE doctor_id = ?")) {
            statement.setLong(1, settings.doctorId());
            statement.executeUpdate();
          }
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO doctor_working_intervals(doctor_id, day_of_week, "
                      + "start_minute, end_minute) VALUES (?, ?, ?, ?)")) {
            for (DayOfWeek day : DayOfWeek.values()) {
              for (WorkingInterval interval : settings.intervals(day)) {
                statement.setLong(1, settings.doctorId());
                statement.setString(2, day.name());
                statement.setInt(3, interval.startMinute());
                statement.setInt(4, interval.endMinute());
                statement.addBatch();
              }
            }
            statement.executeBatch();
          }
          return settings;
        });
  }

  private static void requireDoctorId(long doctorId) {
    if (doctorId <= 0) {
      throw new IllegalArgumentException("Doctor identifier must be positive");
    }
  }

  private static DayOfWeek parseDay(String value) throws SQLException {
    if (value == null) {
      throw new SQLException("Stored Calendar day is invalid");
    }
    try {
      return DayOfWeek.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new SQLException("Stored Calendar day is invalid", exception);
    }
  }
}
