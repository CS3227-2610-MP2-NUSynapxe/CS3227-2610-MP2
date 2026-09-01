package nusynapxe.domain;

/** Payment methods supported by the local checkout workflow. */
public enum PaymentMethod {
  /** Cash payment. */
  CASH,

  /** Card payment. */
  CARD,

  /** Bank or electronic transfer. */
  TRANSFER,

  /** Another method recorded by the receptionist. */
  OTHER
}
