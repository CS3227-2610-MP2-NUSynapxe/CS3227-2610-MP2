package nusynapxe.ui;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javafx.scene.control.Label;
import nusynapxe.domain.Account;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/** Loads the data needed by an appointment editor before it is shown. */
final class AppointmentDialogLoader {
  private static final Map<String, AtomicLong> EDITOR_GENERATIONS = new ConcurrentHashMap<>();

  private AppointmentDialogLoader() {
    throw new AssertionError("Utility class");
  }

  /** Loads an existing appointment and opens its editor when the result is still current. */
  static void showEdit(
      ClinicServices services,
      Session session,
      long appointmentId,
      String prefix,
      String title,
      boolean doctorActions,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    Objects.requireNonNull(workspaceActive, "workspaceActive");
    long generation = nextGeneration(prefix);
    taskRunner.submit(
        () -> services.appointmentService().get(appointmentId),
        appointment -> {
          if (workspaceActive.getAsBoolean() && isCurrent(prefix, generation)) {
            showEditor(
                services,
                session,
                appointment,
                appointment.doctorId(),
                appointment.startsAt(),
                prefix,
                title,
                "Reschedule appointment",
                doctorActions,
                workspaceFeedback,
                onUpdated,
                taskRunner,
                workspaceActive);
          }
        },
        failure -> {
          if (workspaceActive.getAsBoolean() && isCurrent(prefix, generation)) {
            workspaceFeedback.setText(message(failure));
          }
        });
  }

  /** Loads patient and Doctor choices, then renders a create or edit dialog. */
  static void showEditor(
      ClinicServices services,
      Session session,
      Appointment appointment,
      long doctorId,
      LocalDateTime initialStart,
      String prefix,
      String title,
      String submitText,
      boolean doctorActions,
      Label workspaceFeedback,
      Runnable onUpdated,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    Objects.requireNonNull(services, "services");
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(workspaceFeedback, "workspaceFeedback");
    Objects.requireNonNull(onUpdated, "onUpdated");
    Objects.requireNonNull(workspaceActive, "workspaceActive");
    long generation = nextGeneration(prefix);
    taskRunner.submit(
        () -> loadData(services, session, appointment, doctorId),
        loaded -> {
          if (workspaceActive.getAsBoolean() && isCurrent(prefix, generation)) {
            AppointmentEditorView.show(
                services,
                session,
                initialStart,
                doctorId,
                prefix,
                title,
                submitText,
                doctorActions,
                workspaceFeedback,
                onUpdated,
                taskRunner,
                workspaceActive,
                generation,
                loaded);
          }
        },
        failure -> {
          if (workspaceActive.getAsBoolean() && isCurrent(prefix, generation)) {
            workspaceFeedback.setText(message(failure));
          }
        });
  }

  static boolean isCurrent(String prefix, long generation) {
    AtomicLong current = EDITOR_GENERATIONS.get(prefix);
    return current != null && current.get() == generation;
  }

  private static long nextGeneration(String prefix) {
    return EDITOR_GENERATIONS
        .computeIfAbsent(prefix, ignored -> new AtomicLong())
        .incrementAndGet();
  }

  private static EditorData loadData(
      ClinicServices services, Session session, Appointment appointment, long doctorId)
      throws Exception {
    Patient currentPatient =
        appointment == null
            ? null
            : services.patientService().getAdministrative(session, appointment.patientId());
    List<Patient> availablePatients =
        services.patientService().searchAdministrative(session, "").stream()
            .filter(
                patient ->
                    patient.active()
                        || currentPatient != null && patient.id() == currentPatient.id())
            .toList();
    String doctorName = doctorName(services, session, doctorId);
    return new EditorData(appointment, currentPatient, availablePatients, doctorName);
  }

  private static String doctorName(ClinicServices services, Session session, long doctorId)
      throws Exception {
    if (session.accountId() == doctorId && session.role() == Role.DOCTOR) {
      return session.username();
    }
    return services.accountService().listDoctors(session).stream()
        .filter(doctor -> doctor.id() == doctorId)
        .map(Account::displayName)
        .findFirst()
        .orElse("Assigned doctor");
  }

  private static String message(Throwable exception) {
    return exception.getMessage() == null
        ? "The appointment operation could not be completed"
        : exception.getMessage();
  }

  record EditorData(
      Appointment appointment,
      Patient currentPatient,
      List<Patient> availablePatients,
      String doctorName) {
    EditorData {
      Objects.requireNonNull(availablePatients, "availablePatients");
      Objects.requireNonNull(doctorName, "doctorName");
    }
  }
}
