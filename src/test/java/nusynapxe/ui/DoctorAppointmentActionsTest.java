package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class DoctorAppointmentActionsTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private final DoctorAppointmentActions.SelectionState selection =
      new DoctorAppointmentActions.SelectionState();
  private final WorkspaceLifecycle lifecycle = new WorkspaceLifecycle();
  private Button accept;
  private Button decline;
  private Button checkIn;
  private Button reschedule;
  private Button complete;
  private Label feedback;
  private boolean refreshed;

  @Override
  public void start(Stage stage) {
    accept = new Button("Accept");
    decline = new Button("Decline");
    checkIn = new Button("Check in");
    reschedule = new Button("Reschedule");
    complete = new Button("Complete");
    feedback = new Label();
    ClinicServices services = mock(ClinicServices.class);
    Session session = new Session(10, "doc", Role.DOCTOR);
    selection.appointmentId = 100;
    selection.generation = 1;

    DoctorAppointmentActions.configure(
        accept,
        decline,
        checkIn,
        reschedule,
        complete,
        services,
        session,
        selection,
        feedback,
        taskRunner,
        () -> refreshed = true,
        lifecycle);

    VBox root = new VBox(accept, decline, checkIn, reschedule, complete, feedback);
    stage.setScene(new Scene(root));
    stage.show();
  }

  @AfterEach
  void tearDown() {
    taskRunner.close();
  }

  @Test
  void actionSubmissionDisablesActionsUntilCompletion() {
    assertFalse(accept.isDisable());
    assertFalse(decline.isDisable());
    assertFalse(checkIn.isDisable());
    assertFalse(reschedule.isDisable());
    assertFalse(complete.isDisable());

    interact(accept::fire);

    assertEquals(1, taskRunner.submissions.size());
    assertTrue(accept.isDisable());
    assertTrue(decline.isDisable());
    assertTrue(checkIn.isDisable());
    assertTrue(reschedule.isDisable());
    assertTrue(complete.isDisable());

    interact(() -> taskRunner.submissions.getFirst().success().accept(null));

    assertFalse(accept.isDisable());
    assertFalse(decline.isDisable());
    assertFalse(checkIn.isDisable());
    assertFalse(reschedule.isDisable());
    assertFalse(complete.isDisable());
    assertTrue(refreshed);
    assertEquals("Appointment accepted", feedback.getText());
  }

  @Test
  void actionSubmissionFailureRestoresActionsAndShowsError() {
    interact(decline::fire);

    assertEquals(1, taskRunner.submissions.size());
    assertTrue(accept.isDisable());
    assertTrue(decline.isDisable());

    interact(
        () ->
            taskRunner
                .submissions
                .getFirst()
                .failure()
                .accept(new RuntimeException("database timeout")));

    assertFalse(accept.isDisable());
    assertFalse(decline.isDisable());
    assertFalse(checkIn.isDisable());
    assertFalse(reschedule.isDisable());
    assertFalse(complete.isDisable());
    assertEquals("The requested operation is temporarily unavailable", feedback.getText());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<PendingSubmission> submissions = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(task, value -> onSuccess.accept((T) value), onFailure));
    }

    @Override
    public void close() {
      // Satisfies Closeable
    }
  }

  private record PendingSubmission(
      ClinicTaskRunner.ClinicTask<?> task, Consumer<Object> success, Consumer<Throwable> failure) {}
}
