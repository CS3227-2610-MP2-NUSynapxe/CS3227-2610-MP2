package nusynapxe.ui;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.persistence.SqliteDatabase;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

final class DoctorViewTest extends ApplicationTest {
  @TempDir private Path temporaryDirectory;
  private SqliteDatabase database;
  private ClinicServices services;

  @Override
  public void start(Stage stage) throws SQLException {
    database = new SqliteDatabase(temporaryDirectory.resolve("doctor-ui.db"));
    database.open();
    services = ClinicServices.forDatabase(database);
    Account admin =
        services.accountService().createInitialAdmin("admin", "Admin", "secure-pass".toCharArray());
    Session adminSession = new Session(admin.id(), admin.username(), Role.SYSTEM_ADMIN);
    Account doctor =
        services
            .accountService()
            .createStaff(
                adminSession, "doctor", "Dr. Ada", Role.DOCTOR, "doctor-pass".toCharArray());
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "", "555-0100", "", "", ""));
    LocalDateTime start = LocalDateTime.now().minusMinutes(10).withSecond(0).withNano(0);
    new AppointmentRepository(database)
        .create(
            patient.id(), doctor.id(), start, start.plusMinutes(30), AppointmentStatus.CHECKED_IN);
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
  void doctorOpensAssignedConsultationAddsPrescriptionAndCompletesVisit() {
    setText("#login-username", "doctor");
    setText("#login-password", "doctor-pass");
    fire("#login-submit");
    waitForNode("#doctor-workspace");

    verifyThat("#doctor-workspace", isVisible());
    verifyThat("#doctor-accept", isVisible());
    verifyThat("#doctor-timeoff-submit", isVisible());
    selectFirst("#doctor-appointment-list");

    setText("#doctor-diagnosis", "Seasonal allergies");
    setText("#doctor-consultation-notes", "Discussed symptoms and treatment options");
    setText("#doctor-follow-up", "Review in two weeks");
    fire("#doctor-consultation-save");
    verifyThat("#doctor-feedback", hasText("Consultation saved"));

    setText("#doctor-medication", "Cetirizine");
    setText("#doctor-dosage", "10 mg");
    setText("#doctor-frequency", "Once daily");
    setText("#doctor-duration", "14 days");
    setText("#doctor-instructions", "Take in the evening");
    fire("#doctor-prescription-submit");
    verifyThat("#doctor-feedback", hasText("Prescription added"));

    fire("#doctor-complete");
    verifyThat("#doctor-feedback", hasText("Appointment marked completed"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  private void selectFirst(String selector) {
    interact(() -> lookup(selector).queryAs(ListView.class).getSelectionModel().selectFirst());
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
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
