package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Payment;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.PaymentStatus;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.ClinicalRecordRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.PaymentRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

/** Shared TestFX fixture and actions for doctor workspace behavior tests. */
abstract class DoctorViewTestSupport extends ApplicationTest {
  protected static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-09-20T14:50:00Z"), ZoneId.of("Asia/Singapore"));

  @TempDir protected Path temporaryDirectory;
  protected SqliteDatabase database;
  protected ClinicServices services;
  protected Account doctor;
  protected Account receptionist;
  protected LocalDate appointmentDate;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("doctor-ui.db"));
    database.open();
    services = ClinicServices.forDatabase(database);
    Account admin =
        services.accountService().createInitialAdmin("admin", "Admin", "secure-pass".toCharArray());
    Session adminSession = new Session(admin.id(), admin.username(), Role.SYSTEM_ADMIN);
    doctor =
        services
            .accountService()
            .createStaff(
                adminSession, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray());
    receptionist =
        services
            .accountService()
            .createStaff(
                adminSession,
                "reception",
                "Reception",
                Role.RECEPTIONIST,
                "reception-pass".toCharArray());
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "", "555-0100", "", ""));
    Patient geometryPatient =
        new PatientRepository(database)
            .create(new Patient(0, "Alex", "Tan", "", "555-0101", "", ""));
    LocalDateTime start = LocalDateTime.now(TEST_CLOCK).minusMinutes(10).withSecond(0).withNano(0);
    AppointmentRepository appointments = new AppointmentRepository(database);
    appointments.create(
        patient.id(), doctor.id(), start, start.plusMinutes(30), AppointmentStatus.CHECKED_IN);
    LocalDateTime oneHourStart = start.getHour() < 21 ? start.plusHours(2) : start.minusHours(2);
    appointments.create(
        geometryPatient.id(),
        doctor.id(),
        oneHourStart,
        oneHourStart.plusHours(1),
        AppointmentStatus.ACCEPTED);
    appointmentDate = start.toLocalDate();
    new ApplicationRouter(stage, database, TEST_CLOCK).showInitial();
    stage.show();
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    if (database != null) {
      database.close();
    }
  }

  protected void selectDashboardAppointment(long id) {
    interact(
        () ->
            lookup("#doctor-calendar-appointment-" + id + "-" + appointmentDate)
                .query()
                .getOnMouseClicked()
                .handle(primaryClick()));
    WaitForAsyncUtils.waitForFxEvents();
  }

  protected Appointment createAvailableAppointment(String firstName, String lastName)
      throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, firstName, lastName, "", "555-0200", "", ""));
    AppointmentRepository repository = new AppointmentRepository(database);
    List<Appointment> existing = repository.findByDoctor(doctor.id());
    for (int hour = 0; hour < 24; hour++) {
      LocalDateTime start = appointmentDate.atTime(hour, 0);
      LocalDateTime end = start.plusMinutes(30);
      boolean overlaps =
          existing.stream()
              .anyMatch(
                  appointment ->
                      appointment.startsAt().isBefore(end) && appointment.endsAt().isAfter(start));
      if (!overlaps) {
        return repository.create(patient.id(), doctor.id(), start, end, AppointmentStatus.PENDING);
      }
    }
    throw new AssertionError("No available appointment slot in the test date");
  }

  protected static MouseEvent primaryClick() {
    return new MouseEvent(
        MouseEvent.MOUSE_CLICKED,
        5,
        5,
        5,
        5,
        MouseButton.PRIMARY,
        1,
        false,
        false,
        false,
        false,
        true,
        false,
        false,
        false,
        false,
        false,
        null);
  }

  protected void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  @SuppressWarnings("unchecked")
  protected <T> void selectCombo(String selector, T value) {
    interact(() -> lookup(selector).queryAs(ComboBox.class).setValue(value));
  }

  @SuppressWarnings("unchecked")
  protected void setDate(String selector, LocalDate value) {
    interact(
        () -> {
          lookup(selector + "-day").queryAs(ComboBox.class).setValue(value.getDayOfMonth());
          lookup(selector + "-month").queryAs(ComboBox.class).setValue(value.getMonth());
          lookup(selector + "-year").queryAs(ComboBox.class).setValue(value.getYear());
        });
  }

  @SuppressWarnings("unchecked")
  protected SearchSuggestionField<Patient> historySelector() {
    return (SearchSuggestionField<Patient>) lookup("#doctor-history-patient-field").query();
  }

  @SuppressWarnings("unchecked")
  protected TableView<Patient> patientTable() {
    return lookup("#doctor-patient-table").queryAs(TableView.class);
  }

  protected void preparePatientTable() {
    interact(
        () -> {
          TableView<Patient> table = patientTable();
          table.scrollTo(0);
          table.applyCss();
          table.layout();
        });
    WaitForAsyncUtils.waitForFxEvents();
  }

  protected String tableValue(int columnIndex, Patient patient) {
    return (String)
        patientTable().getColumns().get(columnIndex).getCellObservableValue(patient).getValue();
  }

  protected void assertCompactPatientSelectors(String formPrefix) {
    assertTrue(
        lookup("#" + formPrefix + "-identity-type")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-issuing-country")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-date-of-birth-day")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-date-of-birth-month")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-date-of-birth-year")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
    assertTrue(
        lookup("#" + formPrefix + "-sex")
            .queryAs(ComboBox.class)
            .getStyleClass()
            .contains("compact-selector"));
  }

  protected void loginAsDoctor() {
    setText("#login-username", "doctor");
    setText("#login-password", "doctor-pass");
    fire("#login-submit");
    waitForNode("#doctor-workspace");
  }

  protected Session doctorSession() {
    return new Session(doctor.id(), doctor.username(), Role.DOCTOR);
  }

  protected void addPatientHistory() throws SQLException {
    ClinicalRecord clinicalRecord =
        new ClinicalRecordRepository(database)
            .save(
                new ClinicalRecord(
                    0,
                    1,
                    1,
                    doctor.id(),
                    "Existing diagnosis",
                    "Existing notes",
                    "Existing follow-up"));
    new ClinicalRecordRepository(database)
        .addPrescription(
            new Prescription(
                0,
                clinicalRecord.id(),
                "Existing medicine",
                "10 mg",
                "Daily",
                "7 days",
                "Take with food"));
    services.appointmentService().complete(doctorSession(), 1);
    new PaymentRepository(database)
        .createCheckout(
            new Payment(
                0,
                1,
                1,
                receptionist.id(),
                2500,
                PaymentMethod.CARD,
                PaymentStatus.SUCCESSFUL,
                LocalDateTime.of(2026, 9, 2, 10, 0)));
  }

  protected static void join(Thread thread) {
    try {
      thread.join(60_000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for patient dialog", exception);
    }
    assertFalse(thread.isAlive(), "Patient dialog action did not finish");
  }

  protected void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  protected void waitForNode(String selector) {
    try {
      WaitForAsyncUtils.waitFor(
          60, TimeUnit.SECONDS, () -> lookup(selector).tryQuery().isPresent());
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for " + selector, exception);
    }
  }
}
