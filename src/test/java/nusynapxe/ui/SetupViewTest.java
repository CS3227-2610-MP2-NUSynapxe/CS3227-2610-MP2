package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import nusynapxe.service.AccountService;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class SetupViewTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private AccountService accounts;

  @Override
  public void start(Stage stage) {
    accounts = mock(AccountService.class);
    stage.setScene(new Scene(SetupView.create(accounts, () -> {}, taskRunner), 800, 600));
    stage.show();
  }

  @Test
  void setupCapturesCredentialsBeforeTheQueuedTaskRuns() throws Exception {
    AtomicReference<String> submittedUsername = new AtomicReference<>();
    AtomicReference<char[]> submittedPassword = new AtomicReference<>();
    doAnswer(
            invocation -> {
              submittedUsername.set(invocation.getArgument(0));
              submittedPassword.set(((char[]) invocation.getArgument(1)).clone());
              return null;
            })
        .when(accounts)
        .createInitialAdmin(anyString(), any(char[].class));

    setText("#setup-username", "initial-admin");
    setText("#setup-password", "initial-pass");
    setText("#setup-confirm-password", "initial-pass");
    interact(() -> lookup("#setup-submit").queryAs(Button.class).fire());

    setText("#setup-username", "changed-admin");
    setText("#setup-password", "changed-pass");
    taskRunner.submitted.getFirst().run();

    assertEquals("initial-admin", submittedUsername.get());
    assertArrayEquals("initial-pass".toCharArray(), submittedPassword.get());
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextField.class).setText(value));
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<ClinicTask<?>> submitted = new ArrayList<>();

    @Override
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submitted.add(task);
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }
}
