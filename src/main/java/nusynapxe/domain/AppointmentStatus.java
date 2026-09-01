package nusynapxe.domain;

/** States in the appointment lifecycle. */
public enum AppointmentStatus {
  /** Appointment created by a receptionist and awaiting doctor acceptance. */
  PENDING,

  /** Appointment accepted by its assigned doctor. */
  ACCEPTED,

  /** Patient has checked in for the appointment. */
  CHECKED_IN,

  /** Doctor has completed the consultation. */
  COMPLETED,

  /** Receptionist has recorded payment for the appointment. */
  CHECKED_OUT,

  /** Appointment will not take place. */
  CANCELLED
}
