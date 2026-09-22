package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ClinicTaskRunnerTest {
  @BeforeAll
  static void startJavaFx() throws InterruptedException {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException ignored) {
      // The TestFX suite may already have initialized the toolkit.
    }
  }

  @Test
  void serializedRunnerExecutesTasksInSubmissionOrder() throws Exception {
    List<Integer> order = new CopyOnWriteArrayList<>();
    CountDownLatch callbacks = new CountDownLatch(2);
    try (ClinicTaskRunner runner = new SerializedClinicTaskRunner()) {
      runner.submit(
          () -> {
            order.add(1);
            return null;
          },
          ignored -> callbacks.countDown(),
          failure -> callbacks.countDown());
      runner.submit(
          () -> {
            order.add(2);
            return null;
          },
          ignored -> callbacks.countDown(),
          failure -> callbacks.countDown());

      assertTrue(callbacks.await(5, TimeUnit.SECONDS));
    }
    assertEquals(List.of(1, 2), order);
  }

  @Test
  void callbacksRunOnTheJavaFxThreadAndFailuresReachFailureCallback() throws Exception {
    CountDownLatch callback = new CountDownLatch(1);
    AtomicBoolean successOnFxThread = new AtomicBoolean();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (ClinicTaskRunner runner = new SerializedClinicTaskRunner()) {
      runner.submit(
          () -> {
            throw new IllegalStateException("broken load");
          },
          ignored -> successOnFxThread.set(Platform.isFxApplicationThread()),
          exception -> {
            successOnFxThread.set(Platform.isFxApplicationThread());
            failure.set(exception);
            callback.countDown();
          });
      assertTrue(callback.await(5, TimeUnit.SECONDS));
    }
    assertTrue(successOnFxThread.get());
    assertEquals("broken load", failure.get().getMessage());
  }

  @Test
  void closeRejectsNewWork() {
    ClinicTaskRunner runner = new SerializedClinicTaskRunner();
    runner.close();

    assertThrows(
        java.util.concurrent.RejectedExecutionException.class,
        () -> runner.submit(() -> 1, ignored -> {}, failure -> {}));
  }

  @Test
  void immediateRunnerAppliesOnlyTheLatestGeneration() {
    List<Integer> applied = new CopyOnWriteArrayList<>();
    ClinicTaskRunner runner = ClinicTaskRunner.immediate();
    long firstGeneration = 1;
    long latestGeneration = 2;

    runner.submit(
        () -> 1,
        value -> {
          if (firstGeneration == latestGeneration) {
            applied.add(value);
          }
        },
        failure -> {});
    runner.submit(
        () -> 2,
        value -> {
          if (latestGeneration == 2) {
            applied.add(value);
          }
        },
        failure -> {});
    runner.close();

    assertEquals(List.of(2), applied);
    assertFalse(applied.contains(1));
  }
}
