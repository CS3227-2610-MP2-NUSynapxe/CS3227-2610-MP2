package nusynapxe.ui;

import java.time.Clock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import nusynapxe.domain.Patient;
import nusynapxe.domain.PatientDeletionBlockers;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.PatientDeletionBlockedException;
import nusynapxe.service.ValidationException;

/** Composes the selected-patient details, status, deletion, and edit flows. */
final class PatientDirectoryPatientView {
  private PatientDirectoryPatientView() {
    throw new AssertionError("Utility class");
  }

  static void showView(
      String prefix,
      Patient selected,
      VBox viewingContent,
      ClinicServices services,
      nusynapxe.domain.Session session,
      Label workspaceFeedback,
      LongConsumer onPatientChanged,
      ClinicTaskRunner taskRunner,
      BooleanSupplier directoryActive,
      BooleanSupplier viewActive,
      Runnable refresh,
      Runnable showDirectory,
      Consumer<Patient> showEdit,
      Consumer<Patient> showView) {
    Patient[] current = {selected};
    Label feedback = new Label();
    feedback.setId(prefix + "-patient-view-feedback");
    Button edit = button("Edit", prefix + "-patient-edit");
    Button status = button(patientStatusButtonText(selected), prefix + "-patient-deactivate");
    Button delete = UiComponents.dangerButton("Delete patient", prefix + "-patient-delete");
    Button back = UiComponents.secondaryButton("← Back to patients", prefix + "-patient-view-back");

    edit.setOnAction(event -> showEdit.accept(current[0]));
    status.setOnAction(
        event -> {
          long patientId = current[0].id();
          boolean active = current[0].active();
          status.setDisable(true);
          submitStatusChange(
              current,
              patientId,
              active,
              status,
              services,
              session,
              taskRunner,
              workspaceFeedback,
              feedback,
              onPatientChanged,
              viewActive,
              refresh,
              showView);
        });
    delete.setOnAction(
        event ->
            deletePatient(
                prefix,
                current[0],
                viewingContent,
                services,
                session,
                taskRunner,
                workspaceFeedback,
                feedback,
                onPatientChanged,
                directoryActive,
                refresh,
                showDirectory));
    back.setOnAction(
        event -> {
          viewingContent.getChildren().clear();
          showDirectory.run();
          refresh.run();
        });

    HBox actions = UiComponents.actionBar(edit, status, delete);
    viewingContent.setPadding(Insets.EMPTY);
    VBox detailsCard =
        UiComponents.card(
            prefix + "-patient-details-card",
            PatientDirectoryTableView.details(selected),
            actions,
            feedback);
    viewingContent.getChildren().setAll(back, detailsCard);
  }

  static void showEdit(
      String prefix,
      Patient selected,
      VBox editingContent,
      Clock clock,
      ClinicServices services,
      nusynapxe.domain.Session session,
      Label workspaceFeedback,
      LongConsumer onPatientChanged,
      ClinicTaskRunner taskRunner,
      Runnable refresh,
      Runnable showEditing,
      Consumer<Patient> showView) {
    PatientDirectoryFormView.PatientForm form =
        PatientDirectoryFormView.createForm(prefix + "-patient", false, clock);
    PatientDirectoryFormView.populate(selected, form);
    Patient[] current = {selected};
    Label feedback = new Label();
    feedback.setId(prefix + "-patient-edit-feedback");
    Button update = button("Save", prefix + "-patient-update");
    Button cancel = button("Discard changes", prefix + "-patient-edit-cancel");
    boolean[] editActive = {true};

    update.setOnAction(
        event -> {
          try {
            Patient draft =
                PatientDirectoryFormView.fromForm(form, current[0].id(), current[0].active());
            update.setDisable(true);
            taskRunner.submit(
                () -> services.patientService().updateAdministrative(session, draft),
                updated -> {
                  if (!editActive[0] || !editingContent.isVisible()) {
                    refresh.run();
                    return;
                  }
                  current[0] = updated;
                  PatientDirectoryFormView.populate(updated, form);
                  UiComponents.showMessage(feedback, "Patient changes saved");
                  UiComponents.showMessage(workspaceFeedback, "Patient changes saved");
                  refresh.run();
                  onPatientChanged.accept(updated.id());
                  editingContent.getChildren().clear();
                  showView.accept(updated);
                },
                failure -> {
                  if (editActive[0] && editingContent.isVisible()) {
                    update.setDisable(false);
                    showTaskError(feedback, failure, "Patient update is temporarily unavailable");
                  }
                });
          } catch (ValidationException exception) {
            UiComponents.showError(feedback, exception.getMessage());
          }
        });
    cancel.setOnAction(
        event -> {
          editActive[0] = false;
          PatientDirectoryFormView.clear(form);
          editingContent.getChildren().clear();
          showView.accept(current[0]);
        });

    HBox actions = new HBox(8, update, cancel);
    actions.setAlignment(Pos.CENTER_RIGHT);
    editingContent.setPadding(Insets.EMPTY);
    editingContent
        .getChildren()
        .setAll(
            UiComponents.card(
                prefix + "-patient-edit-card",
                PatientDirectoryFormView.grid(form, false),
                actions,
                feedback));
    showEditing.run();
  }

