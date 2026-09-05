package nusynapxe.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueReport;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.PaymentRepository;
import nusynapxe.persistence.ReceiptRepository;

/** Applies Receptionist authorization and validation to checkout operations. */
public final class BillingService {
  private final PaymentRepository payments;
  private final AppointmentService appointments;
  private final Clock clock;
  private final ReceiptRepository receipts;

  /**
   * Creates billing service using the system clock.
   *
   * @param payments repository used to persist payments
   * @param appointments service used to validate appointment state
   * @throws NullPointerException if a dependency is {@code null}
   */
  public BillingService(PaymentRepository payments, AppointmentService appointments) {
    this(payments, appointments, Clock.systemDefaultZone());
  }

  BillingService(PaymentRepository payments, AppointmentService appointments, Clock clock) {
    this.payments = Objects.requireNonNull(payments, "payments");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.receipts = new ReceiptRepository(payments.backingDatabase());
  }

  /**
   * Records a successful payment and checks out a completed appointment.
   *
   * @param actor authenticated Receptionist session
   * @param appointmentId completed appointment identifier
   * @param amountMinor payment amount in minor currency units
   * @param method payment method
   * @return the persisted successful payment
   * @throws AuthorizationException if the actor is not a Receptionist
   * @throws SQLException if payment persistence fails
   * @throws ValidationException if the amount, method, or appointment state is invalid
   */
  public Payment checkout(Session actor, long appointmentId, long amountMinor, PaymentMethod method)
      throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (amountMinor <= 0) {
      throw new ValidationException("Payment amount must be positive");
    }
    if (method == null) {
      throw new ValidationException("Payment method is required");
    }
    Appointment appointment = appointments.get(appointmentId);
    if (appointment.status() != AppointmentStatus.COMPLETED) {
      throw new ValidationException("Only completed appointments can be checked out");
    }
    Payment payment =
        new Payment(
            0,
            appointment.id(),
            appointment.patientId(),
            actor.accountId(),
            amountMinor,
            method,
            PaymentStatus.SUCCESSFUL,
            LocalDateTime.now(clock));
    try {
      return payments.createCheckout(payment);
    } catch (SQLException exception) {
      throw new ValidationException("Checkout could not be completed", exception);
    }
  }

  /**
   * Returns successful checkout revenue for a local clinic date.
   *
   * @param actor authenticated Receptionist session
   * @param date Singapore-local clinic date
   * @return revenue totals for the date
   * @throws AuthorizationException if the actor is not a Receptionist
   * @throws SQLException if the revenue query fails
   * @throws ValidationException if {@code date} is {@code null}
   */
  public RevenueSummary dailyRevenue(Session actor, LocalDate date) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (date == null) {
      throw new ValidationException("Revenue date is required");
    }
    return payments.revenueFor(date);
  }

  /**
   * Returns a receipt-backed report for an inclusive local date range.
   *
   * @param actor authenticated Receptionist session
   * @param from inclusive Singapore-local start date
   * @param to inclusive Singapore-local end date
   * @param patientQuery optional patient search text
   * @param doctorId optional doctor identifier
   * @param method optional payment method filter
   * @return immutable receipt-backed revenue report
   * @throws AuthorizationException if the actor is not a Receptionist
   * @throws SQLException if receipt queries fail
   * @throws ValidationException if the dates are missing or the range is reversed
   */
  public RevenueReport revenueReport(
      Session actor,
      LocalDate from,
      LocalDate to,
      String patientQuery,
      Long doctorId,
      PaymentMethod method)
      throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (from == null || to == null) {
      throw new ValidationException("Report dates are required");
    }
    if (to.isBefore(from)) {
      throw new ValidationException("Report end date must not be before its start date");
    }
    List<Receipt> result = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      receipts.findAll(patientQuery, doctorId, date).stream()
          .filter(receipt -> method == null || receipt.method() == method)
          .forEach(result::add);
    }
    return new RevenueReport(result);
  }

  /**
   * Returns Receptionist-visible receipt history.
   *
   * @param actor authenticated Receptionist session
   * @param patientQuery optional patient search text
   * @param doctorId optional doctor identifier
   * @param date optional Singapore-local receipt date
   * @return immutable matching receipt list
   * @throws AuthorizationException if the actor is not a Receptionist
   * @throws SQLException if the receipt query fails
   */
  public java.util.List<Receipt> receiptHistory(
      Session actor, String patientQuery, Long doctorId, LocalDate date) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return receipts.findAll(patientQuery, doctorId, date);
  }

  /**
   * Returns one persisted receipt for reprinting.
   *
   * @param actor authenticated Receptionist session
   * @param receiptId receipt identifier
   * @return the matching receipt
   * @throws AuthorizationException if the actor is not a Receptionist
   * @throws SQLException if the receipt does not exist or the query fails
   */
  public Receipt receipt(Session actor, long receiptId) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return receipts.findById(receiptId);
  }
}
