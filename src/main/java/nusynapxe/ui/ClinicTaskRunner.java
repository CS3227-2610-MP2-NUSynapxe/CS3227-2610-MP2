package nusynapxe.ui;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Runs blocking clinic work and applies its result through JavaFX-safe callbacks. */
public interface ClinicTaskRunner extends AutoCloseable {
  /**
   * A unit of blocking work that may use checked exceptions.
   *
   * @param <T> result type
   */
  @FunctionalInterface
  interface ClinicTask<T> {
    /**
     * Executes the blocking operation.
     *
     * @return operation result
     * @throws Exception if the operation fails
     */
    T run() throws Exception;
  }

  /**
   * Submits one unit of work.
   *
   * @param task blocking work to execute
   * @param onSuccess callback for a successful result
   * @param onFailure callback for a failed task
   * @param <T> result type
   * @throws RejectedExecutionException if this runner is closed
   */
  <T> void submit(ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure);

  /** Stops accepting work and releases the worker resources. */
  @Override
  void close();

  /**
   * Creates a deterministic runner intended for tests and compatibility factories.
   *
   * @return an immediately executing task runner
   */
  static ClinicTaskRunner immediate() {
    return new ImmediateClinicTaskRunner();
  }

  /**
   * Validates a task callback bundle before submission.
   *
   * @param task blocking task
   * @param onSuccess callback for a successful result
   * @param onFailure callback for a failed task
   * @param <T> result type
   * @throws NullPointerException if any argument is {@code null}
   */
  static <T> void requireCallbacks(
      ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
    Objects.requireNonNull(task, "task");
    Objects.requireNonNull(onSuccess, "onSuccess");
    Objects.requireNonNull(onFailure, "onFailure");
  }
}
