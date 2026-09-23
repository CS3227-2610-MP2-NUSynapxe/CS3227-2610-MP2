package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

final class DoctorConsultationPanelTest extends ApplicationTest {
  private final CapturingTaskRunner taskRunner = new CapturingTaskRunner();
  private DoctorConsultationPanel panel;

  @Override
  public void start(Stage stage) {
    panel =
        new DoctorConsultationPanel(
            mock(ClinicServices.class),
            new Session(7, "doctor", Role.DOCTOR),
            new Label(),
            taskRunner,
            () -> 42,
            () -> 1);
    stage.setScene(new Scene(new StackPane(panel.prescriptionCardView()), 800, 600));
    stage.show();
  }

  @Test
  void disablesPrescriptionSubmissionUntilTheQueuedRequestFinishes() {
    interact(
        () -> {
          panel.medicationField().setText("Medication");
          panel.dosageField().setText("Dose");
          panel.frequencyField().setText("Daily");
          panel.durationField().setText("7 days");
          panel.instructionsField().setText("Take with food");
          panel.addPrescriptionButton().fire();
        });

    assertTrue(panel.addPrescriptionButton().isDisable());
    assertEquals(1, taskRunner.submissions.size());
    interact(() -> panel.addPrescriptionButton().fire());
    assertEquals(1, taskRunner.submissions.size());

    interact(
        () -> taskRunner.submissions.getFirst().failure().accept(new RuntimeException("failed")));
    assertFalse(panel.addPrescriptionButton().isDisable());
  }

  private static final class CapturingTaskRunner implements ClinicTaskRunner {
    private final List<PendingSubmission> submissions = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void submit(
        ClinicTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
      ClinicTaskRunner.requireCallbacks(task, onSuccess, onFailure);
      submissions.add(new PendingSubmission(task, value -> onSuccess.accept((T) value), onFailure));
    }

    @Override
    public void close() {
      // The test runner does not own external resources.
    }
  }

  private record PendingSubmission(
      ClinicTaskRunner.ClinicTask<?> task, Consumer<Object> success, Consumer<Throwable> failure) {}
}
