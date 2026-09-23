package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.DoctorTimeOff;

/** Persists appointments and Doctor availability while delegating reads to a query repository. */
public final class AppointmentRepository {
  private static final String STATUS_COLUMN = "status";
  private final SqliteDatabase database;
  private final Clock clock;
  private final AppointmentQueryRepository queries;

  /** Creates an appointment repository using the Singapore clinic system clock. */
  public AppointmentRepository(SqliteDatabase database) {
    this(database, ClinicClock.system());
  }

  /** Creates an appointment repository using an injectable clinic clock. */
  public AppointmentRepository(SqliteDatabase database, Clock clock) {
    this.database = Objects.requireNonNull(database, "database");
    this.clock = ClinicClock.withClinicZone(clock);
    this.queries = new AppointmentQueryRepository(database);
  }

  /** Creates an appointment after checking the Doctor's schedule. */
  public Appointment create(
      long patientId,
      long doctorId,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      AppointmentStatus status)
      throws SQLException {
    validateInterval(startsAt, endsAt);
    Objects.requireNonNull(status, STATUS_COLUMN);
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
            String timestamp = SqliteQueries.formatTimestamp(ClinicClock.now(clock));
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

  /** Reschedules an appointment while preserving its lifecycle status. */
  public Appointment reschedule(long id, LocalDateTime startsAt, LocalDateTime endsAt)
      throws SQLException {
    validateInterval(startsAt, endsAt);
    return rescheduleInternal(id, startsAt, endsAt, null);
  }

  /** Reschedules an appointment and atomically sets its lifecycle status. */
  public Appointment reschedule(
      long id, LocalDateTime startsAt, LocalDateTime endsAt, AppointmentStatus status)
      throws SQLException {
    validateInterval(startsAt, endsAt);
    Objects.requireNonNull(status, STATUS_COLUMN);
    return rescheduleInternal(id, startsAt, endsAt, status);
  }

  private Appointment rescheduleInternal(
      long id, LocalDateTime startsAt, LocalDateTime endsAt, AppointmentStatus status)
      throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          Appointment current = queries.findById(connection, id).orElseThrow(() -> missing(id));
          ensureAvailable(connection, current.doctorId(), startsAt, endsAt, id);
          AppointmentStatus resultingStatus = status == null ? current.status() : status;
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET starts_at = ?, ends_at = ?, "
                      + "status = COALESCE(?, status), updated_at = ? WHERE id = ?")) {
            SqliteQueries.bindTimestamp(statement, 1, startsAt);
            SqliteQueries.bindTimestamp(statement, 2, endsAt);
            if (status == null) {
              statement.setNull(3, Types.VARCHAR);
            } else {
              statement.setString(3, status.name());
            }
            statement.setString(4, SqliteQueries.formatTimestamp(ClinicClock.now(clock)));
            statement.setLong(5, id);
            statement.executeUpdate();
          }
          return new Appointment(
              current.id(),
              current.patientId(),
              current.doctorId(),
              startsAt,
              endsAt,
              resultingStatus);
        });
  }

  /** Changes an appointment's lifecycle status. */
  public Appointment updateStatus(long id, AppointmentStatus status) throws SQLException {
    Objects.requireNonNull(status, STATUS_COLUMN);
    return SqliteTransactions.execute(
        database,
        connection -> {
          Appointment current = queries.findById(connection, id).orElseThrow(() -> missing(id));
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET status = ?, updated_at = ? WHERE id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, SqliteQueries.formatTimestamp(ClinicClock.now(clock)));
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
    return queries.findById(id);
  }

  /** Returns all appointments assigned to one Doctor in chronological order. */
  public List<Appointment> findByDoctor(long doctorId) throws SQLException {
    return queries.findByDoctor(doctorId);
  }

  /** Returns non-clinical appointment projections overlapping a time range. */
  public List<CalendarAppointment> findCalendarByDoctor(
      long doctorId, LocalDateTime rangeStart, LocalDateTime rangeEnd) throws SQLException {
    validateInterval(rangeStart, rangeEnd);
    return queries.findCalendarByDoctor(doctorId, rangeStart, rangeEnd);
  }

  /** Returns one bounded future-schedule page using a stable keyset cursor. */
  public CalendarSchedulePage findCalendarPageByDoctor(
      long doctorId, LocalDateTime anchor, CalendarScheduleCursor cursor, int pageSize)
      throws SQLException {
    Objects.requireNonNull(anchor, "anchor");
    CalendarSchedulePage.validatePageSize(pageSize);
    return queries.findCalendarPageByDoctor(doctorId, anchor, cursor, pageSize);
  }

  /** Returns all appointments in chronological order. */
  public List<Appointment> findAll() throws SQLException {
    return queries.search(null, null, null, null);
  }

  /** Searches appointments using optional date, Doctor, patient, and status filters. */
  public List<Appointment> search(
      LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    return queries.search(date, doctorId, patientQuery, status);
  }

  /** Searches appointment rows with patient and Doctor names loaded by the same SQL query. */
  public List<AppointmentListRow> searchListRows(
      LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    return queries.searchListRows(date, doctorId, patientQuery, status);
  }

  /** Adds a Doctor time-off interval after checking existing availability. */
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

  /** Returns all time-off intervals for one Doctor. */
  public List<DoctorTimeOff> findTimeOffByDoctor(long doctorId) throws SQLException {
    return queries.findTimeOffByDoctor(doctorId);
  }

  /** Returns one Doctor's time-off intervals overlapping a half-open range. */
  public List<DoctorTimeOff> findTimeOffByDoctor(
      long doctorId, LocalDateTime rangeStart, LocalDateTime rangeEnd) throws SQLException {
    validateInterval(rangeStart, rangeEnd);
    return queries.findTimeOffByDoctor(doctorId, rangeStart, rangeEnd);
  }

  /** Deletes a time-off interval only when it belongs to the supplied Doctor. */
  public boolean deleteTimeOff(long id, long doctorId) throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "DELETE FROM doctor_time_off WHERE id = ? AND doctor_id = ?")) {
            statement.setLong(1, id);
            statement.setLong(2, doctorId);
            return statement.executeUpdate() == 1;
          }
        });
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
        "SELECT 1 FROM appointments WHERE doctor_id = ? AND id <> ? "
            + "AND status NOT IN ('DECLINED', 'CANCELLED') "
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

  private static SQLException missing(long id) {
    return new SQLException("Appointment does not exist: " + id);
  }
}
