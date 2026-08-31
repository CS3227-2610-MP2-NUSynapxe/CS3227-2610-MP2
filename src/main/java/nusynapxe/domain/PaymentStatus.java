package nusynapxe.domain;

/** Outcomes that can be stored for a checkout payment attempt. */
public enum PaymentStatus {
  /** Payment was successfully recorded. */
  SUCCESSFUL,

  /** Payment attempt did not complete successfully. */
  UNSUCCESSFUL
}
