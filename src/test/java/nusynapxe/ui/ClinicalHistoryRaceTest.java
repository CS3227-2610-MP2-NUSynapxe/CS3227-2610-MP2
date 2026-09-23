package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.ClinicalHistoryEntry;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class ClinicalHistoryRaceTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private ClinicalHistoryView historyView;

  @Override
  public void start(Stage stage) {
    historyView =
        ClinicalHistoryView.create(
            mock(ClinicServices.class),
            new Session(7, "doctor", Role.DOCTOR),
            new Label(),
            taskRunner);
    Scene scene = new Scene(new StackPane(historyView.view()), 1200, 760);
    UiComponents.applyStylesheet(scene);
    stage.setScene(scene);
    stage.show();
  }

  @Test
  void requestingAnotherPatientClearsThePreviousHistoryBeforeTheLookupFinishes() {
    Patient firstPatient = patient(1, "First");
    Patient secondPatient = patient(2, "Second");
    succeed(0, List.of(firstPatient, secondPatient));

    interact(() -> historyView.showPatient(firstPatient.id()));
    succeed(1, firstPatient);
    succeed(2, List.of(historyEntry(firstPatient.id())));
    assertEquals(1, historyList().getItems().size());

    interact(() -> historyView.showPatient(secondPatient.id()));
    assertTrue(historyList().getItems().isEmpty());
    fail(3);
    assertTrue(historyList().getItems().isEmpty());
  }

  @SuppressWarnings("unchecked")
  private ListView<ClinicalHistoryEntry> historyList() {
    return lookup("#doctor-history-list").queryAs(ListView.class);
  }

  private void succeed(int index, Object value) {
    interact(() -> taskRunner.submissions.get(index).success().accept(value));
  }

  private void fail(int index) {
    interact(
        () ->
            taskRunner
                .submissions
                .get(index)
                .failure()
                .accept(new IllegalStateException("load failed")));
  }

  private static Patient patient(long id, String firstName) {
    return new Patient(id, firstName, "Patient", "", "555-0100", "", "");
  }

  private static ClinicalHistoryEntry historyEntry(long patientId) {
    Appointment appointment =
        new Appointment(
            11,
            patientId,
            7,
            java.time.LocalDateTime.of(2026, 9, 23, 10, 0),
            java.time.LocalDateTime.of(2026, 9, 23, 10, 30),
            AppointmentStatus.COMPLETED);
    ClinicalRecord record =
        new ClinicalRecord(12, patientId, appointment.id(), 7, "Diagnosis", "Notes", "Follow-up");
    return new ClinicalHistoryEntry(appointment, "Dr. Doctor", record, List.of());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<PendingSubmission> submissions = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(value -> onSuccess.accept((T) value), onFailure));
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }

  private record PendingSubmission(Consumer<Object> success, Consumer<Throwable> failure) {}
}
