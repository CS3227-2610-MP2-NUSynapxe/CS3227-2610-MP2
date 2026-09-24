package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import nusynapxe.domain.Account;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class ReceptionistCalendarRefreshRaceTest extends ApplicationTest {
  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-09-23T02:00:00Z"), ZoneId.of("Asia/Singapore"));

  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private final Account firstDoctor = account(1, "First Doctor");
  private final Account secondDoctor = account(2, "Second Doctor");
  private ReceptionistCalendarView calendar;
  private SearchSuggestionField<Account> doctor;

  @Override
  @SuppressWarnings("unchecked")
  public void start(Stage stage) {
    calendar =
        new ReceptionistCalendarView(
            mock(ClinicServices.class),
            new Session(7, "reception", Role.RECEPTIONIST),
            new Label(),
            (ignoredDoctor, ignoredStart) -> {},
            ignoredAppointment -> {},
            TEST_CLOCK,
            taskRunner);
    TextField editor = (TextField) calendar.view().lookup("#reception-calendar-doctor");
    doctor = (SearchSuggestionField<Account>) editor.getParent();
    Scene scene = new Scene(new StackPane(calendar.view()), 1200, 760);
    UiComponents.applyStylesheet(scene);
    stage.setScene(scene);
    stage.show();
  }

  @AfterEach
  void tearDown() {
    taskRunner.close();
  }

  @Test
  void doctorRefreshPreservesSelectionMadeWhileTheLoadWasQueued() {
    int latestDoctorSubmission = taskRunner.submissions.size() - 1;
    succeed(latestDoctorSubmission, List.of(firstDoctor, secondDoctor));
    interact(() -> doctor.select(secondDoctor));

    int doctorRefreshSubmission = taskRunner.submissions.size();
    interact(calendar::refreshDoctors);
    interact(() -> doctor.select(firstDoctor));
    succeed(doctorRefreshSubmission, List.of(firstDoctor, secondDoctor));

    AtomicReference<Account> selected = new AtomicReference<>();
    interact(() -> selected.set(doctor.getValue()));
    assertEquals(firstDoctor, selected.get());
  }

  @Test
  void calendarDisablesPreviousGridWhileLoadingAnotherDoctorAndRestoresOnSuccess() {
    int doctorSubmission = taskRunner.submissions.size() - 1;
    succeed(doctorSubmission, List.of(firstDoctor, secondDoctor));

    int firstCalendarSubmission = taskRunner.submissions.size() - 1;
    DoctorCalendarWeek weekData =
        new DoctorCalendarWeek(
            firstDoctor.id(),
            LocalDate.now(TEST_CLOCK),
            DoctorCalendarSettings.defaults(firstDoctor.id()),
            List.of(),
            List.of());
    succeed(firstCalendarSubmission, weekData);

    BorderPane root = (BorderPane) calendar.view().lookup("#reception-calendar-card");
    Node firstGrid = root.getCenter();
    assertEquals("reception-calendar-time-grid", firstGrid.getId());
    assertFalse(firstGrid.isDisable());

    interact(() -> doctor.select(secondDoctor));
    assertTrue(firstGrid.isDisable());

    int secondCalendarSubmission = taskRunner.submissions.size() - 1;
    DoctorCalendarWeek secondWeekData =
        new DoctorCalendarWeek(
            secondDoctor.id(),
            LocalDate.now(TEST_CLOCK),
            DoctorCalendarSettings.defaults(secondDoctor.id()),
            List.of(),
            List.of());
    succeed(secondCalendarSubmission, secondWeekData);

    Node secondGrid = root.getCenter();
    assertEquals("reception-calendar-time-grid", secondGrid.getId());
    assertFalse(secondGrid.isDisable());
  }

  @Test
  void calendarRefreshFailureReplacesStaleGridWithUnavailableState() {
    int doctorSubmission = taskRunner.submissions.size() - 1;
    succeed(doctorSubmission, List.of(firstDoctor, secondDoctor));

    int firstCalendarSubmission = taskRunner.submissions.size() - 1;
    DoctorCalendarWeek weekData =
        new DoctorCalendarWeek(
            firstDoctor.id(),
            LocalDate.now(TEST_CLOCK),
            DoctorCalendarSettings.defaults(firstDoctor.id()),
            List.of(),
            List.of());
    succeed(firstCalendarSubmission, weekData);

    BorderPane root = (BorderPane) calendar.view().lookup("#reception-calendar-card");
    assertEquals("reception-calendar-time-grid", root.getCenter().getId());

    interact(() -> doctor.select(secondDoctor));
    assertTrue(root.getCenter().isDisable());

    int secondCalendarSubmission = taskRunner.submissions.size() - 1;
    interact(
        () ->
            taskRunner
                .submissions
                .get(secondCalendarSubmission)
                .failure()
                .accept(new RuntimeException("database error")));

    assertEquals("reception-calendar-unavailable", root.getCenter().getId());
  }

  private void succeed(int index, Object value) {
    interact(() -> taskRunner.submissions.get(index).success().accept(value));
  }

  private static Account account(long id, String displayName) {
    return new Account(id, "doctor" + id, displayName, Role.DOCTOR, true);
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<PendingSubmission> submissions = new java.util.ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(value -> onSuccess.accept((T) value), onFailure));
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }

  private record PendingSubmission(Consumer<Object> success, Consumer<Throwable> failure) {}
}
