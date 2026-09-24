package nusynapxe.tools;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.Patient;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;
import nusynapxe.persistence.SqliteDatabase;

/** Creates calendar settings and the rolling appointment schedule for the demo dataset. */
final class DemoDataScheduleSeeder {
  private static final int HISTORICAL_DAYS = 7;
  private static final int FUTURE_DAYS = 14;
  private static final int APPOINTMENTS_PER_DOCTOR_PER_DAY = 2;
  private static final List<AppointmentStatus> HISTORICAL_STATUSES =
      List.of(
          AppointmentStatus.COMPLETED,
          AppointmentStatus.CHECKED_IN,
          AppointmentStatus.CHECKED_OUT,
          AppointmentStatus.ACCEPTED,
          AppointmentStatus.DECLINED,
          AppointmentStatus.CANCELLED,
          AppointmentStatus.PENDING);
  private static final List<AppointmentStatus> FUTURE_STATUSES =
      List.of(
          AppointmentStatus.PENDING,
          AppointmentStatus.ACCEPTED,
          AppointmentStatus.DECLINED,
          AppointmentStatus.CANCELLED);
  private static final List<LocalTime> ADA_APPOINTMENT_TIMES =
      List.of(LocalTime.of(9, 0), LocalTime.of(14, 0));
  private static final List<LocalTime> GRACE_APPOINTMENT_TIMES =
      List.of(LocalTime.of(10, 0), LocalTime.of(15, 0));

  private DemoDataScheduleSeeder() {
    throw new AssertionError("Utility class");
  }

  static void saveCalendarSettings(SqliteDatabase database, Account ada, Account grace)
      throws SQLException {
    CalendarSettingsRepository settings = new CalendarSettingsRepository(database);
    settings.save(splitWeekSettings(ada.id(), DayOfWeek.MONDAY));
    settings.save(singleShiftSettings(grace.id(), DayOfWeek.SUNDAY));
  }

  static List<Appointment> seedAppointments(
      AppointmentRepository repository, Account ada, Account grace, List<Patient> patients)
      throws SQLException {
    LocalDate today = ClinicClock.today(ClinicClock.system());
    List<Patient> schedulablePatients = patients.stream().filter(Patient::active).toList();
    List<Appointment> created = new ArrayList<>();
    for (int dayOffset = -HISTORICAL_DAYS; dayOffset <= FUTURE_DAYS; dayOffset++) {
      LocalDate date = today.plusDays(dayOffset);
      int dayIndex = dayOffset + HISTORICAL_DAYS;
      for (int slot = 0; slot < APPOINTMENTS_PER_DOCTOR_PER_DAY; slot++) {
        int patientIndex = Math.floorMod(dayIndex * 4 + slot, schedulablePatients.size());
        created.add(
            createAppointment(
                repository,
                ada,
                schedulablePatients.get(patientIndex),
                date.atTime(ADA_APPOINTMENT_TIMES.get(slot)),
                appointmentStatus(dayIndex, 0, slot)));
        created.add(
            createAppointment(
                repository,
                grace,
                schedulablePatients.get((patientIndex + 2) % schedulablePatients.size()),
                date.atTime(GRACE_APPOINTMENT_TIMES.get(slot)),
                appointmentStatus(dayIndex, 1, slot)));
      }
    }
    return List.copyOf(created);
  }

  private static DoctorCalendarSettings splitWeekSettings(long doctorId, DayOfWeek firstDay) {
    Map<DayOfWeek, List<WorkingInterval>> intervals = emptyIntervals();
    for (DayOfWeek day : weekdays()) {
      intervals.put(
          day,
          List.of(new WorkingInterval(8 * 60, 12 * 60), new WorkingInterval(13 * 60, 18 * 60)));
    }
    return new DoctorCalendarSettings(doctorId, firstDay, intervals);
  }

  private static DoctorCalendarSettings singleShiftSettings(long doctorId, DayOfWeek firstDay) {
    Map<DayOfWeek, List<WorkingInterval>> intervals = emptyIntervals();
    for (DayOfWeek day : weekdays()) {
      intervals.put(day, List.of(new WorkingInterval(9 * 60, 17 * 60)));
    }
    return new DoctorCalendarSettings(doctorId, firstDay, intervals);
  }

  private static Map<DayOfWeek, List<WorkingInterval>> emptyIntervals() {
    Map<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
    for (DayOfWeek day : DayOfWeek.values()) {
      intervals.put(day, List.of());
    }
    return intervals;
  }

  private static List<DayOfWeek> weekdays() {
    return List.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY);
  }

  private static AppointmentStatus appointmentStatus(int dayIndex, int doctorIndex, int slot) {
    List<AppointmentStatus> statuses =
        dayIndex < HISTORICAL_DAYS ? HISTORICAL_STATUSES : FUTURE_STATUSES;
    return statuses.get(Math.floorMod(dayIndex * 4 + doctorIndex * 2 + slot, statuses.size()));
  }

  private static Appointment createAppointment(
      AppointmentRepository repository,
      Account doctor,
      Patient patient,
      java.time.LocalDateTime startsAt,
      AppointmentStatus status)
      throws SQLException {
    return repository.create(patient.id(), doctor.id(), startsAt, startsAt.plusMinutes(30), status);
  }
}
