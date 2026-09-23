package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.AccountService;
import nusynapxe.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class CredentialSnapshotTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private Stage stage;

  @Override
  public void start(Stage stage) {
    this.stage = stage;
    stage.setScene(new Scene(new StackPane(), 800, 600));
    stage.show();
  }

  @Test
  void systemAdminCapturesAllStaffFieldsBeforeQueueingCreation() throws Exception {
    AccountService accounts = mock(AccountService.class);
    AtomicReference<String> submittedUsername = new AtomicReference<>();
    AtomicReference<String> submittedDisplayName = new AtomicReference<>();
    AtomicReference<Role> submittedRole = new AtomicReference<>();
    AtomicReference<char[]> submittedPassword = new AtomicReference<>();
    doAnswer(
            invocation -> {
              submittedUsername.set(invocation.getArgument(1));
              submittedDisplayName.set(invocation.getArgument(2));
              submittedRole.set(invocation.getArgument(3));
              submittedPassword.set(((char[]) invocation.getArgument(4)).clone());
              return null;
            })
        .when(accounts)
        .createStaff(any(), anyString(), anyString(), any(), any(char[].class));
    show(
        SystemAdminView.create(
            accounts, new Session(1, "admin", Role.SYSTEM_ADMIN), () -> {}, taskRunner));

    setText("#admin-account-username", "doctor-before-queue");
    setText("#admin-account-display-name", "Dr. Before Queue");
    setText("#admin-account-password", "before-pass");
    setText("#admin-account-confirm-password", "before-pass");
    fire("#admin-account-submit");

    setText("#admin-account-username", "doctor-after-queue");
    setText("#admin-account-display-name", "Dr. After Queue");
    setText("#admin-account-password", "after-pass");
    setText("#admin-account-confirm-password", "after-pass");
    taskRunner.tasks.getLast().run();

    assertEquals("doctor-before-queue", submittedUsername.get());
    assertEquals("Dr. Before Queue", submittedDisplayName.get());
    assertEquals(Role.DOCTOR, submittedRole.get());
    assertArrayEquals("before-pass".toCharArray(), submittedPassword.get());
  }

  @Test
  void loginCapturesCredentialsBeforeQueueingAuthentication() throws Exception {
    AuthenticationService authentication = mock(AuthenticationService.class);
    AtomicReference<String> submittedUsername = new AtomicReference<>();
    AtomicReference<char[]> submittedPassword = new AtomicReference<>();
    doAnswer(
            invocation -> {
              submittedUsername.set(invocation.getArgument(0));
              submittedPassword.set(((char[]) invocation.getArgument(1)).clone());
              return Optional.empty();
            })
        .when(authentication)
        .login(anyString(), any(char[].class));
    show(LoginView.create(authentication, ignored -> {}, taskRunner));

    setText("#login-username", "user-before-queue");
    setText("#login-password", "before-pass");
    fire("#login-submit");

    setText("#login-username", "user-after-queue");
    setText("#login-password", "after-pass");
    taskRunner.tasks.getLast().run();

    assertEquals("user-before-queue", submittedUsername.get());
    assertArrayEquals("before-pass".toCharArray(), submittedPassword.get());
  }

  private void show(Parent view) {
    interact(() -> stage.setScene(new Scene(view, 800, 600)));
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<ClinicTask<?>> tasks = new ArrayList<>();

    @Override
    public <T> void submit(
        ClinicTask<T> task,
        java.util.function.Consumer<T> onSuccess,
        java.util.function.Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      tasks.add(task);
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }
}
