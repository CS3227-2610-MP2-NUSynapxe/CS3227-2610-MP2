package nusynapxe.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;

/** Persists and retrieves checkout receipts. */
public final class ReceiptRepository {
  private static final String RECEIPT_QUERY =
      "SELECT r.id, r.payment_id, r.appointment_id, r.patient_id, p.first_name || ' ' || p.last_name patient_name, u.display_name doctor_name, r.amount_minor, r.method, r.receipt_date, r.sequence_number, r.recorded_at FROM receipts r JOIN patients p ON p.id = r.patient_id JOIN appointments a ON a.id = r.appointment_id JOIN users u ON u.id = a.doctor_id";
  private static final String FIND_BY_ID_QUERY = RECEIPT_QUERY + " WHERE r.id = ?";
  private static final String FIND_BY_APPOINTMENT_QUERY =
      RECEIPT_QUERY + " WHERE r.appointment_id = ? ORDER BY r.id DESC LIMIT 1";
  private static final String FIND_ALL_QUERY =
      RECEIPT_QUERY
          + " WHERE (? IS NULL OR ? = '' OR CAST(p.id AS TEXT) LIKE lower(?) OR lower(p.first_name || ' ' || p.last_name) LIKE lower(?) OR lower(p.email) LIKE lower(?)) AND (? IS NULL OR a.doctor_id = ?) AND (? IS NULL OR r.receipt_date = ?) ORDER BY r.receipt_date DESC, r.sequence_number DESC";
  private static final String FIND_RANGE_QUERY =
      RECEIPT_QUERY
          + " WHERE r.receipt_date >= ? AND r.receipt_date <= ? AND (? IS NULL OR ? = '' OR CAST(p.id AS TEXT) LIKE lower(?) OR lower(p.first_name || ' ' || p.last_name) LIKE lower(?) OR lower(p.email) LIKE lower(?)) AND (? IS NULL OR a.doctor_id = ?) AND (? IS NULL OR r.method = ?) ORDER BY r.receipt_date DESC, r.sequence_number DESC";
  private final SqliteDatabase database;

