package nusynapxe.ui;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Synchronous task runner used by deterministic view factories and unit tests. */
final class ImmediateClinicTaskRunner implements ClinicTaskRunner {
  private final AtomicBoolean closed = new AtomicBoolean();

  @Override
  public <T> void submit(ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
    ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
    if (closed.get()) {
      throw new RejectedExecutionException("Clinic task runner is closed");
    }
    try {
      onSuccess.accept(task.run());
    } catch (Throwable failure) {
      onFailure.accept(failure);
    }
  }

  @Override
  public void close() {
    closed.set(true);
  }
}
