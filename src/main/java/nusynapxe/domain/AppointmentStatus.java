package nusynapxe.domain;

/** States in the appointment lifecycle. */
public enum AppointmentStatus {
  /** Appointment created by a receptionist and awaiting doctor acceptance. */
  PENDING,

  /** Appointment accepted by its assigned doctor. */
  ACCEPTED,

  /** Appointment declined by its assigned doctor and awaiting receptionist coordination. */
  DECLINED,

  /** Patient has checked in for the appointment. */
  CHECKED_IN,

  /** Doctor has completed the consultation. */
  COMPLETED,

  /** Receptionist has recorded payment for the appointment. */
  CHECKED_OUT,

  /** Appointment will not take place. */
  CANCELLED
}
