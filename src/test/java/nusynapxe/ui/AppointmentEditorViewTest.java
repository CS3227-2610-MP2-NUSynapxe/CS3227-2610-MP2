package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.AccountService;
import nusynapxe.service.AppointmentService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.PatientService;
import nusynapxe.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class AppointmentEditorViewTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private ClinicServices services;
  private AppointmentService appointmentService;
  private PatientService patientService;
  private AccountService accountService;
  private Patient patient;
  private Appointment appointment;

  @Override
  public void start(Stage stage) throws Exception {
    patient =
        new Patient(10, "John", "Doe", "S1234567A", "555-0100", "john@example.com", "Main St");
    appointment =
        new Appointment(
            100,
            10,
            1,
            LocalDateTime.of(2026, 9, 23, 10, 0),
            LocalDateTime.of(2026, 9, 23, 10, 30),
            AppointmentStatus.PENDING);

    services = mock(ClinicServices.class);
    appointmentService = mock(AppointmentService.class);
    patientService = mock(PatientService.class);
    accountService = mock(AccountService.class);

    when(services.appointmentService()).thenReturn(appointmentService);
    when(services.patientService()).thenReturn(patientService);
    when(services.accountService()).thenReturn(accountService);

    when(patientService.getAdministrative(any(), anyLong())).thenReturn(patient);
    when(patientService.searchAdministrative(any(), anyString())).thenReturn(List.of(patient));
    when(accountService.listDoctors(any()))
        .thenReturn(List.of(new Account(1, "doc", "Dr. Smith", Role.DOCTOR, true)));

    stage.setScene(new Scene(new StackPane(), 400, 300));
    stage.show();
  }

  @Test
  void submittingAppointmentDisablesControlsAndRefreshesEvenIfDialogClosed() throws Exception {
    Label workspaceFeedback = new Label();
    AtomicBoolean updated = new AtomicBoolean(false);
    Session session = new Session(1, "doc", Role.DOCTOR);

    interact(
        () ->
            AppointmentDialogLoader.showEditor(
                services,
                session,
                null,
                1,
                LocalDateTime.of(2026, 9, 23, 10, 0),
                "test-appt-create",
                "New Appointment",
                "Create",
                true,
                workspaceFeedback,
                () -> updated.set(true),
                taskRunner,
                () -> true));

    // Initial load task
    assertEquals(1, taskRunner.submissions.size());
    interact(() -> taskRunner.runSubmission(0));

    Button submit = lookup("#test-appt-create-submit").queryAs(Button.class);
    Button cancel = lookup("#test-appt-create-cancel").queryAs(Button.class);
    Stage dialog = (Stage) submit.getScene().getWindow();

    // Fire submit
    interact(submit::fire);

    // Controls must be busy/disabled
    assertTrue(submit.isDisable());
    assertTrue(cancel.isDisable());

    // Window close request while busy must be consumed
    WindowEvent closeRequest = new WindowEvent(dialog, WindowEvent.WINDOW_CLOSE_REQUEST);
    interact(() -> dialog.fireEvent(closeRequest));
    assertTrue(closeRequest.isConsumed());

    // User hides dialog directly or it closes
    interact(dialog::hide);
    assertFalse(updated.get());

    // Mutation task finishes
    interact(() -> taskRunner.runSubmission(1));

    assertTrue(updated.get());
    assertEquals("Appointment created and accepted", workspaceFeedback.getText());
    verify(appointmentService).book(any(), anyLong(), anyLong(), any(), any());
  }

  @Test
  void cancelAppointmentButtonDisablesWhileSubmittingAndRefreshes() throws Exception {
    Label workspaceFeedback = new Label();
    AtomicBoolean updated = new AtomicBoolean(false);
    Session session = new Session(1, "doc", Role.DOCTOR);

    interact(
        () ->
            AppointmentDialogLoader.showEditor(
                services,
                session,
                appointment,
                1,
                LocalDateTime.of(2026, 9, 23, 10, 0),
                "test-appt-cancel",
                "Edit Appointment",
                "Save",
                true,
                workspaceFeedback,
                () -> updated.set(true),
                taskRunner,
                () -> true));

    interact(() -> taskRunner.runSubmission(0));

    Button cancel = lookup("#test-appt-cancel-cancel").queryAs(Button.class);
    interact(cancel::fire);

    assertTrue(cancel.isDisable());

    interact(() -> taskRunner.runSubmission(1));
    assertTrue(updated.get());
    assertEquals("Appointment cancelled", workspaceFeedback.getText());
    verify(appointmentService).cancel(any(), anyLong());
  }

  @Test
  void acceptAndDeclineActionsUpdateFeedbackAndRefresh() throws Exception {
    Label workspaceFeedback = new Label();
    AtomicBoolean updated = new AtomicBoolean(false);
    Session session = new Session(1, "doc", Role.DOCTOR);

    interact(
        () ->
            AppointmentDialogLoader.showEditor(
                services,
                session,
                appointment,
                1,
                LocalDateTime.of(2026, 9, 23, 10, 0),
                "test-appt-decision",
                "Edit Appointment",
                "Save",
                true,
                workspaceFeedback,
                () -> updated.set(true),
                taskRunner,
                () -> true));

    interact(() -> taskRunner.runSubmission(0));

    Button accept = lookup("#test-appt-decision-accept").queryAs(Button.class);
    interact(accept::fire);
    assertTrue(accept.isDisable());
    interact(() -> taskRunner.runSubmission(1));
    assertEquals("Appointment accepted", workspaceFeedback.getText());
    assertTrue(updated.get());

    // Test decline on another instance
    AtomicBoolean declinedUpdated = new AtomicBoolean(false);
    interact(
        () ->
            AppointmentDialogLoader.showEditor(
                services,
                session,
                appointment,
                1,
                LocalDateTime.of(2026, 9, 23, 10, 0),
                "test-appt-decline",
                "Edit Appointment",
                "Save",
                true,
                workspaceFeedback,
                () -> declinedUpdated.set(true),
                taskRunner,
                () -> true));

    interact(() -> taskRunner.runSubmission(2));

    Button decline = lookup("#test-appt-decline-decline").queryAs(Button.class);
    interact(decline::fire);
    assertTrue(decline.isDisable());
    interact(() -> taskRunner.runSubmission(3));
    assertEquals("Appointment declined", workspaceFeedback.getText());
    assertTrue(declinedUpdated.get());
  }

  @Test
  void operationFailureReenablesControlsAndShowsError() throws Exception {
    Label workspaceFeedback = new Label();
    Session session = new Session(1, "doc", Role.DOCTOR);

    interact(
        () ->
            AppointmentDialogLoader.showEditor(
                services,
                session,
                null,
                1,
                LocalDateTime.of(2026, 9, 23, 10, 0),
                "test-appt-fail",
                "New Appointment",
                "Create",
                false,
                workspaceFeedback,
                () -> {},
                taskRunner,
                () -> true));

    interact(() -> taskRunner.runSubmission(0));

    Button submit = lookup("#test-appt-fail-submit").queryAs(Button.class);
    Label feedback = lookup("#test-appt-fail-feedback").queryAs(Label.class);

    interact(submit::fire);
    assertTrue(submit.isDisable());

    // Task fails
    interact(
        () ->
            taskRunner.failSubmission(1, new ValidationException("Time slot is already occupied")));

    assertFalse(submit.isDisable());
    assertTrue(feedback.isVisible());
    assertEquals("Time slot is already occupied", feedback.getText());
  }

  @Test
  void existingAppointmentDisplaysDisabledDoctorName() throws Exception {
    Session receptionist = new Session(2, "reception", Role.RECEPTIONIST);
    when(accountService.getDoctor(receptionist, 1))
        .thenReturn(new Account(1, "doc", "Dr. Historical", Role.DOCTOR, false));

    interact(
        () ->
            AppointmentDialogLoader.showEditor(
                services,
                receptionist,
                appointment,
                1,
                appointment.startsAt(),
                "test-disabled-doctor",
                "Edit Appointment",
                "Save",
                false,
                new Label(),
                () -> {},
                taskRunner,
                () -> true));
    interact(() -> taskRunner.runSubmission(0));

    Label assignedDoctor = lookup("#test-disabled-doctor-doctor").queryAs(Label.class);
    assertEquals("Doctor: Dr. Historical", assignedDoctor.getText());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final ClinicTaskRunner immediateRunner = ClinicTaskRunner.immediate();
    private final List<PendingSubmission> submissions = new ArrayList<>();

    @Override
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(
          new PendingSubmission(
              () -> immediateRunner.submit(task, onSuccess, onFailure), onFailure));
    }

    void runSubmission(int index) {
      submissions.get(index).execution().run();
    }

    void failSubmission(int index, Throwable t) {
      submissions.get(index).failure().accept(t);
    }

    @Override
    public void close() {
      immediateRunner.close();
    }
  }

  private record PendingSubmission(Runnable execution, Consumer<Throwable> failure) {}
}
