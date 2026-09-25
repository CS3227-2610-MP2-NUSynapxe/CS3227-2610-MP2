package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.AccountService;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class SystemAdminLifecycleTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private final AtomicBoolean loggedOut = new AtomicBoolean();
  private AccountService accounts;

  @Override
  public void start(Stage stage) {
    accounts = mock(AccountService.class);
    stage.setScene(
        new Scene(
            SystemAdminView.create(
                accounts,
                new Session(1, "admin", Role.SYSTEM_ADMIN),
                () -> loggedOut.set(true),
                taskRunner)));
    stage.show();
  }

  @Test
  void queuedAccountCreationIsCancelledWhenTheAdminLogsOut() throws Exception {
    interact(
        () -> {
          text("#admin-account-username").setText("doctor");
          text("#admin-account-display-name").setText("Doctor");
          text("#admin-account-password").setText("secure-pass");
          text("#admin-account-confirm-password").setText("secure-pass");
          button("#admin-account-submit").fire();
          button("#logout-button").fire();
        });

    assertEquals(2, taskRunner.submissions.size());
    Object result = taskRunner.submissions.get(1).task().run();
    interact(() -> taskRunner.submissions.get(1).success().accept(result));

    assertFalse((Boolean) result);
    assertTrue(loggedOut.get());
    verify(accounts, never())
        .createStaff(any(), anyString(), anyString(), any(), any(char[].class));
  }

  private TextInputControl text(String selector) {
    return lookup(selector).queryAs(TextInputControl.class);
  }

  private Button button(String selector) {
    return lookup(selector).queryAs(Button.class);
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<PendingSubmission> submissions = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(task, value -> onSuccess.accept((T) value)));
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }

  private record PendingSubmission(ClinicTaskRunner.ClinicTask<?> task, Consumer<Object> success) {}
}
