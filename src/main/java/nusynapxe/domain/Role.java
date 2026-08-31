package nusynapxe.domain;

/** Roles that can access the clinic application. */
public enum Role {
  /** A clinician who manages assigned appointments and clinical records. */
  DOCTOR,

  /** A staff member who manages scheduling, administration, and checkout. */
  RECEPTIONIST,

  /** A staff member who creates clinic staff accounts. */
  SYSTEM_ADMIN
}
