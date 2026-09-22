package nusynapxe.ui;

/** Immutable appointment selection captured when a Doctor action is submitted. */
record DoctorAppointmentTarget(long appointmentId, long generation) {
  static DoctorAppointmentTarget capture(long appointmentId, long generation) {
    if (appointmentId <= 0) {
      throw new IllegalArgumentException("An appointment must be selected");
    }
    return new DoctorAppointmentTarget(appointmentId, generation);
  }

  boolean isCurrent(long currentAppointmentId, long currentGeneration) {
    return appointmentId == currentAppointmentId && generation == currentGeneration;
  }
}
