package nusynapxe.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class ReceptionistViewTest extends ApplicationTest {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;
  private ClinicServices services;
  private Account doctor;
  private Account receptionist;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("receptionist-ui.db"));
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
    new ApplicationRouter(stage, database).showInitial();
    stage.show();
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void receptionistBooksChecksInChecksOutAndViewsRevenue() throws SQLException {
    loginAsReceptionist();
    verifyThat("#receptionist-workspace", isVisible());
    verifyThat("#reception-book", isVisible());

    clickOn("#reception-patient-first-name").write("Pat");
    clickOn("#reception-patient-last-name").write("Lee");
    clickOn("#reception-patient-phone").write("555-0100");
    clickOn("#reception-patient-register");
    verifyThat("#reception-feedback", hasText("Patient registered"));

    LocalDateTime start = LocalDateTime.now().minusMinutes(5).withSecond(0).withNano(0);
    LocalDateTime end = start.plusMinutes(30);
    setText("#reception-start", start.format(DATE_TIME_FORMAT));
    setText("#reception-end", end.format(DATE_TIME_FORMAT));
    fire("#reception-book");
    verifyThat("#reception-feedback", hasText("Appointment booked and awaiting Doctor acceptance"));

    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    List<Appointment> bookedAppointments =
        services.appointmentService().allAppointments(receptionistSession);
    assertThat(bookedAppointments, hasSize(1));
    Appointment appointment = bookedAppointments.get(0);
    services.appointmentService().accept(doctorSession, appointment.id());

    fire("#reception-refresh");
    fire("#reception-check-in");
    verifyThat("#reception-feedback", hasText("Patient checked in"));
    services.appointmentService().complete(doctorSession, appointment.id());

    fire("#reception-refresh");
    setText("#reception-charge", "45.00");
    fire("#reception-checkout");
    verifyThat("#reception-feedback", hasText("Checkout completed"));

    setText("#reception-revenue-date", LocalDate.now().toString());
    fire("#reception-revenue-submit");
    verifyThat("#reception-revenue", hasText("1 successful payment(s), total 45.00"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(javafx.scene.control.TextField.class).setText(value));
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  private void loginAsReceptionist() {
    clickOn("#login-username").write("reception");
    clickOn("#login-password").write("reception-pass");
    fire("#login-submit");
    waitForNode("#receptionist-workspace");
  }

  private void waitForNode(String selector) {
    try {
      WaitForAsyncUtils.waitFor(
          60, TimeUnit.SECONDS, () -> lookup(selector).tryQuery().isPresent());
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out waiting for " + selector, exception);
    }
  }
}
