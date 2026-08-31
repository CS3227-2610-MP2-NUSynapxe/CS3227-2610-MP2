package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.RevenueSummary;

/** Persists checkout payments and calculates successful daily revenue. */
public final class PaymentRepository {
  private static final int EXPECTED_UPDATE_COUNT = 1;
  private static final String PAYMENT_COLUMNS =
      "id, appointment_id, patient_id, receptionist_id, amount_minor, method, status, recorded_at";
  private final SqliteDatabase database;

  /** Creates a payment repository backed by an opened database. */
  public PaymentRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /** Records a payment attempt and returns its assigned identifier. */
  public Payment create(Payment payment) throws SQLException {
    Objects.requireNonNull(payment, "payment");
    return SqliteTransactions.execute(database, connection -> insert(connection, payment));
  }

  /** Records a successful checkout and marks its completed appointment atomically. */
  public Payment createCheckout(Payment payment) throws SQLException {
    Objects.requireNonNull(payment, "payment");
    return SqliteTransactions.execute(
        database,
        connection -> {
          Payment stored = insert(connection, payment);
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE appointments SET status = 'CHECKED_OUT', updated_at = ? "
                      + "WHERE id = ? AND status = 'COMPLETED'")) {
            statement.setString(1, SqliteQueries.formatTimestamp(LocalDateTime.now()));
            statement.setLong(2, payment.appointmentId());
            if (statement.executeUpdate() != EXPECTED_UPDATE_COUNT) {
              throw new SQLException("Appointment is not ready for checkout");
            }
          }
          return stored;
        });
  }

  /** Finds a payment associated with an appointment. */
  public Optional<Payment> findByAppointment(long appointmentId) throws SQLException {
    try (PreparedStatement statement =
        database
            .connection()
            .prepareStatement(
                "SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE appointment_id = ?")) {
      statement.setLong(1, appointmentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readPayment(resultSet)) : Optional.empty();
      }
    }
  }

  /** Calculates successful payment count and total for a local clinic date. */
  public RevenueSummary revenueFor(LocalDate date) throws SQLException {
    Objects.requireNonNull(date, "date");
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    String sql =
        "SELECT COUNT(*), COALESCE(SUM(amount_minor), 0) FROM payments "
            + "WHERE status = 'SUCCESSFUL' AND recorded_at >= ? AND recorded_at < ?";
    try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
      SqliteQueries.bindTimestamp(statement, 1, start);
      SqliteQueries.bindTimestamp(statement, 2, end);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("SQLite did not return a revenue summary");
        }
        return new RevenueSummary(date, resultSet.getLong(1), resultSet.getLong(2));
      }
    }
  }

  private static Payment readPayment(ResultSet resultSet) throws SQLException {
    return new Payment(
        resultSet.getLong("id"),
        resultSet.getLong("appointment_id"),
        resultSet.getLong("patient_id"),
        resultSet.getLong("receptionist_id"),
        resultSet.getLong("amount_minor"),
        PaymentMethod.valueOf(resultSet.getString("method")),
        PaymentStatus.valueOf(resultSet.getString("status")),
        SqliteQueries.parseTimestamp(resultSet.getString("recorded_at")));
  }

  private static Payment insert(java.sql.Connection connection, Payment payment)
      throws SQLException {
    String sql =
        "INSERT INTO payments(appointment_id, patient_id, receptionist_id, amount_minor, "
            + "method, status, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement statement =
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setLong(1, payment.appointmentId());
      statement.setLong(2, payment.patientId());
      statement.setLong(3, payment.receptionistId());
      statement.setLong(4, payment.amountMinor());
      statement.setString(5, payment.method().name());
      statement.setString(6, payment.status().name());
      statement.setString(7, SqliteQueries.formatTimestamp(payment.recordedAt()));
      statement.executeUpdate();
      try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
        if (!generatedKeys.next()) {
          throw new SQLException("SQLite did not return the payment identifier");
        }
        return withId(payment, generatedKeys.getLong(1));
      }
    }
  }

  private static Payment withId(Payment payment, long id) {
    return new Payment(
        id,
        payment.appointmentId(),
        payment.patientId(),
        payment.receptionistId(),
        payment.amountMinor(),
        payment.method(),
        payment.status(),
        payment.recordedAt());
  }
}
