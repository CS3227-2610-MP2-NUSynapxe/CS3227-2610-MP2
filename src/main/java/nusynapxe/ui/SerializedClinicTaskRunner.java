package nusynapxe.ui;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Executes clinic database tasks serially because the application owns one SQLite connection. */
public final class SerializedClinicTaskRunner implements ClinicTaskRunner {
  private final ExecutorService executor;
  private final AtomicBoolean closed = new AtomicBoolean();

  /** Creates a runner backed by one daemon worker thread. */
  public SerializedClinicTaskRunner() {
    executor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread worker = new Thread(runnable, "nusynapxe-clinic-db");
              worker.setDaemon(true);
              return worker;
            });
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public <T> void submit(ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
    ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
    if (closed.get()) {
      throw new RejectedExecutionException("Clinic task runner is closed");
    }
    try {
      executor.execute(
          () -> {
            try {
              T result = task.run();
              dispatch(() -> onSuccess.accept(result));
            } catch (Exception failure) {
              dispatch(() -> onFailure.accept(failure));
            }
          });
    } catch (RejectedExecutionException exception) {
      throw exception;
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      executor.shutdown();
      boolean interrupted = false;
      try {
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
          // Keep waiting so the database worker has definitely stopped before its connection
          // closes.
          Thread.yield();
        }
      } catch (InterruptedException interruption) {
        executor.shutdownNow();
        interrupted = true;
        while (!executor.isTerminated()) {
          try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
          } catch (InterruptedException ignored) {
            interrupted = true;
          }
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void dispatch(Runnable callback) {
    Objects.requireNonNull(callback, "callback");
    if (Platform.isFxApplicationThread()) {
      callback.run();
    } else {
      Platform.runLater(callback);
    }
  }
}
