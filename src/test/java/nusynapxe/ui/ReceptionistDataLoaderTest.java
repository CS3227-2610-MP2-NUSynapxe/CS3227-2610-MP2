package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Receipt;
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

  @Test
  void selectorRefreshShowsOnlyCurrentFailuresAndSupportsEmptyNoSelectionResults()
      throws Exception {
    SearchSuggestionField<Account> doctor = newField("doctor-failure");
    Label feedback = onFx(Label::new);

    loader.refreshDoctors(doctor, feedback, true);
    fail(0);
    assertEquals("Doctors are temporarily unavailable", onFx(feedback::getText));

    onFx(
        () -> {
          feedback.setText("");
          return null;
        });
    loader.refreshDoctors(doctor, feedback, true);
    loader.refreshDoctors(doctor, feedback, true);
    fail(1);
    assertEquals("", onFx(feedback::getText));
    deliver(2, List.of());
    assertTrue(onFx(doctor::getItems).isEmpty());
    assertNull(onFx(doctor::getValue));
  }

  @Test
  void appointmentAndReceiptRefreshesIgnoreStaleResultsAndReportCurrentFailures() throws Exception {
    TableView<AppointmentListRow> checkout = onFx(TableView::new);
    TableView<Receipt> history = onFx(TableView::new);
    Label checkoutFeedback = onFx(Label::new);
    Label receiptFeedback = onFx(Label::new);
    Label preview = onFx(Label::new);
    Appointment appointment =
        new Appointment(
            7,
            2,
            3,
            LocalDateTime.of(2026, 9, 23, 10, 0),
            LocalDateTime.of(2026, 9, 23, 10, 30),
            AppointmentStatus.COMPLETED);
    AppointmentListRow row = new AppointmentListRow(appointment, "Patient", "Doctor");

    loader.refreshCheckoutReady(checkout, checkoutFeedback, "", null, LocalDate.of(2026, 9, 23));
    loader.refreshCheckoutReady(checkout, checkoutFeedback, "", null, LocalDate.of(2026, 9, 23));
    deliver(0, List.of(row));
    assertTrue(onFx(checkout::getItems).isEmpty());
    deliver(1, List.of(row));
    assertEquals(List.of(row), onFx(checkout::getItems));
    fail(0);
    assertEquals("", onFx(checkoutFeedback::getText));
    fail(1);
    assertEquals(
        "Checkout appointments are temporarily unavailable", onFx(checkoutFeedback::getText));

    loader.refreshReceiptHistory(
        history, preview, "", null, LocalDate.of(2026, 9, 23), receiptFeedback);
    loader.refreshReceiptHistory(
        history, preview, "", null, LocalDate.of(2026, 9, 23), receiptFeedback);
    deliver(2, List.of());
    deliver(3, List.of());
    fail(2);
    assertEquals("", onFx(receiptFeedback::getText));
    fail(3);
    assertEquals("Receipt history is temporarily unavailable", onFx(receiptFeedback::getText));
  }

  @Test
  void appointmentDetailLoadsIgnoreSupersededRequests() throws Exception {
    List<ReceptionistDataLoader.AppointmentDetails> loaded = new ArrayList<>();
    ReceptionistDataLoader.AppointmentDetails first = appointmentDetails(7);
    ReceptionistDataLoader.AppointmentDetails second = appointmentDetails(8);

    loader.loadCheckInDetails(7, loaded::add, failure -> {});
    loader.loadCheckoutDetails(8, loaded::add, failure -> {});
    deliver(0, first);
    assertTrue(loaded.isEmpty());
    deliver(1, second);

    assertEquals(List.of(second), loaded);

    loader.loadCheckInDetails(9, loaded::add, failure -> {});
    loader.invalidateAppointmentDetails();
    deliver(2, appointmentDetails(9));
    assertEquals(List.of(second), loaded);
  }

  @Test
  void scheduleRefreshUpdatesSummaryAndClearsAnEmptySelection() throws Exception {
    TableView<AppointmentListRow> schedule = onFx(TableView::new);
    Label feedback = onFx(Label::new);
    Label summary = onFx(Label::new);
    ReceptionistAppointmentPanel.SelectionState selection =
        new ReceptionistAppointmentPanel.SelectionState();
    selection.appointmentId = 0;
    AppointmentListRow row =
        new AppointmentListRow(
            new Appointment(
                8,
                2,
                3,
                LocalDateTime.of(2026, 9, 23, 11, 0),
                LocalDateTime.of(2026, 9, 23, 11, 30),
                AppointmentStatus.ACCEPTED),
            "Patient",
            "Doctor");

    loader.refreshSchedule(
        schedule,
        selection,
        feedback,
        LocalDate.of(2026, 9, 23),
        null,
        "",
        "All statuses",
        summary);
    loader.refreshSchedule(
        schedule,
        selection,
        feedback,
        LocalDate.of(2026, 9, 23),
        null,
        "",
        "All statuses",
        summary);
    deliver(0, List.of(row));
    assertTrue(onFx(schedule::getItems).isEmpty());
    deliver(1, List.of(row));
    assertEquals(List.of(row), onFx(schedule::getItems));
    assertTrue(onFx(summary::getText).contains("Accepted: 1"));
  }

  private void deliver(int index, Object value) throws Exception {
    onFx(
        () -> {
          submissions.get(index).success().accept(value);
          return null;
        });
  }

  private void fail(int index) throws Exception {
    onFx(
        () -> {
          submissions.get(index).failure().accept(new IllegalStateException("load failed"));
          return null;
        });
  }

  private static Account account(long id, String displayName) {
    return new Account(id, "doctor" + id, displayName, Role.DOCTOR, true);
  }

  private static ReceptionistDataLoader.AppointmentDetails appointmentDetails(long id) {
    Appointment appointment =
        new Appointment(
            id,
            2,
            3,
            LocalDateTime.of(2026, 9, 23, 10, 0),
            LocalDateTime.of(2026, 9, 23, 10, 30),
            AppointmentStatus.COMPLETED);
    return new ReceptionistDataLoader.AppointmentDetails(
        appointment, new Patient(2, "Patient", "Example", "", "555-0100", "", ""), "Doctor");
  }

  private static SearchSuggestionField<Account> newField(String id) throws Exception {
    return onFx(
        () ->
            new SearchSuggestionField<>(id, "Doctor", Account::displayName, Account::displayName));
  }

  private static <T> T onFx(FxOperation<T> operation) {
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
          } catch (AssertionError exception) {
            failure.set(exception);
          } finally {
            completed.countDown();
          }
        });
    try {
      assertTrue(completed.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("JavaFX operation was interrupted", exception);
    }
    if (failure.get() != null) {
      throw new AssertionError("JavaFX operation failed", failure.get());
    }
    return result.get();
  }

  @FunctionalInterface
  private interface FxOperation<T> {
    T run();
  }

  private record PendingSubmission(Consumer<Object> success, Consumer<Throwable> failure) {}
}
