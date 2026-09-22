package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import nusynapxe.domain.Account;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ReceptionistDataLoaderTest {
  private final List<PendingSubmission> submissions = new ArrayList<>();
  private ClinicTaskRunner taskRunner;
  private ReceptionistDataLoader loader;

  @BeforeAll
  static void startJavaFx() {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException ignored) {
      // Another JavaFX test may have started the toolkit already.
    }
  }

  @BeforeEach
  void setUp() {
    taskRunner = mock(ClinicTaskRunner.class);
    doAnswer(
            invocation -> {
              submissions.add(
                  new PendingSubmission(invocation.getArgument(1), invocation.getArgument(2)));
              return null;
            })
        .when(taskRunner)
        .submit(any(), any(), any());
    loader =
        new ReceptionistDataLoader(
            mock(ClinicServices.class), new Session(1, "reception", Role.RECEPTIONIST), taskRunner);
  }

  @Test
  void newerLoadForOneSelectorDoesNotReplaceItsCurrentItems() throws Exception {
    SearchSuggestionField<Account> doctor = newField("doctor");
    Label feedback = onFx(Label::new);
    Account oldDoctor = account(1, "Old Doctor");
    Account currentDoctor = account(2, "Current Doctor");

    loader.refreshDoctors(doctor, feedback, true);
    loader.refreshDoctors(doctor, feedback, true);
    deliver(0, List.of(oldDoctor));
    deliver(1, List.of(currentDoctor));

    assertEquals(List.of(currentDoctor), onFx(doctor::getItems));
    assertEquals(currentDoctor, onFx(doctor::getValue));
  }

  @Test
  void loadsForDifferentSelectorsRemainIndependent() throws Exception {
    SearchSuggestionField<Account> bookingDoctor = newField("booking-doctor");
    SearchSuggestionField<Account> filterDoctor = newField("filter-doctor");
    Label feedback = onFx(Label::new);
    Account bookingAccount = account(1, "Booking Doctor");
    Account filterAccount = account(2, "Filter Doctor");

    loader.refreshDoctors(bookingDoctor, feedback, true);
    loader.refreshDoctors(filterDoctor, feedback, false);
    deliver(0, List.of(bookingAccount));
    deliver(1, List.of(filterAccount));

    assertEquals(List.of(bookingAccount), onFx(bookingDoctor::getItems));
    assertEquals(bookingAccount, onFx(bookingDoctor::getValue));
    assertEquals(List.of(filterAccount), onFx(filterDoctor::getItems));
    assertNull(onFx(filterDoctor::getValue));
  }

  private void deliver(int index, List<Account> doctors) throws Exception {
    onFx(
        () -> {
          submissions.get(index).success().accept(doctors);
          return null;
        });
  }

  private static Account account(long id, String displayName) {
    return new Account(id, "doctor" + id, displayName, Role.DOCTOR, true);
  }

  private static SearchSuggestionField<Account> newField(String id) throws Exception {
    return onFx(
        () ->
            new SearchSuggestionField<>(id, "Doctor", Account::displayName, Account::displayName));
  }

  private static <T> T onFx(FxOperation<T> operation) throws Exception {
    if (Platform.isFxApplicationThread()) {
      return operation.run();
    }
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(
        () -> {
          try {
            result.set(operation.run());
          } catch (Throwable exception) {
            failure.set(exception);
          } finally {
            completed.countDown();
          }
        });
    assertTrue(completed.await(5, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError("JavaFX operation failed", failure.get());
    }
    return result.get();
  }

  @FunctionalInterface
  private interface FxOperation<T> {
    T run() throws Exception;
  }

  private record PendingSubmission(Consumer<Object> success, Consumer<Throwable> failure) {}
}
