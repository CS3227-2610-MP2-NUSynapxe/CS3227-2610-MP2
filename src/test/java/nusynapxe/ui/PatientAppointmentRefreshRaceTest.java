package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class PatientAppointmentRefreshRaceTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private SearchSuggestionField<Patient> selector;

  @Override
  public void start(Stage stage) {
    selector = PatientDirectoryView.patientSearchField("reception-appointment-patient");
    Scene scene = new Scene(new StackPane(selector), 1200, 760);
    UiComponents.applyStylesheet(scene);
    stage.setScene(scene);
    stage.show();
  }

  @Test
  void patientRefreshPreservesASelectionMadeWhileTheLoadWasQueued() {
    Patient firstPatient = patient(1, "First");
    Patient secondPatient = patient(2, "Second");
    interact(
        () -> {
          selector.setItems(List.of(firstPatient, secondPatient));
          selector.select(firstPatient);
        });

    interact(
        () ->
            AppointmentPatientSelector.refreshAppointmentPatients(
                mock(ClinicServices.class),
                new Session(7, "reception", Role.RECEPTIONIST),
                selector,
                new Label(),
                firstPatient.id(),
                taskRunner));
    interact(() -> selector.select(secondPatient));
    succeed(0, List.of(firstPatient, secondPatient));

    assertEquals(secondPatient, selectedPatient());
  }

  private Patient selectedPatient() {
    AtomicReference<Patient> selected = new AtomicReference<>();
    interact(() -> selected.set(selector.getValue()));
    return selected.get();
  }

  private void succeed(int index, Object value) {
    interact(() -> taskRunner.submissions.get(index).success().accept(value));
  }

  private static Patient patient(long id, String firstName) {
    return new Patient(id, firstName, "Patient", "", "555-0100", "", "");
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<PendingSubmission> submissions = new ArrayList<>();

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