  private static void submitStatusChange(
      Patient[] current,
      long patientId,
      boolean active,
      Button status,
      ClinicServices services,
      nusynapxe.domain.Session session,
      ClinicTaskRunner taskRunner,
      Label workspaceFeedback,
      Label feedback,
      LongConsumer onPatientChanged,
      BooleanSupplier viewActive,
      Runnable refresh,
      Consumer<Patient> showView) {
    try {
      taskRunner.submit(
          () ->
              active
                  ? services.patientService().deactivateAdministrative(session, patientId)
                  : services.patientService().activateAdministrative(session, patientId),
          updated -> {
            if (!viewActive.getAsBoolean()) {
              refresh.run();
              return;
            }
            status.setDisable(false);
            UiComponents.showMessage(
                workspaceFeedback, updated.active() ? "Patient activated" : "Patient deactivated");
            current[0] = updated;
            onPatientChanged.accept(updated.id());
            refresh.run();
            showView.accept(updated);
          },
          failure -> {
            if (viewActive.getAsBoolean()) {
              status.setDisable(false);
              showTaskError(feedback, failure, "Patient status update is temporarily unavailable");
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException exception) {
      if (viewActive.getAsBoolean()) {
        status.setDisable(false);
        showTaskError(feedback, exception, "Patient status update is temporarily unavailable");
      }
    }
  }

  private static void deletePatient(
      String prefix,
      Patient patient,
      VBox viewingContent,
      ClinicServices services,
      nusynapxe.domain.Session session,
      ClinicTaskRunner taskRunner,
      Label workspaceFeedback,
      Label feedback,
      LongConsumer onPatientChanged,
      BooleanSupplier directoryActive,
      Runnable refresh,
      Runnable showDirectory) {
    Stage owner = (Stage) viewingContent.getScene().getWindow();
    if (!directoryActive.getAsBoolean()) {
      return;
    }
    taskRunner.submit(
        () -> services.patientService().deletionBlockers(session, patient.id()),
        blockers -> {
          if (!directoryActive.getAsBoolean()) {
            return;
          }
          if (!blockers.canDelete()) {
            showBlockedDeletionDialog(prefix, owner, blockers);
            return;
          }
          if (!confirmDeletion(prefix, owner, patient)) {
            return;
          }
          if (!directoryActive.getAsBoolean()) {
            return;
          }
          taskRunner.submit(
              () -> {
                services.patientService().deleteAdministrative(session, patient.id());
                return null;
              },
              ignored -> {
                if (!directoryActive.getAsBoolean()) {
                  return;
                }
                UiComponents.showMessage(workspaceFeedback, "Patient deleted");
                viewingContent.getChildren().clear();
                showDirectory.run();
                refresh.run();
                onPatientChanged.accept(0);
              },
              failure ->
                  showDeletionErrorIfActive(
                      directoryActive,
                      prefix,
                      owner,
                      feedback,
                      failure,
                      "Patient deletion is temporarily unavailable"));
        },
        failure ->
            showDeletionErrorIfActive(
                directoryActive,
                prefix,
                owner,
                feedback,
                failure,
                "Patient deletion is temporarily unavailable"));
  }

  private static void showDeletionErrorIfActive(
      BooleanSupplier directoryActive,
      String prefix,
      Stage owner,
      Label feedback,
      Throwable failure,
      String fallback) {
    if (directoryActive.getAsBoolean()) {
      showDeletionError(prefix, owner, feedback, failure, fallback);
    }
  }

  private static void showDeletionError(
      String prefix, Stage owner, Label feedback, Throwable failure, String fallback) {
    if (failure instanceof PatientDeletionBlockedException blocked) {
      showBlockedDeletionDialog(prefix, owner, blocked.blockers());
    } else {
      showTaskError(feedback, failure, fallback);
    }
  }

  private static boolean confirmDeletion(String prefix, Stage owner, Patient patient) {
    Label warning =
        new Label(
            "Delete "
                + patient.displayedId()
                + " permanently? This patient has no related clinic data, and this action cannot be undone.");
    warning.setWrapText(true);
    Button confirm =
        UiComponents.dangerButton("Delete permanently", prefix + "-patient-delete-confirm");
    Button cancel = UiComponents.secondaryButton("Cancel", prefix + "-patient-delete-cancel");
    boolean[] confirmed = {false};
    Stage dialog = new Stage();
    confirm.setOnAction(
        event -> {
          confirmed[0] = true;
          dialog.close();
        });
    cancel.setOnAction(event -> dialog.close());
    VBox content = new VBox(12, warning, UiComponents.actionBar(confirm, cancel));
    content.setId(prefix + "-patient-delete-confirm-window");
    content.setPadding(new Insets(18));
    dialog.initOwner(owner);
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Confirm patient deletion");
    dialog.setScene(new Scene(content, 460, 190));
    dialog.showAndWait();
    return confirmed[0];
  }

  private static void showBlockedDeletionDialog(
      String prefix, Stage owner, PatientDeletionBlockers blockers) {
    Label explanation =
        new Label(
            "This patient cannot be deleted because related clinic data exists. The patient and its history were preserved.");
    explanation.setId(prefix + "-patient-delete-blocked-explanation");
    explanation.setWrapText(true);
    VBox categories = new VBox(6);
    categories.setId(prefix + "-patient-delete-blocked-categories");
    for (PatientDeletionBlockers.BlockingRelation relation : blockers.blockingRelations()) {
      Label category = new Label(relation.label() + ": " + relation.count());
      category.setId(prefix + "-patient-delete-blocked-" + slug(relation.label()));
      categories.getChildren().add(category);
    }
    Label alternative =
        new Label(
            "To retain the history and prevent new bookings, deactivate the patient instead.");
    alternative.setId(prefix + "-patient-delete-blocked-alternative");
    alternative.setWrapText(true);
    Button close = UiComponents.secondaryButton("Close", prefix + "-patient-delete-blocked-close");
    Stage dialog = new Stage();
    close.setOnAction(event -> dialog.close());
    VBox content = new VBox(12, explanation, categories, alternative, close);
    content.setId(prefix + "-patient-delete-blocked-window");
    content.setPadding(new Insets(18));
    dialog.initOwner(owner);
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Patient cannot be deleted");
    dialog.setScene(new Scene(content, 500, 320));
    dialog.showAndWait();
  }

  private static String patientStatusButtonText(Patient patient) {
    return patient.active() ? "Deactivate patient" : "Activate patient";
  }

  private static String slug(String label) {
    return label.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
  }

  private static void showTaskError(Label feedback, Throwable failure, String fallback) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, fallback);
    }
  }

  private static Button button(String label, String id) {
    Button button = new Button(label);
    button.setId(id);
    return button;
  }
}
