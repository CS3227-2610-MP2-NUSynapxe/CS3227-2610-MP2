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
  private static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;

  private final ExecutorService executor;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final long gracefulShutdownTimeoutNanos;
  private final long forcedShutdownTimeoutNanos;

  /** Creates a runner backed by one daemon worker thread. */
  public SerializedClinicTaskRunner() {
    this(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  SerializedClinicTaskRunner(
      long gracefulShutdownTimeout, long forcedShutdownTimeout, TimeUnit timeUnit) {
    if (gracefulShutdownTimeout <= 0 || forcedShutdownTimeout <= 0) {
      throw new IllegalArgumentException("Shutdown timeouts must be positive");
    }
    TimeUnit unit = Objects.requireNonNull(timeUnit, "timeUnit");
    gracefulShutdownTimeoutNanos = unit.toNanos(gracefulShutdownTimeout);
    forcedShutdownTimeoutNanos = unit.toNanos(forcedShutdownTimeout);
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
    }
    if (executor.isTerminated()) {
      return;
    }

    boolean interrupted = false;
    try {
      if (executor.awaitTermination(gracefulShutdownTimeoutNanos, TimeUnit.NANOSECONDS)) {
        return;
      }
    } catch (InterruptedException interruption) {
      interrupted = true;
    }

    executor.shutdownNow();
    try {
      executor.awaitTermination(forcedShutdownTimeoutNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException interruption) {
      interrupted = true;
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    if (!executor.isTerminated()) {
      throw new IllegalStateException(
          "Clinic database worker did not stop; database resources must remain open");
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
