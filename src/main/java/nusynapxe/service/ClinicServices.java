package nusynapxe.service;

import java.util.Objects;
import nusynapxe.persistence.AccountRepository;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.CalendarSettingsRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.PaymentRepository;
import nusynapxe.persistence.SqliteDatabase;

/** Wires the persistence repositories and application services for one database. */
public final class ClinicServices {
  private final AccountService accountOperations;
  private final AuthenticationService authentication;
  private final PatientService patientOperations;
  private final AppointmentService appointmentOperations;
  private final ClinicalService clinicalOperations;
  private final BillingService billingOperations;
  private final CalendarService calendarOperations;

  private ClinicServices(
      AccountService accountService,
      AuthenticationService authenticationService,
      PatientService patientService,
      AppointmentService appointmentService,
      ClinicalService clinicalService,
      BillingService billingService,
      CalendarService calendarService) {
    this.accountOperations = accountService;
    this.authentication = authenticationService;
    this.patientOperations = patientService;
    this.appointmentOperations = appointmentService;
    this.clinicalOperations = clinicalService;
    this.billingOperations = billingService;
    this.calendarOperations = calendarService;
  }

  /**
   * Creates all application services over one opened database.
   *
   * @param database opened database shared by all repositories and services
   * @return fully wired clinic service facade
   * @throws NullPointerException if {@code database} is {@code null}
   */
  public static ClinicServices forDatabase(SqliteDatabase database) {
    Objects.requireNonNull(database, "database");
    AccountRepository accounts = new AccountRepository(database);
    PatientRepository patients = new PatientRepository(database);
    AppointmentRepository appointments = new AppointmentRepository(database);
    ClinicalRecordRepository clinicalRecords = new ClinicalRecordRepository(database);
    AppointmentService appointmentService =
        new AppointmentService(appointments, accounts, patients);
    CalendarService calendarService =
        new CalendarService(accounts, appointments, new CalendarSettingsRepository(database));
    return new ClinicServices(
        new AccountService(accounts),
        new AuthenticationService(accounts),
        new PatientService(patients, appointments, clinicalRecords),
        appointmentService,
        new ClinicalService(appointments, clinicalRecords),
        new BillingService(new PaymentRepository(database), appointmentService),
        calendarService);
  }

  /**
   * Returns account setup and staff-management operations.
   *
   * @return account service
   */
  public AccountService accountService() {
    return accountOperations;
  }

  /**
   * Returns login and logout operations.
   *
   * @return authentication service
   */
  public AuthenticationService authenticationService() {
    return authentication;
  }

  /**
   * Returns patient operations.
   *
   * @return patient service
   */
  public PatientService patientService() {
    return patientOperations;
  }

  /**
   * Returns appointment operations.
   *
   * @return appointment service
   */
  public AppointmentService appointmentService() {
    return appointmentOperations;
  }

  /**
   * Returns clinical-record operations.
   *
   * @return clinical service
   */
  public ClinicalService clinicalService() {
    return clinicalOperations;
  }

  /**
   * Returns billing operations.
   *
   * @return billing service
   */
  public BillingService billingService() {
    return billingOperations;
  }

  /**
   * Returns Doctor Calendar and preference operations.
   *
   * @return Calendar service
   */
  public CalendarService calendarService() {
    return calendarOperations;
  }
}
