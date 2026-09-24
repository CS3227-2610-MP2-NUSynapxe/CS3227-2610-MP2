package nusynapxe.tools;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Prescription;
import nusynapxe.persistence.ClinicalRecordRepository;

/** Adds representative consultations and prescriptions to completed demo appointments. */
final class DemoDataClinicalSeeder {
  private static final Set<AppointmentStatus> CONSULTATION_STATUSES =
      Set.of(
          AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED, AppointmentStatus.CHECKED_OUT);

  private DemoDataClinicalSeeder() {
    throw new AssertionError("Utility class");
  }

  static void seed(ClinicalRecordRepository repository, List<Appointment> appointments)
      throws SQLException {
    java.time.LocalDate today = ClinicClock.today(ClinicClock.system());
    for (Appointment appointment : appointments) {
      if (!appointment.startsAt().toLocalDate().isBefore(today)
          || !CONSULTATION_STATUSES.contains(appointment.status())) {
        continue;
      }
      ClinicalRecord record =
          repository.save(
              new ClinicalRecord(
                  0,
                  appointment.patientId(),
                  appointment.id(),
                  appointment.doctorId(),
                  diagnosisFor(appointment.status()),
                  "Seeded consultation notes for dashboard and clinical-history demos.",
                  "Seeded follow-up instructions for the next appointment."));
      if (appointment.status() == AppointmentStatus.COMPLETED
          || appointment.status() == AppointmentStatus.CHECKED_OUT) {
        repository.addPrescription(
            new Prescription(
                0,
                record.id(),
                "DemoCare tablets",
                "1 tablet",
                "Twice daily",
                "5 days",
                "Take after meals."));
      }
    }
  }

  private static String diagnosisFor(AppointmentStatus status) {
    return switch (status) {
      case CHECKED_IN -> "Routine consultation in progress";
      case COMPLETED -> "Routine follow-up completed";
      case CHECKED_OUT -> "Stable condition at checkout";
      default -> throw new IllegalArgumentException("Unsupported clinical status: " + status);
    };
  }
}
