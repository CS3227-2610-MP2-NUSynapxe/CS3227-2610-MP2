package nusynapxe.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import nusynapxe.domain.Account;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;

/** Provides authorized Doctor Calendar reads and preference operations. */
public final class CalendarService {
  /** Fixed clinic timezone used by Calendar date and current-time calculations. */
  public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Singapore");

  private final AccountRepository accounts;
  private final AppointmentRepository appointments;
  private final CalendarSettingsRepository settings;

  /** Creates a Calendar service over the clinic repositories. */
  public CalendarService(
      AccountRepository accounts,
      AppointmentRepository appointments,
      CalendarSettingsRepository settings) {
    this.accounts = Objects.requireNonNull(accounts, "accounts");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Returns the saved settings or deterministic display defaults for the signed-in Doctor. */
  public DoctorCalendarSettings getSettings(Session actor) throws SQLException {
    Account doctor = requireDoctor(actor);
    return settings
        .findByDoctor(doctor.id())
        .orElseGet(() -> DoctorCalendarSettings.defaults(doctor.id()));
  }

  /** Saves complete settings for the signed-in Doctor after ownership validation. */
  public DoctorCalendarSettings saveSettings(
      Session actor, DoctorCalendarSettings requestedSettings) throws SQLException {
    Account doctor = requireDoctor(actor);
    Objects.requireNonNull(requestedSettings, "requestedSettings");
    if (requestedSettings.doctorId() != doctor.id()) {
      throw new AuthorizationException("You are not allowed to change another doctor's settings");
    }
    return settings.save(requestedSettings);
  }

  /** Returns non-clinical appointments overlapping a selected seven-day period. */
  public DoctorCalendarWeek getWeek(Session actor, LocalDate weekStart) throws SQLException {
    Account doctor = requireDoctor(actor);
    Objects.requireNonNull(weekStart, "weekStart");
    DoctorCalendarSettings calendarSettings = getSettings(actor);
    LocalDateTime rangeStart = weekStart.atStartOfDay();
    LocalDateTime rangeEnd = weekStart.plusDays(7).atStartOfDay();
    java.util.List<CalendarAppointment> calendarAppointments =
        appointments.findCalendarByDoctor(doctor.id(), rangeStart, rangeEnd);
    return new DoctorCalendarWeek(doctor.id(), weekStart, calendarSettings, calendarAppointments);
  }

  /**
   * Returns one bounded page of the signed-in Doctor's appointments from an inclusive clinic date.
   *
   * <p>The schedule deliberately returns the existing administrative calendar projection only;
   * clinical details are not part of this read.
   */
  public CalendarSchedulePage getSchedulePage(
      Session actor, LocalDate anchor, CalendarScheduleCursor cursor, int pageSize)
      throws SQLException {
    Account doctor = requireDoctor(actor);
    Objects.requireNonNull(anchor, "anchor");
    try {
      CalendarSchedulePage.validatePageSize(pageSize);
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(exception.getMessage(), exception);
    }
    return appointments.findCalendarPageByDoctor(
        doctor.id(), anchor.atStartOfDay(), cursor, pageSize);
  }

  private Account requireDoctor(Session actor) throws SQLException {
    Authorization.requireRole(actor, Role.DOCTOR);
    Optional<Account> account = accounts.findById(actor.accountId());
    if (account.isEmpty() || account.orElseThrow().role() != Role.DOCTOR) {
      throw new ValidationException("Doctor does not exist");
    }
    return account.orElseThrow();
  }
}
