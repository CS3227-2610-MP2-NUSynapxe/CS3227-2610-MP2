package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class PatientDirectoryLifecycleTest extends ApplicationTest {
  private static final int INITIAL_SUBMISSION_COUNT = 1;

  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private PatientDirectoryView directory;

  @Override
  public void start(Stage stage) throws Exception {
    Patient patient1 =
        new Patient(42, "Alex", "Tan", "1990-01-01", "555-0100", "alex@example.com", "Address");
    Patient patient2 =
        new Patient(43, "Bob", "Lee", "1992-02-02", "555-0200", "bob@example.com", "Address 2");
    PatientService patientService = mock(PatientService.class);
    when(patientService.searchAdministrative(any(), anyString()))
        .thenReturn(List.of(patient1, patient2));
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
    Scene scene = new Scene(new StackPane(directory.view()), 1200, 760);
    UiComponents.applyStylesheet(scene);
    stage.setScene(scene);
    stage.show();
  }

  @AfterEach
  void tearDown() {
    taskRunner.close();
  }

  @Test
  void leavingPatientViewIgnoresSubsequentStatusCallback() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-deactivate").queryAs(Button.class).fire());
    assertEquals(2, taskRunner.submissions.size());

    interact(() -> lookup("#test-patient-view-back").queryAs(Button.class).fire());

    interact(() -> lookup("#test-patient-view-43").queryAs(Button.class).fire());
    Button statusButton = lookup("#test-patient-deactivate").queryAs(Button.class);
    assertEquals("Deactivate patient", statusButton.getText());

    Patient deactivatedPatient =
        new Patient(
            42,
            null,
            null,
            null,
            "Alex",
            "Tan",
            "1990-01-01",
            null,
            null,
            "555-0100",
            "alex@example.com",
            "Address",
            null,
            null,
            false);
    interact(() -> taskRunner.submissions.get(1).success().accept(deactivatedPatient));

    assertEquals("Deactivate patient", statusButton.getText());
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

  @Test
  void leavingPatientViewIgnoresSubsequentDeletionBlockersCallback() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-delete").queryAs(Button.class).fire());
    assertEquals(2, taskRunner.submissions.size());

    interact(() -> lookup("#test-patient-view-back").queryAs(Button.class).fire());
    assertEquals(3, taskRunner.submissions.size());

    interact(
        () ->
            taskRunner
                .submissions
                .get(1)
                .success()
                .accept(new PatientDeletionBlockers(42, 0, 0, 0, 0, 0, 0)));

    assertFalse(lookup("#test-patient-delete-confirm-window").tryQuery().isPresent());
  }

  @Test
  void patientDeleteButtonIsDisabledWhileBlockerCheckIsQueued() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    Button deleteButton = lookup("#test-patient-delete").queryAs(Button.class);
    assertFalse(deleteButton.isDisable());

    interact(deleteButton::fire);
    assertTrue(deleteButton.isDisable());

    interact(
        () -> taskRunner.submissions.getLast().failure().accept(new RuntimeException("failed")));
    assertFalse(deleteButton.isDisable());
  }

  @Test
  void patientMutationsUpdatePreferredPatientId() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    assertEquals(42, directory.selectedPatientId());

    interact(() -> lookup("#test-patient-view-back").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-view-43").queryAs(Button.class).fire());
    assertEquals(43, directory.selectedPatientId());

    interact(() -> lookup("#test-patient-edit").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-update").queryAs(Button.class).fire());
    Patient updatedPatient =
        new Patient(43, "Bob", "Lee", "1992-02-02", "555-0200", "bob@example.com", "Address 2");
    interact(() -> taskRunner.submissions.getLast().success().accept(updatedPatient));
    assertEquals(43, directory.selectedPatientId());

    interact(() -> lookup("#test-patient-deactivate").queryAs(Button.class).fire());
    Patient deactivatedPatient =
        new Patient(
            43,
            null,
            null,
            null,
            "Bob",
            "Lee",
            "1992-02-02",
            null,
            null,
            "555-0200",
            "bob@example.com",
            "Address 2",
            null,
            null,
            false);
    interact(() -> taskRunner.submissions.getLast().success().accept(deactivatedPatient));
    assertEquals(43, directory.selectedPatientId());
  }

  @Test
  void patientStatusSubmissionIsDisabledUntilTheQueuedRequestFinishes() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-deactivate").queryAs(Button.class).fire());

    assertTrue(lookup("#test-patient-deactivate").queryAs(Button.class).isDisable());
    assertEquals(2, taskRunner.submissions.size());
    interact(() -> lookup("#test-patient-deactivate").queryAs(Button.class).fire());
    assertEquals(2, taskRunner.submissions.size());
  }

  @Test
  void registrationSubmissionDisablesWorkflowUntilQueuedRequestFinishes() {
    interact(() -> lookup("#test-patient-open-register").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-register").queryAs(Button.class).fire());

    assertTrue(lookup("#test-patient-register").queryAs(Button.class).isDisable());
    assertTrue(lookup("#test-patient-register-cancel").queryAs(Button.class).isDisable());

    interact(
        () -> taskRunner.submissions.getLast().failure().accept(new RuntimeException("failed")));

    assertFalse(lookup("#test-patient-register").queryAs(Button.class).isDisable());
    assertFalse(lookup("#test-patient-register-cancel").queryAs(Button.class).isDisable());
  }

  @Test
  void discardedPatientEditIgnoresSubsequentUpdateCallback() {
    interact(() -> lookup("#test-patient-view-42").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-edit").queryAs(Button.class).fire());
    interact(() -> lookup("#test-patient-update").queryAs(Button.class).fire());
    assertEquals(2, taskRunner.submissions.size());

    // User discards changes before background update finishes
    interact(() -> lookup("#test-patient-edit-cancel").queryAs(Button.class).fire());
    assertFalse(lookup("#test-patient-edit-card").tryQuery().isPresent());

    // When update finishes, it should not resurrect or replace with the edit callback
    Patient updatedPatient =
        new Patient(42, "Updated", "Name", "1990-01-01", "555-0100", "test@example.com", "Address");
    interact(() -> taskRunner.submissions.getLast().success().accept(updatedPatient));

    assertFalse(lookup("#test-patient-edit-card").tryQuery().isPresent());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final ClinicTaskRunner immediateRunner = ClinicTaskRunner.immediate();
    private final java.util.List<PendingSubmission> submissions = new java.util.ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(task, value -> onSuccess.accept((T) value), onFailure));
      if (submissions.size() == INITIAL_SUBMISSION_COUNT) {
        immediateRunner.submit(task, onSuccess, onFailure);
      }
    }

    @Override
    public void close() {
      immediateRunner.close();
    }
  }

  private record PendingSubmission(
      ClinicTaskRunner.ClinicTask<?> task, Consumer<Object> success, Consumer<Throwable> failure) {}
}
