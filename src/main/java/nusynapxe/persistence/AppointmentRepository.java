package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.DoctorTimeOff;

/** Persists appointments and doctor availability intervals. */
public final class AppointmentRepository {
  private static final String SELECT_PREFIX = "SELECT ";
  private static final String APPOINTMENT_COLUMNS =
      "id, patient_id, doctor_id, starts_at, ends_at, status";
  private static final String TIME_OFF_COLUMNS = "id, doctor_id, starts_at, ends_at";
  private final SqliteDatabase database;

  /** Creates an appointment repository backed by an opened database. */
  public AppointmentRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /** Creates an appointment after checking the doctor's availability. */
  public Appointment create(
      long patientId,
      long doctorId,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      AppointmentStatus status)
      throws SQLException {
    validateInterval(startsAt, endsAt);
    Objects.requireNonNull(status, "status");
    return SqliteTransactions.execute(
        database,
        connection -> {
          ensureAvailable(connection, doctorId, startsAt, endsAt, 0);
          String sql =
              "INSERT INTO appointments(patient_id, doctor_id, starts_at, ends_at, status, "
                  + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
          try (PreparedStatement statement =
              connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, patientId);
            statement.setLong(2, doctorId);
            SqliteQueries.bindTimestamp(statement, 3, startsAt);
            SqliteQueries.bindTimestamp(statement, 4, endsAt);
            statement.setString(5, status.name());
            String timestamp = SqliteQueries.formatTimestamp(LocalDateTime.now());
            statement.setString(6, timestamp);
            statement.setString(7, timestamp);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
              if (!generatedKeys.next()) {
                throw new SQLException("SQLite did not return the new appointment identifier");
              }
              return new Appointment(
                  generatedKeys.getLong(1), patientId, doctorId, startsAt, endsAt, status);
            }
          }
        });
  }

  /** Reschedules an appointment after checking the replacement interval. */
  public Appointment reschedule(long id, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    validateInterval(startsAt, endsAt);
    return SqliteTransactions.execute(
        database,
        connection -> {
          Appointment current = findById(connection, id).orElseThrow(() -> missing(id));
          ensureAvailable(connection, current.doctorId(), startsAt, endsAt, id);
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET starts_at = ?, ends_at = ?, updated_at = ? WHERE id = ?")) {
            SqliteQueries.bindTimestamp(statement, 1, startsAt);
            SqliteQueries.bindTimestamp(statement, 2, endsAt);
            statement.setString(3, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            statement.setLong(4, id);
            statement.executeUpdate();
          }
          return new Appointment(
              current.id(),
              current.patientId(),
              current.doctorId(),
              startsAt,
              endsAt,
              current.status());
        });
  }

  /** Changes an appointment's lifecycle status. */
  public Appointment updateStatus(long id, AppointmentStatus status) throws SQLException {
    Objects.requireNonNull(status, "status");
    return SqliteTransactions.execute(
        database,
        connection -> {
          Appointment current = findById(connection, id).orElseThrow(() -> missing(id));
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET status = ?, updated_at = ? WHERE id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            statement.setLong(3, id);
            statement.executeUpdate();
          }
          return new Appointment(
              current.id(),
              current.patientId(),
              current.doctorId(),
              current.startsAt(),
              current.endsAt(),
              status);
        });
  }

  /** Finds an appointment by identifier. */
  public Optional<Appointment> findById(long id) throws SQLException {
    return findById(database.connection(), id);
  }

  /** Returns all appointments assigned to one doctor in chronological order. */
  public List<Appointment> findByDoctor(long doctorId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                SELECT_PREFIX
                    + APPOINTMENT_COLUMNS
                    + " FROM appointments WHERE doctor_id = ? ORDER BY starts_at")) {
      statement.setLong(1, doctorId);
      return SqliteQueries.readAll(statement, AppointmentRepository::readAppointment);
    }
  }

  /** Returns all appointments in chronological order. */
  public List<Appointment> findAll() throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                SELECT_PREFIX + APPOINTMENT_COLUMNS + " FROM appointments ORDER BY starts_at")) {
      return SqliteQueries.readAll(statement, AppointmentRepository::readAppointment);
    }
  }

  /** Adds a doctor time-off interval after checking existing availability. */
  public DoctorTimeOff createTimeOff(long doctorId, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    validateInterval(startsAt, endsAt);
    return SqliteTransactions.execute(
        database,
        connection -> {
          ensureAvailable(connection, doctorId, startsAt, endsAt, 0);
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO doctor_time_off(doctor_id, starts_at, ends_at) VALUES (?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, doctorId);
            SqliteQueries.bindTimestamp(statement, 2, startsAt);
            SqliteQueries.bindTimestamp(statement, 3, endsAt);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
              if (!generatedKeys.next()) {
                throw new SQLException("SQLite did not return the time-off identifier");
              }
              return new DoctorTimeOff(generatedKeys.getLong(1), doctorId, startsAt, endsAt);
            }
          }
        });
  }

  /** Returns time-off intervals for one doctor. */
  public List<DoctorTimeOff> findTimeOffByDoctor(long doctorId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                SELECT_PREFIX
                    + TIME_OFF_COLUMNS
                    + " FROM doctor_time_off WHERE doctor_id = ? ORDER BY starts_at")) {
      statement.setLong(1, doctorId);
      return SqliteQueries.readAll(statement, AppointmentRepository::readTimeOff);
    }
  }

  private static void validateInterval(LocalDateTime startsAt, LocalDateTime endsAt) {
    Objects.requireNonNull(startsAt, "startsAt");
    Objects.requireNonNull(endsAt, "endsAt");
    if (!endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("The interval must end after it starts");
    }
  }

  private static void ensureAvailable(
      java.sql.Connection connection,
      long doctorId,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      long excludedAppointmentId)
      throws SQLException {
    if (hasAppointmentConflict(connection, doctorId, startsAt, endsAt, excludedAppointmentId)
        || hasTimeOffConflict(connection, doctorId, startsAt, endsAt)) {
      throw new SQLException("The doctor's schedule has a conflict");
    }
  }

  private static boolean hasAppointmentConflict(
      java.sql.Connection connection,
      long doctorId,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      long excludedAppointmentId)
      throws SQLException {
    String sql =
        "SELECT 1 FROM appointments WHERE doctor_id = ? AND id <> ? AND status <> 'CANCELLED' "
            + "AND starts_at < ? AND ends_at > ? LIMIT 1";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, doctorId);
      statement.setLong(2, excludedAppointmentId);
      SqliteQueries.bindTimestamp(statement, 3, endsAt);
      SqliteQueries.bindTimestamp(statement, 4, startsAt);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static boolean hasTimeOffConflict(
      java.sql.Connection connection, long doctorId, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    String sql =
        "SELECT 1 FROM doctor_time_off WHERE doctor_id = ? AND starts_at < ? AND ends_at > ? LIMIT 1";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, doctorId);
      SqliteQueries.bindTimestamp(statement, 2, endsAt);
      SqliteQueries.bindTimestamp(statement, 3, startsAt);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static Optional<Appointment> findById(java.sql.Connection connection, long id)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            SELECT_PREFIX + APPOINTMENT_COLUMNS + " FROM appointments WHERE id = ?")) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readAppointment(resultSet)) : Optional.empty();
      }
    }
  }

  private static SQLException missing(long id) {
    return new SQLException("Appointment does not exist: " + id);
  }

  private static Appointment readAppointment(ResultSet resultSet) throws SQLException {
    return new Appointment(
        resultSet.getLong("id"),
        resultSet.getLong("patient_id"),
        resultSet.getLong("doctor_id"),
        SqliteQueries.parseTimestamp(resultSet.getString("starts_at")),
        SqliteQueries.parseTimestamp(resultSet.getString("ends_at")),
        AppointmentStatus.valueOf(resultSet.getString("status")));
  }

  private static DoctorTimeOff readTimeOff(ResultSet resultSet) throws SQLException {
    return new DoctorTimeOff(
        resultSet.getLong("id"),
        resultSet.getLong("doctor_id"),
        SqliteQueries.parseTimestamp(resultSet.getString("starts_at")),
        SqliteQueries.parseTimestamp(resultSet.getString("ends_at")));
  }
}
