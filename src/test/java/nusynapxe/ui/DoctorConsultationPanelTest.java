package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
  private final AtomicLong selectionGeneration = new AtomicLong(1);
  private Label feedback;
  private DoctorConsultationPanel panel;

  @Override
  public void start(Stage stage) {
    feedback = new Label();
    panel =
        new DoctorConsultationPanel(
            mock(ClinicServices.class),
            new Session(7, "doctor", Role.DOCTOR),
            feedback,
            taskRunner,
            () -> 42,
            selectionGeneration::get);
    Scene scene =
        new Scene(
            new StackPane(panel.consultationCardView(), panel.prescriptionCardView()), 1200, 760);
    UiComponents.applyStylesheet(scene);
    stage.setScene(scene);
    stage.show();
  }

  @Test
  void disablesConsultationEditsWhileSaveIsQueued() {
    interact(
        () -> {
          panel.diagnosisField().setText("Diagnosis");
          panel.consultationNotesArea().setText("Notes");
          panel.followUpNotesArea().setText("Follow up");
          panel.saveButton().fire();
        });

    assertTrue(panel.saveButton().isDisable());
    assertTrue(panel.diagnosisField().isDisable());
    assertTrue(panel.consultationNotesArea().isDisable());
    assertTrue(panel.followUpNotesArea().isDisable());

    interact(
        () -> taskRunner.submissions.getLast().failure().accept(new RuntimeException("failed")));

    assertFalse(panel.saveButton().isDisable());
    assertFalse(panel.diagnosisField().isDisable());
    assertFalse(panel.consultationNotesArea().isDisable());
    assertFalse(panel.followUpNotesArea().isDisable());
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

  @Test
  void staleConsultationFailureDoesNotDisableOrOverwriteTheNewSelection() {
    interact(
        () -> {
          panel.diagnosisField().setText("Old diagnosis");
          panel.saveButton().fire();
          selectionGeneration.set(2);
          panel.load(null, 2);
          feedback.setText("New appointment selected");
        });

    interact(
        () ->
            taskRunner.submissions.getFirst().failure().accept(new RuntimeException("old error")));

    assertFalse(panel.saveButton().isDisable());
    assertFalse(panel.diagnosisField().isDisable());
    assertEquals("New appointment selected", feedback.getText());
  }

  @Test
  void stalePrescriptionCallbackDoesNotAlterANewerSubmission() {
    interact(
        () -> {
          panel.medicationField().setText("Old medication");
          panel.addPrescriptionButton().fire();
          selectionGeneration.set(2);
          panel.load(null, 2);
          panel.medicationField().setText("New medication");
          panel.addPrescriptionButton().fire();
          feedback.setText("New prescription pending");
        });

    interact(() -> taskRunner.submissions.getFirst().success().accept(null));

    assertTrue(panel.addPrescriptionButton().isDisable());
    assertEquals("New medication", panel.medicationField().getText());
    assertEquals("New prescription pending", feedback.getText());
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