  /**
   * Creates a receipt repository backed by an opened database.
   *
   * @param database database used for receipt persistence
   * @throws NullPointerException if {@code database} is {@code null}
   */
  public ReceiptRepository(SqliteDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /**
   * Creates a receipt row inside the caller's transaction.
   *
   * @param connection active transaction connection
   * @param paymentId payment identifier
   * @param appointmentId paid appointment identifier
   * @param patientId patient identifier
   * @param amountMinor amount in minor currency units
   * @param method payment method
   * @param receiptDate Singapore-local receipt date
   * @param recordedAt payment recording timestamp
   * @return the created receipt
   * @throws NullPointerException if a required argument is {@code null}
   * @throws SQLException if the sequence lookup or insert fails
   */
  public Receipt create(
      java.sql.Connection connection,
      long paymentId,
      long appointmentId,
      long patientId,
      long amountMinor,
      PaymentMethod method,
      LocalDate receiptDate,
      java.time.LocalDateTime recordedAt)
      throws SQLException {
    long next;
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM receipts WHERE receipt_date = ?")) {
      statement.setString(1, receiptDate.toString());
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new SQLException("SQLite did not return the receipt sequence");
        }
        next = result.getLong(1);
      }
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO receipts(payment_id, appointment_id, patient_id, amount_minor, method, receipt_date, sequence_number, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS)) {
      statement.setLong(1, paymentId);
      statement.setLong(2, appointmentId);
      statement.setLong(3, patientId);
      statement.setLong(4, amountMinor);
      statement.setString(5, method.name());
      statement.setString(6, receiptDate.toString());
      statement.setLong(7, next);
      statement.setString(8, SqliteQueries.formatTimestamp(recordedAt));
      statement.executeUpdate();
      try (ResultSet keys = statement.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("SQLite did not return the receipt identifier");
        }
        return new Receipt(
            keys.getLong(1),
            paymentId,
            appointmentId,
            patientId,
            "",
            "",
            amountMinor,
            method,
            receiptDate,
            next,
            recordedAt);
      }
    }
  }

  /**
   * Finds one receipt by identifier.
   *
   * @param id receipt identifier
   * @return the matching receipt
   * @throws SQLException if the receipt does not exist or the query fails
   */
  public Receipt findById(long id) throws SQLException {
    try (PreparedStatement statement = database.connection().prepareStatement(FIND_BY_ID_QUERY)) {
      statement.setLong(1, id);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new SQLException("Receipt does not exist");
        }
        return read(result);
      }
    }
  }

  /**
   * Finds the receipt associated with one appointment.
   *
   * @param appointmentId appointment identifier
   * @return the matching receipt
   * @throws SQLException if the receipt does not exist or the query fails
   */
  public Receipt findByAppointment(long appointmentId) throws SQLException {
    try (PreparedStatement statement =
        database.connection().prepareStatement(FIND_BY_APPOINTMENT_QUERY)) {
      statement.setLong(1, appointmentId);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new SQLException("Receipt does not exist");
        }
        return read(result);
      }
    }
  }

  /**
   * Finds receipts using optional patient, Doctor, and date filters.
   *
   * @param patientQuery optional patient search text
   * @param doctorId optional Doctor identifier
   * @param date optional Singapore-local receipt date
   * @return matching receipts in reverse receipt-date and sequence order
   * @throws SQLException if the query fails
   */
  public List<Receipt> findAll(String patientQuery, Long doctorId, LocalDate date)
      throws SQLException {
    try (PreparedStatement statement = database.connection().prepareStatement(FIND_ALL_QUERY)) {
      String query = patientQuery == null ? "" : patientQuery.trim();
      String pattern = "%" + query + "%";
      statement.setString(1, patientQuery == null ? null : query);
      statement.setString(2, query);
      statement.setString(3, pattern);
      statement.setString(4, pattern);
      statement.setString(5, pattern);
      if (doctorId == null) {
        statement.setObject(6, null);
        statement.setObject(7, null);
      } else {
        statement.setLong(6, doctorId);
        statement.setLong(7, doctorId);
      }
      statement.setString(8, date == null ? null : date.toString());
      statement.setString(9, date == null ? null : date.toString());
      try (ResultSet result = statement.executeQuery()) {
        List<Receipt> receipts = new ArrayList<>();
        while (result.next()) {
          receipts.add(read(result));
        }
        return receipts;
      }
    }
  }

  /**
   * Finds receipts in one inclusive date-range query with optional filters.
   *
   * @param patientQuery optional patient search text
   * @param doctorId optional Doctor identifier
   * @param from inclusive Singapore-local receipt date
   * @param to inclusive Singapore-local receipt date
   * @param method optional payment method
   * @return matching receipts in reverse receipt-date and sequence order
   * @throws NullPointerException if a date is {@code null}
   * @throws SQLException if the query fails
   */
  public List<Receipt> findRange(
      String patientQuery, Long doctorId, LocalDate from, LocalDate to, PaymentMethod method)
      throws SQLException {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Receipt range is reversed");
    }
    try (PreparedStatement statement = database.connection().prepareStatement(FIND_RANGE_QUERY)) {
      String query = patientQuery == null ? "" : patientQuery.trim();
      String pattern = "%" + query + "%";
      statement.setString(1, from.toString());
      statement.setString(2, to.toString());
      statement.setString(3, patientQuery == null ? null : query);
      statement.setString(4, query);
      statement.setString(5, pattern);
      statement.setString(6, pattern);
      statement.setString(7, pattern);
      if (doctorId == null) {
        statement.setObject(8, null);
        statement.setObject(9, null);
      } else {
        statement.setLong(8, doctorId);
        statement.setLong(9, doctorId);
      }
      if (method == null) {
        statement.setObject(10, null);
        statement.setObject(11, null);
      } else {
        statement.setString(10, method.name());
        statement.setString(11, method.name());
      }
      try (ResultSet result = statement.executeQuery()) {
        List<Receipt> receipts = new ArrayList<>();
        while (result.next()) {
          receipts.add(read(result));
        }
        return receipts;
      }
    }
  }

  private static Receipt read(ResultSet result) throws SQLException {
    return new Receipt(
        result.getLong("id"),
        result.getLong("payment_id"),
        result.getLong("appointment_id"),
        result.getLong("patient_id"),
        result.getString("patient_name"),
        result.getString("doctor_name"),
        result.getLong("amount_minor"),
        PaymentMethod.valueOf(result.getString("method")),
        LocalDate.parse(result.getString("receipt_date")),
        result.getLong("sequence_number"),
        SqliteQueries.parseTimestamp(result.getString("recorded_at")));
  }
}
