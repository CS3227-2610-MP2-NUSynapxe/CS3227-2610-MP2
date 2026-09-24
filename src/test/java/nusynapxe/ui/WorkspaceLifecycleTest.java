package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.Test;

final class WorkspaceLifecycleTest {
  @Test
  void remainsActiveUntilTheOwningWorkspaceInvalidatesIt() {
    WorkspaceLifecycle lifecycle = new WorkspaceLifecycle();

    assertTrue(lifecycle.isActive());

    lifecycle.invalidate();
    lifecycle.invalidate();

    assertFalse(lifecycle.isActive());
  }

  @Test
  void staleAppointmentEditorDoesNotSubmitItsSecondLoad() throws Exception {
    try (ClinicTaskRunner taskRunner = mock(ClinicTaskRunner.class)) {
      List<PendingSubmission> submissions = new ArrayList<>();
      doAnswer(
              invocation -> {
                submissions.add(
                    new PendingSubmission(invocation.getArgument(1), invocation.getArgument(2)));
                return null;
              })
          .when(taskRunner)
          .submit(any(), any(), any());
      WorkspaceLifecycle lifecycle = new WorkspaceLifecycle();
      Label feedback = onFx(Label::new);

      onFx(
          () -> {
            AppointmentDialog.showDoctorEdit(
                mock(ClinicServices.class),
                new Session(7, "doctor", Role.DOCTOR),
                42,
                feedback,
                () -> {},
                taskRunner,
                lifecycle::isActive);
            return null;
          });
      lifecycle.invalidate();
      onFx(
          () -> {
            submissions
                .getFirst()
                .success()
                .accept(
                    new Appointment(
                        42,
                        11,
                        7,
                        LocalDateTime.of(2026, 9, 22, 10, 0),
                        LocalDateTime.of(2026, 9, 22, 10, 30),
                        AppointmentStatus.PENDING));
            return null;
          });

      assertEquals(1, submissions.size());
    }
  }

  private static <T> T onFx(FxOperation<T> operation) {
    if (Platform.isFxApplicationThread()) {
      return operation.run();
    }
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException ignored) {
      // Another JavaFX test may have started the toolkit already.
    }
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(
        () -> {
          try {
            result.set(operation.run());
          } catch (AssertionError exception) {
            failure.set(exception);
          } finally {
            completed.countDown();
          }
        });
    try {
      assertTrue(completed.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("JavaFX operation was interrupted", exception);
    }
    if (failure.get() != null) {
      throw new AssertionError("JavaFX operation failed", failure.get());
    }
    return result.get();
  }

  @FunctionalInterface
  private interface FxOperation<T> {
    T run();
  }

  private record PendingSubmission(Consumer<Object> success, Consumer<Throwable> failure) {}
}
