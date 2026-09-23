package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PatientDeletionBlockers;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.PatientService;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class PatientDirectoryLifecycleTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private PatientDirectoryView directory;
  private Stage stage;

  @Override
  public void start(Stage stage) throws Exception {
    this.stage = stage;
    Patient patient = new Patient(42, "Pat", "Patient", "", "555-0100", "", "");
    PatientService patientService = mock(PatientService.class);
    when(patientService.searchAdministrative(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of(patient));
    ClinicServices services = mock(ClinicServices.class);
    when(services.patientService()).thenReturn(patientService);
    directory =
        PatientDirectoryView.create(
            services,
            new Session(7, "reception", Role.RECEPTIONIST),
            "test",
            new Label(),
            ignored -> {},
            null,
            java.time.Clock.systemUTC(),
            taskRunner);
    stage.setScene(new Scene(new StackPane(directory.view()), 800, 600));
    stage.show();
  }

  @Test
  void staleDeletionBlockerCallbacksDoNotOpenDialogAfterDirectoryDisposal() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-delete").queryAs(Button.class).fire());
    assertEquals(2, taskRunner.submissions.size());

    directory.dispose();
    interact(
        () ->
            taskRunner
                .submissions
                .getLast()
                .success()
                .accept(new PatientDeletionBlockers(42, 0, 0, 0, 0, 0, 0)));

    assertEquals(2, taskRunner.submissions.size());
    assertFalse(lookup("#test-patient-delete-confirm-window").tryQuery().isPresent());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final java.util.List<PendingSubmission> submissions = new java.util.ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(task, value -> onSuccess.accept((T) value), onFailure));
      if (submissions.size() == 1) {
        try {
          onSuccess.accept(task.run());
        } catch (Exception exception) {
          onFailure.accept(exception);
        }
      }
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }

  private record PendingSubmission(
      ClinicTaskRunner.ClinicTask<?> task, Consumer<Object> success, Consumer<Throwable> failure) {}
}
