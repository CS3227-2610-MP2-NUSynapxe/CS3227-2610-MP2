package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;

/** Atomically applies lifecycle writes against an appointment status snapshot. */
final class AppointmentLifecycleWriter {
  private static final int EXPECTED_UPDATE_COUNT = 1;

  private final SqliteDatabase database;
  private final Clock clock;
  private final AppointmentQueryRepository queries;

  AppointmentLifecycleWriter(SqliteDatabase database, Clock clock) {
    this.database = database;
    this.clock = clock;
    this.queries = new AppointmentQueryRepository(database);
  }

  Optional<Appointment> reschedule(
      long id,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      AppointmentStatus expectedStatus,
      AppointmentStatus status)
      throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          Appointment current = queries.findById(connection, id).orElseThrow(() -> missing(id));
          if (current.status() != expectedStatus) {
            return Optional.empty();
          }
          AppointmentRepository.ensureAvailable(
              connection, current.doctorId(), startsAt, endsAt, id);
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET starts_at = ?, ends_at = ?, status = ?, updated_at = ? "
                      + "WHERE id = ? AND status = ?")) {
            SqliteQueries.bindTimestamp(statement, 1, startsAt);
            SqliteQueries.bindTimestamp(statement, 2, endsAt);
            statement.setString(3, status.name());
            statement.setString(4, SqliteQueries.formatTimestamp(ClinicClock.now(clock)));
            statement.setLong(5, id);
            statement.setString(6, expectedStatus.name());
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              return Optional.empty();
            }
          }
          return Optional.of(
              new Appointment(
                  current.id(), current.patientId(), current.doctorId(), startsAt, endsAt, status));
        });
  }

  Optional<Appointment> updateStatus(
      long id, AppointmentStatus expectedStatus, AppointmentStatus status) throws SQLException {
    return SqliteTransactions.execute(
        database,
        connection -> {
          Appointment current = queries.findById(connection, id).orElseThrow(() -> missing(id));
          if (current.status() != expectedStatus) {
            return Optional.empty();
          }
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET status = ?, updated_at = ? "
                      + "WHERE id = ? AND status = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, SqliteQueries.formatTimestamp(ClinicClock.now(clock)));
            statement.setLong(3, id);
            statement.setString(4, expectedStatus.name());
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              return Optional.empty();
            }
          }
          return Optional.of(
              new Appointment(
                  current.id(),
                  current.patientId(),
                  current.doctorId(),
                  current.startsAt(),
                  current.endsAt(),
                  status));
        });
  }

  private static SQLException missing(long id) {
    return new SQLException("Appointment does not exist: " + id);
  }
}
