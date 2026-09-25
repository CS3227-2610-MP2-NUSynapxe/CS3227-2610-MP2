package nusynapxe.ui;

import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/** Coalesces rapid JavaFX events into one action after a short quiet period. */
final class FxDebouncer {
  private final PauseTransition delay;
  private final Runnable action;

  FxDebouncer(Duration duration, Runnable action) {
    delay = new PauseTransition(Objects.requireNonNull(duration, "duration"));
    this.action = Objects.requireNonNull(action, "action");
    delay.setOnFinished(event -> this.action.run());
  }

  void request() {
    delay.playFromStart();
  }

  void runNow() {
    delay.stop();
    action.run();
  }

  void cancel() {
    delay.stop();
  }
}
