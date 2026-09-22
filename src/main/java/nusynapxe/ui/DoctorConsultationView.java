package nusynapxe.ui;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.ClinicalRecord;
import nusynapxe.domain.Prescription;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Loads and presents the editable Doctor consultation projection. */
final class DoctorConsultationView {
  private DoctorConsultationView() {
    throw new AssertionError("Utility class");
  }

  static boolean loadClinical(
      ClinicServices services,
      Session session,
      Appointment appointment,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Label feedback) {
    if (appointment == null) {
      clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
      return true;
    }
    clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
    try {
      Optional<ClinicalRecord> record =
          services.clinicalService().findForDoctor(session, appointment.id());
      if (record.isEmpty()) {
        return true;
      }
      ClinicalRecord value = record.orElseThrow();
      diagnosis.setText(value.diagnosis());
      consultationNotes.setText(value.consultationNotes());
      followUpNotes.setText(value.followUpNotes());
      prescriptions.setItems(
          FXCollections.observableArrayList(
              services.clinicalService().prescriptionsForDoctor(session, appointment.id())));
      return true;
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback,
          exception.getMessage() == null
              ? "Clinical information is temporarily unavailable"
              : exception.getMessage());
      return false;
    }
  }

  static void clearClinical(
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions) {
    diagnosis.clear();
    consultationNotes.clear();
    followUpNotes.clear();
    prescriptions.setItems(FXCollections.observableArrayList());
  }

  static void loadClinicalAsync(
      ClinicServices services,
      Session session,
      Appointment appointment,
      TextField diagnosis,
      TextArea consultationNotes,
      TextArea followUpNotes,
      ListView<Prescription> prescriptions,
      Label feedback,
      ClinicTaskRunner taskRunner,
      Consumer<Boolean> onComplete) {
    if (appointment == null) {
      clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
      onComplete.accept(true);
      return;
    }
    taskRunner.submit(
        () -> {
          Optional<ClinicalRecord> record =
              services.clinicalService().findForDoctor(session, appointment.id());
          List<Prescription> values =
              record.isEmpty()
                  ? List.of()
                  : services.clinicalService().prescriptionsForDoctor(session, appointment.id());
          return new ClinicalProjection(record, values);
        },
        projection -> {
          clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
          projection
              .record()
              .ifPresent(
                  value -> {
                    diagnosis.setText(value.diagnosis());
                    consultationNotes.setText(value.consultationNotes());
                    followUpNotes.setText(value.followUpNotes());
                    prescriptions.setItems(
                        FXCollections.observableArrayList(projection.prescriptions()));
                  });
          onComplete.accept(true);
        },
        failure -> {
          clearClinical(diagnosis, consultationNotes, followUpNotes, prescriptions);
          UiComponents.showError(
              feedback,
              failure.getMessage() == null
                  ? "Clinical information is temporarily unavailable"
                  : failure.getMessage());
          onComplete.accept(false);
        });
  }

  private record ClinicalProjection(
      Optional<ClinicalRecord> record, List<Prescription> prescriptions) {
    private ClinicalProjection {
      Objects.requireNonNull(record, "record");
      Objects.requireNonNull(prescriptions, "prescriptions");
    }
  }
}
