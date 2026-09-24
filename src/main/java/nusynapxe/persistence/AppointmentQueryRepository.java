package nusynapxe.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.DoctorTimeOff;

/** Reads appointment, schedule, and Doctor time-off projections from SQLite. */
final class AppointmentQueryRepository {
  private static final String SELECT_PREFIX = "SELECT ";
  private static final String STATUS_COLUMN = "status";
  private static final String APPOINTMENT_COLUMNS =
      "id, patient_id, doctor_id, starts_at, ends_at, " + STATUS_COLUMN;
  private static final String TIME_OFF_COLUMNS = "id, doctor_id, starts_at, ends_at";
  private static final String APPOINTMENT_SEARCH_SQL =
      "SELECT a.id, a.patient_id, a.doctor_id, a.starts_at, a.ends_at, a.status "
          + "FROM appointments a JOIN patients p ON p.id = a.patient_id "
          + "WHERE (? IS NULL OR a.starts_at LIKE ?) "
          + "AND (? IS NULL OR a.doctor_id = ?) "
          + "AND (? IS NULL OR a.status = ?) "
          + "AND (? IS NULL OR (CAST(p.id AS TEXT) LIKE ? "
          + "OR LOWER(p.first_name) LIKE ? OR LOWER(p.last_name) LIKE ? "
          + "OR LOWER(p.email) LIKE ?)) "
          + "ORDER BY a.starts_at, a.id";
  private static final String APPOINTMENT_LIST_ROW_SEARCH_SQL =
      "SELECT a.id, a.patient_id, a.doctor_id, a.starts_at, a.ends_at, a.status, "
          + "p.first_name || ' ' || p.last_name AS patient_display_name, "
          + "u.display_name AS doctor_display_name "
          + "FROM appointments a "
          + "JOIN patients p ON p.id = a.patient_id "
          + "JOIN users u ON u.id = a.doctor_id "
          + "WHERE (? IS NULL OR a.starts_at LIKE ?) "
          + "AND (? IS NULL OR a.doctor_id = ?) "
          + "AND (? IS NULL OR a.status = ?) "
          + "AND (? IS NULL OR (CAST(p.id AS TEXT) LIKE ? "
          + "OR LOWER(p.first_name) LIKE ? OR LOWER(p.last_name) LIKE ? "
          + "OR LOWER(p.email) LIKE ?)) "
          + "ORDER BY a.starts_at, a.id";
  private final SqliteDatabase database;

  AppointmentQueryRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  Optional<Appointment> findById(long id) throws SQLException {
    return findById(database.connection(), id);
  }

  Optional<Appointment> findById(Connection connection, long id) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            SELECT_PREFIX + APPOINTMENT_COLUMNS + " FROM appointments WHERE id = ?")) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readAppointment(resultSet)) : Optional.empty();
      }
    }
  }

  List<Appointment> findByDoctor(long doctorId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                SELECT_PREFIX
                    + APPOINTMENT_COLUMNS
                    + " FROM appointments WHERE doctor_id = ? ORDER BY starts_at")) {
      statement.setLong(1, doctorId);
      return SqliteQueries.readAll(statement, AppointmentQueryRepository::readAppointment);
    }
  }

  List<CalendarAppointment> findCalendarByDoctor(
      long doctorId, LocalDateTime rangeStart, LocalDateTime rangeEnd) throws SQLException {
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
      return SqliteQueries.readAll(statement, AppointmentQueryRepository::readCalendarAppointment);
    }
  }

  CalendarSchedulePage findCalendarPageByDoctor(
      long doctorId, LocalDateTime anchor, CalendarScheduleCursor cursor, int pageSize)
      throws SQLException {
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
          SqliteQueries.readAll(statement, AppointmentQueryRepository::readCalendarAppointment);
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

  List<Appointment> search(
      LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement(APPOINTMENT_SEARCH_SQL)) {
      bindSearchParameters(statement, date, doctorId, patientQuery, status);
      return SqliteQueries.readAll(statement, AppointmentQueryRepository::readAppointment);
    }
  }

  List<AppointmentListRow> searchListRows(
      LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement(APPOINTMENT_LIST_ROW_SEARCH_SQL)) {
      bindSearchParameters(statement, date, doctorId, patientQuery, status);
      return SqliteQueries.readAll(statement, AppointmentQueryRepository::readAppointmentListRow);
    }
  }

  List<DoctorTimeOff> findTimeOffByDoctor(long doctorId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                SELECT_PREFIX
                    + TIME_OFF_COLUMNS
                    + " FROM doctor_time_off WHERE doctor_id = ? ORDER BY starts_at")) {
      statement.setLong(1, doctorId);
      return SqliteQueries.readAll(statement, AppointmentQueryRepository::readTimeOff);
    }
  }

  List<DoctorTimeOff> findTimeOffByDoctor(
      long doctorId, LocalDateTime rangeStart, LocalDateTime rangeEnd) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                SELECT_PREFIX
                    + TIME_OFF_COLUMNS
                    + " FROM doctor_time_off WHERE doctor_id = ? "
                    + "AND starts_at < ? AND ends_at > ? ORDER BY starts_at, id")) {
      statement.setLong(1, doctorId);
      SqliteQueries.bindTimestamp(statement, 2, rangeEnd);
      SqliteQueries.bindTimestamp(statement, 3, rangeStart);
      return SqliteQueries.readAll(statement, AppointmentQueryRepository::readTimeOff);
    }
  }

  private static void bindSearchParameters(
      PreparedStatement statement,
      LocalDate date,
      Long doctorId,
      String patientQuery,
      AppointmentStatus status)
      throws SQLException {
    String datePattern = date == null ? null : date + "%";
    bindNullableString(statement, 1, datePattern);
    bindNullableString(statement, 2, datePattern);
    bindNullableLong(statement, 3, doctorId);
    bindNullableLong(statement, 4, doctorId);
    String statusValue = status == null ? null : status.name();
    bindNullableString(statement, 5, statusValue);
    bindNullableString(statement, 6, statusValue);

    String normalizedPatientQuery = patientQuery == null ? "" : patientQuery.trim();
    String patientPattern =
        normalizedPatientQuery.isEmpty()
            ? null
            : "%" + normalizedPatientQuery.toLowerCase(Locale.ROOT) + "%";
    for (int parameter = 7; parameter <= 11; parameter++) {
      bindNullableString(statement, parameter, patientPattern);
    }
  }

  private static void bindNullableString(PreparedStatement statement, int parameter, String value)
      throws SQLException {
    if (value == null) {
      statement.setNull(parameter, Types.VARCHAR);
    } else {
      statement.setString(parameter, value);
    }
  }

  private static void bindNullableLong(PreparedStatement statement, int parameter, Long value)
      throws SQLException {
    if (value == null) {
      statement.setNull(parameter, Types.BIGINT);
    } else {
      statement.setLong(parameter, value);
    }
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

  private static AppointmentListRow readAppointmentListRow(ResultSet resultSet)
      throws SQLException {
    return new AppointmentListRow(
        readAppointment(resultSet),
        resultSet.getString("patient_display_name"),
        resultSet.getString("doctor_display_name"));
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
