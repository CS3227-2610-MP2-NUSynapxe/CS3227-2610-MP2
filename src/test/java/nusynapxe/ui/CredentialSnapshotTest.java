package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import nusynapxe.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class CredentialSnapshotTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private Scene scene;

  @Override
  public void start(Stage stage) {
    scene = styledScene(new StackPane());
    stage.setScene(scene);
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

    assertTrue(lookup("#admin-account-submit").queryAs(Button.class).isDisable());

    setText("#admin-account-username", "doctor-after-queue");
    setText("#admin-account-display-name", "Dr. After Queue");
    setText("#admin-account-password", "after-pass");
    setText("#admin-account-confirm-password", "after-pass");
    taskRunner.tasks.getLast().run();

    assertEquals("doctor-before-queue", submittedUsername.get());
    assertEquals("Dr. Before Queue", submittedDisplayName.get());
    assertEquals(Role.DOCTOR, submittedRole.get());
    assertArrayEquals("before-pass".toCharArray(), submittedPassword.get());

    interact(() -> taskRunner.submissions.getLast().success().accept(null));
    assertFalse(lookup("#admin-account-submit").queryAs(Button.class).isDisable());
    assertEquals(
        "doctor-after-queue",
        lookup("#admin-account-username").queryAs(TextInputControl.class).getText());
    assertEquals(
        "Dr. After Queue",
        lookup("#admin-account-display-name").queryAs(TextInputControl.class).getText());
    assertEquals(
        "after-pass", lookup("#admin-account-password").queryAs(TextInputControl.class).getText());
    assertEquals(
        "after-pass",
        lookup("#admin-account-confirm-password").queryAs(TextInputControl.class).getText());
  }

  @Test
  void systemAdminClearsMatchingFieldsOnSuccessfulCreation() {
    AccountService accounts = mock(AccountService.class);
    show(
        SystemAdminView.create(
            accounts, new Session(1, "admin", Role.SYSTEM_ADMIN), () -> {}, taskRunner));

    setText("#admin-account-username", "doctor-clean");
    setText("#admin-account-display-name", "Dr. Clean");
    setText("#admin-account-password", "clean-pass");
    setText("#admin-account-confirm-password", "clean-pass");
    fire("#admin-account-submit");

    interact(() -> taskRunner.submissions.getLast().success().accept(null));

    assertEquals("", lookup("#admin-account-username").queryAs(TextInputControl.class).getText());
    assertEquals(
        "", lookup("#admin-account-display-name").queryAs(TextInputControl.class).getText());
    assertEquals("", lookup("#admin-account-password").queryAs(TextInputControl.class).getText());
    assertEquals(
        "", lookup("#admin-account-confirm-password").queryAs(TextInputControl.class).getText());
    assertFalse(lookup("#admin-account-submit").queryAs(Button.class).isDisable());
  }

  @Test
  void systemAdminReenablesSubmitOnFailure() {
    AccountService accounts = mock(AccountService.class);
    show(
        SystemAdminView.create(
            accounts, new Session(1, "admin", Role.SYSTEM_ADMIN), () -> {}, taskRunner));

    setText("#admin-account-username", "doctor-fail");
    setText("#admin-account-display-name", "Dr. Fail");
    setText("#admin-account-password", "fail-pass");
    setText("#admin-account-confirm-password", "fail-pass");
    fire("#admin-account-submit");

    assertTrue(lookup("#admin-account-submit").queryAs(Button.class).isDisable());
    interact(
        () ->
            taskRunner
                .submissions
                .getLast()
                .failure()
                .accept(new ValidationException("Username already exists")));
    assertFalse(lookup("#admin-account-submit").queryAs(Button.class).isDisable());
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

  @Test
  void loginIgnoresAStaleSuccessfulCallbackAfterANewerSubmission() {
    AuthenticationService authentication = mock(AuthenticationService.class);
    List<Session> acceptedSessions = new ArrayList<>();
    show(LoginView.create(authentication, acceptedSessions::add, taskRunner));

    setText("#login-username", "first-user");
    setText("#login-password", "first-pass");
    fire("#login-submit");
    setText("#login-username", "second-user");
    setText("#login-password", "second-pass");
    fire("#login-submit");

    interact(
        () ->
            taskRunner
                .submissions
                .getFirst()
                .success()
                .accept(Optional.of(new Session(9, "first-user", Role.DOCTOR))));
    assertTrue(acceptedSessions.isEmpty());
    interact(() -> taskRunner.submissions.getLast().success().accept(Optional.empty()));
    assertTrue(acceptedSessions.isEmpty());
  }

  private void show(Parent view) {
    interact(() -> scene.setRoot(view));
  }

  private static Scene styledScene(Parent root) {
    Scene scene = new Scene(root, 1200, 760);
    UiComponents.applyStylesheet(scene);
    return scene;
  }

  private void setText(String selector, String value) {
    interact(() -> lookup(selector).queryAs(TextInputControl.class).setText(value));
  }

  private void fire(String selector) {
    interact(() -> lookup(selector).queryAs(Button.class).fire());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<ClinicTask<?>> tasks = new ArrayList<>();
    private final List<PendingSubmission> submissions = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task,
        java.util.function.Consumer<T> onSuccess,
        java.util.function.Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      tasks.add(task);
      submissions.add(new PendingSubmission(value -> onSuccess.accept((T) value), onFailure));
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }

  private record PendingSubmission(
      java.util.function.Consumer<Object> success,
      java.util.function.Consumer<Throwable> failure) {}
}
