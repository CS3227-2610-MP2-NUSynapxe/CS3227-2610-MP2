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

  /**
   * Creates a Calendar service over the clinic repositories.
   *
   * @param accounts repository used to validate the signed-in Doctor
   * @param appointments repository used for administrative appointment projections
   * @param settings repository used for Doctor-owned Calendar preferences
   * @throws NullPointerException if a repository is {@code null}
   */
  public CalendarService(
      AccountRepository accounts,
      AppointmentRepository appointments,
      CalendarSettingsRepository settings) {
    this.accounts = Objects.requireNonNull(accounts, "accounts");
    this.appointments = Objects.requireNonNull(appointments, "appointments");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /**
   * Returns the saved settings or deterministic display defaults for the signed-in Doctor.
   *
   * @param actor authenticated Doctor session
   * @return the Doctor's saved settings or default profile
   * @throws AuthorizationException if the actor is not a valid Doctor session
   * @throws SQLException if the account or settings query fails
   */
  public DoctorCalendarSettings getSettings(Session actor) throws SQLException {
    Account doctor = requireDoctor(actor);
    return settings
        .findByDoctor(doctor.id())
        .orElseGet(() -> DoctorCalendarSettings.defaults(doctor.id()));
  }

  /**
   * Saves complete settings for the signed-in Doctor after ownership validation.
   *
   * @param actor authenticated Doctor session
   * @param requestedSettings complete settings to save
   * @return the persisted settings
   * @throws AuthorizationException if the actor does not own the settings
   * @throws NullPointerException if {@code requestedSettings} is {@code null}
   * @throws SQLException if the settings cannot be saved
   */
  public DoctorCalendarSettings saveSettings(
      Session actor, DoctorCalendarSettings requestedSettings) throws SQLException {
    Account doctor = requireDoctor(actor);
    Objects.requireNonNull(requestedSettings, "requestedSettings");
    if (requestedSettings.doctorId() != doctor.id()) {
      throw new AuthorizationException("You are not allowed to change another doctor's settings");
    }
    return settings.save(requestedSettings);
  }

  /**
   * Returns non-clinical appointments overlapping a selected seven-day period.
   *
   * @param actor authenticated Doctor session
   * @param weekStart first date of the selected seven-day period
   * @return authorized administrative week projection
   * @throws AuthorizationException if the actor is not a valid Doctor session
   * @throws NullPointerException if {@code weekStart} is {@code null}
   * @throws SQLException if the account, settings, or appointment query fails
   */
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
   *
   * @param actor authenticated Doctor session
   * @param anchor inclusive Singapore-local date from which to load appointments
   * @param cursor optional keyset cursor from the previous page
   * @param pageSize bounded number of appointments to request
   * @return authorized schedule page
   * @throws AuthorizationException if the actor is not a valid Doctor session
   * @throws IllegalArgumentException if {@code pageSize} is outside the supported range
   * @throws NullPointerException if {@code anchor} is {@code null}
   * @throws SQLException if the account or appointment query fails
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
