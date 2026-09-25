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
  private static final long SECOND_GENERATION = 2;
  private static final long LATEST_GENERATION = 2;

  @BeforeAll
  static void startJavaFx() throws InterruptedException {
    try {
      Platform.startup(
          () -> {
            // JavaFX toolkit startup requires a callback.
          });
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

  @SuppressWarnings("PMD.UseTryWithResources")
  @Test
  void closeRejectsNewWork() {
    ClinicTaskRunner runner = new SerializedClinicTaskRunner();
    try {
      runner.close();

      assertThrows(
          java.util.concurrent.RejectedExecutionException.class,
          () ->
              runner.submit(
                  () -> 1,
                  ignored -> {
                    // No success callback is expected.
                  },
                  failure -> {
                    // The rejection happens before a failure callback is registered.
                  }));
    } finally {
      runner.close();
    }
  }

  @SuppressWarnings("try")
  @Test
  void closeWaitsForSubmittedWorkBeforeReturning() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch closeStarted = new CountDownLatch(1);
    CountDownLatch closed = new CountDownLatch(1);
    try (ClinicTaskRunner runner = new SerializedClinicTaskRunner()) {
      runner.submit(
          () -> {
            started.countDown();
            release.await();
            return null;
          },
          ignored -> {
            // The test only observes executor shutdown.
          },
          failure -> {
            // The task is released normally.
          });
      assertTrue(started.await(5, TimeUnit.SECONDS));

      Thread closing =
          new Thread(
              () -> {
                closeStarted.countDown();
                runner.close();
                closed.countDown();
              });
      closing.start();
      assertTrue(closeStarted.await(5, TimeUnit.SECONDS));
      assertFalse(closed.await(100, TimeUnit.MILLISECONDS));
      release.countDown();
      assertTrue(closed.await(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void closeIsBoundedWhenAWorkerIgnoresInterruption() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try (SerializedClinicTaskRunner runner =
        new SerializedClinicTaskRunner(50, 50, TimeUnit.MILLISECONDS)) {
      try {
        runner.submit(
            () -> {
              started.countDown();
              while (release.getCount() > 0) {
                try {
                  release.await();
                } catch (InterruptedException ignored) {
                  // Deliberately model a database operation that does not honor interruption.
                }
              }
              return null;
            },
            ignored -> {},
            failure -> {});
        assertTrue(started.await(5, TimeUnit.SECONDS));

        long startedAt = System.nanoTime();
        IllegalStateException failure = assertThrows(IllegalStateException.class, runner::close);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsedMillis < 2_000, "Shutdown must not wait indefinitely");
        assertTrue(failure.getMessage().contains("resources must remain open"));
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void immediateRunnerAppliesOnlyTheLatestGeneration() {
    List<Integer> applied = new CopyOnWriteArrayList<>();
    try (ClinicTaskRunner runner = ClinicTaskRunner.immediate()) {
      long firstGeneration = 1;

      runner.submit(
          () -> 1,
          value -> {
            if (firstGeneration == LATEST_GENERATION) {
              applied.add(value);
            }
          },
          failure -> {
            // The immediate task does not fail.
          });
      runner.submit(
          () -> 2,
          value -> {
            if (LATEST_GENERATION == SECOND_GENERATION) {
              applied.add(value);
            }
          },
          failure -> {
            // The immediate task does not fail.
          });
    }

    assertEquals(List.of(2), applied);
    assertFalse(applied.contains(1));
  }
}
