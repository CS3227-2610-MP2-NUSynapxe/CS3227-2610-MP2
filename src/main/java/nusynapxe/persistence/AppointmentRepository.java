package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.DoctorTimeOff;

/** Persists appointments and doctor availability intervals. */
public final class AppointmentRepository {
  private static final String SELECT_PREFIX = "SELECT ";
  private static final String STATUS_COLUMN = "status";
  private static final String APPOINTMENT_COLUMNS =
      "id, patient_id, doctor_id, starts_at, ends_at, " + STATUS_COLUMN;
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
    return rescheduleInternal(id, startsAt, endsAt, null);
  }

  /** Reschedules an appointment and sets its resulting lifecycle status atomically. */
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
          Appointment current = findById(connection, id).orElseThrow(() -> missing(id));
          ensureAvailable(connection, current.doctorId(), startsAt, endsAt, id);
          AppointmentStatus resultingStatus = status == null ? current.status() : status;
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET starts_at = ?, ends_at = ?, "
                      + "status = COALESCE(?, status), updated_at = ? WHERE id = ?")) {
            SqliteQueries.bindTimestamp(statement, 1, startsAt);
            SqliteQueries.bindTimestamp(statement, 2, endsAt);
            if (status != null) {
              statement.setString(3, status.name());
            } else {
              statement.setNull(3, Types.VARCHAR);
            }
            statement.setString(4, SqliteQueries.formatTimestamp(LocalDateTime.now()));
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

  /** Returns a Doctor's non-clinical appointment projections overlapping a time range. */
  public List<CalendarAppointment> findCalendarByDoctor(
      long doctorId, LocalDateTime rangeStart, LocalDateTime rangeEnd) throws SQLException {
    validateInterval(rangeStart, rangeEnd);
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT a.id, a.patient_id, a.starts_at, a.ends_at, a.status, "
                    + "p.first_name, p.last_name FROM appointments a "
                    + "JOIN patients p ON p.id = a.patient_id "
                    + "WHERE a.doctor_id = ? AND a.starts_at < ? AND a.ends_at > ? "
                    + "AND a.status NOT IN ('DECLINED', 'CANCELLED') "
                    + "ORDER BY a.starts_at, a.id")) {
      statement.setLong(1, doctorId);
      SqliteQueries.bindTimestamp(statement, 2, rangeEnd);
      SqliteQueries.bindTimestamp(statement, 3, rangeStart);
      return SqliteQueries.readAll(statement, AppointmentRepository::readCalendarAppointment);
    }
  }

  /**
   * Returns one bounded future-schedule page for a Doctor using a stable keyset cursor.
   *
   * <p>The anchor is inclusive. The extra look-ahead row is used to determine whether another page
   * exists without issuing an unbounded read.
   */
  public CalendarSchedulePage findCalendarPageByDoctor(
      long doctorId, LocalDateTime anchor, CalendarScheduleCursor cursor, int pageSize)
      throws SQLException {
    Objects.requireNonNull(anchor, "anchor");
    CalendarSchedulePage.validatePageSize(pageSize);

    StringBuilder sql =
        new StringBuilder(
            "SELECT a.id, a.patient_id, a.starts_at, a.ends_at, a.status, "
                + "p.first_name, p.last_name FROM appointments a "
                + "JOIN patients p ON p.id = a.patient_id "
                + "WHERE a.doctor_id = ? AND a.starts_at >= ? "
                + "AND a.status NOT IN ('DECLINED', 'CANCELLED')");
    if (cursor != null) {
      sql.append(" AND (a.starts_at > ? OR (a.starts_at = ? AND a.id > ?))");
    }
    sql.append(" ORDER BY a.starts_at, a.id LIMIT ?");

    try (PreparedStatement statement = database.connection().prepareStatement(sql.toString())) {
      statement.setLong(1, doctorId);
      SqliteQueries.bindTimestamp(statement, 2, anchor);
      int parameter = 3;
      if (cursor != null) {
        SqliteQueries.bindTimestamp(statement, parameter, cursor.startsAt());
        parameter++;
        SqliteQueries.bindTimestamp(statement, parameter, cursor.startsAt());
        parameter++;
        statement.setLong(parameter, cursor.appointmentId());
        parameter++;
      }
      statement.setInt(parameter, pageSize + 1);

      List<CalendarAppointment> fetched =
          SqliteQueries.readAll(statement, AppointmentRepository::readCalendarAppointment);
      boolean hasMore = fetched.size() > pageSize;
      List<CalendarAppointment> pageAppointments =
          hasMore ? List.copyOf(fetched.subList(0, pageSize)) : fetched;
      CalendarScheduleCursor nextCursor = null;
      if (hasMore) {
        CalendarAppointment last = pageAppointments.get(pageAppointments.size() - 1);
        nextCursor = new CalendarScheduleCursor(last.startsAt(), last.appointmentId());
      }
      return new CalendarSchedulePage(pageAppointments, nextCursor, hasMore);
    }
  }

  /** Returns all appointments in chronological order. */
  public List<Appointment> findAll() throws SQLException {
    return search(null, null, null, null);
  }

  /** Searches appointments using optional date, Doctor, patient, and status filters. */
  public List<Appointment> search(
      LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    StringBuilder sql =
        new StringBuilder(
            "SELECT a.id, a.patient_id, a.doctor_id, a.starts_at, a.ends_at, a.status "
                + "FROM appointments a JOIN patients p ON p.id = a.patient_id WHERE 1 = 1");
    List<Object> parameters = new java.util.ArrayList<>();
    if (date != null) {
      sql.append(" AND a.starts_at LIKE ?");
      parameters.add(date + "%");
    }
    if (doctorId != null) {
      sql.append(" AND a.doctor_id = ?");
      parameters.add(doctorId);
    }
    if (status != null) {
      sql.append(" AND a.status = ?");
      parameters.add(status.name());
    }
    if (patientQuery != null && !patientQuery.trim().isEmpty()) {
      sql.append(
          " AND (CAST(p.id AS TEXT) LIKE ? OR LOWER(p.first_name) LIKE ? "
              + "OR LOWER(p.last_name) LIKE ? OR LOWER(p.email) LIKE ?)");
      String pattern = "%" + patientQuery.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      parameters.add(pattern);
      parameters.add(pattern);
      parameters.add(pattern);
      parameters.add(pattern);
    }
    sql.append(" ORDER BY a.starts_at, a.id");
    try (PreparedStatement statement = database.connection().prepareStatement(sql.toString())) {
      for (int index = 0; index < parameters.size(); index++) {
        Object parameter = parameters.get(index);
        if (parameter instanceof Long value) {
          statement.setLong(index + 1, value);
        } else {
          statement.setString(index + 1, parameter.toString());
        }
      }
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
        AppointmentStatus.valueOf(resultSet.getString(STATUS_COLUMN)));
  }

  private static DoctorTimeOff readTimeOff(ResultSet resultSet) throws SQLException {
    return new DoctorTimeOff(
        resultSet.getLong("id"),
        resultSet.getLong("doctor_id"),
        SqliteQueries.parseTimestamp(resultSet.getString("starts_at")),
        SqliteQueries.parseTimestamp(resultSet.getString("ends_at")));
  }

  private static CalendarAppointment readCalendarAppointment(ResultSet resultSet)
      throws SQLException {
    long patientId = resultSet.getLong("patient_id");
    String patientName = resultSet.getString("first_name") + " " + resultSet.getString("last_name");
    return new CalendarAppointment(
        resultSet.getLong("id"),
        patientId,
        patientName,
        SqliteQueries.parseTimestamp(resultSet.getString("starts_at")),
        SqliteQueries.parseTimestamp(resultSet.getString("ends_at")),
        AppointmentStatus.valueOf(resultSet.getString(STATUS_COLUMN)));
  }
}
