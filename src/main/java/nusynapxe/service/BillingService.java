package nusynapxe.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Receipt;
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

  /** Creates billing service using the system clock. */
  public BillingService(PaymentRepository payments, AppointmentService appointments) {
    this(payments, appointments, Clock.systemDefaultZone());
  }

  BillingService(PaymentRepository payments, AppointmentService appointments, Clock clock) {
    this.payments = Objects.requireNonNull(payments, "payments");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.receipts = new ReceiptRepository(payments.backingDatabase());
  }

  /** Records a successful payment and checks out a completed appointment. */
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

  /** Returns successful checkout revenue for a local clinic date. */
  public RevenueSummary dailyRevenue(Session actor, LocalDate date) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    if (date == null) {
      throw new ValidationException("Revenue date is required");
    }
    return payments.revenueFor(date);
  }

  /** Returns Receptionist-visible receipt history. */
  public java.util.List<Receipt> receiptHistory(
      Session actor, String patientQuery, Long doctorId, LocalDate date) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return receipts.findAll(patientQuery, doctorId, date);
  }

  /** Returns one persisted receipt for reprinting. */
  public Receipt receipt(Session actor, long receiptId) throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return receipts.findById(receiptId);
  }
}
