package nusynapxe.ui;

import java.util.Objects;
import javafx.scene.control.Label;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Session;
import nusynapxe.service.ClinicServices;

/** Loads active patients for receptionist appointment selectors. */
final class AppointmentPatientSelector {
  private AppointmentPatientSelector() {
    throw new AssertionError("Utility class");
  }

  /** Populates an appointment patient selector with active patients. */
  static void refreshAppointmentPatients(
      ClinicServices services,
      Session session,
      SearchSuggestionField<Patient> selector,
      Label feedback,
      long preferredPatientId) {
    refreshAppointmentPatients(
        services,
        session,
        selector,
        feedback,
        preferredPatientId,
        ClinicTaskRunner.immediate(),
        new SelectorLoadGeneration());
  }

  /** Populates an appointment selector while ignoring superseded refreshes. */
  static void refreshAppointmentPatients(
      ClinicServices services,
      Session session,
      SearchSuggestionField<Patient> selector,
      Label feedback,
      long preferredPatientId,
      ClinicTaskRunner taskRunner) {
    refreshAppointmentPatients(
        services,
        session,
        selector,
        feedback,
        preferredPatientId,
        taskRunner,
        new SelectorLoadGeneration());
  }

  /** Populates an appointment selector with an externally owned freshness tracker. */
  static void refreshAppointmentPatients(
      ClinicServices services,
      Session session,
      SearchSuggestionField<Patient> selector,
      Label feedback,
      long preferredPatientId,
      ClinicTaskRunner taskRunner,
      SelectorLoadGeneration generations) {
    long generation = generations.next(selector);
    Patient previousPatient = selector.getValue();
    taskRunner.submit(
        () ->
            services.patientService().searchAdministrative(session, "").stream()
                .filter(Patient::active)
                .toList(),
        patients -> {
          if (!generations.isCurrent(selector, generation)) {
            return;
          }
          Patient currentPatient = selector.getValue();
          boolean selectionChanged = !samePatient(previousPatient, currentPatient);
          selector.setItems(patients);
          if (selectionChanged) {
            if (currentPatient != null) {
              selectPatient(selector, currentPatient.id());
            }
            return;
          }
          if (!selectPatient(selector, preferredPatientId) && !selector.getItems().isEmpty()) {
            selector.select(selector.getItems().getFirst());
          }
        },
        failure -> {
          if (generations.isCurrent(selector, generation)) {
            UiComponents.showError(feedback, "Patients are temporarily unavailable");
          }
        });
  }

  private static boolean selectPatient(SearchSuggestionField<Patient> selector, long id) {
    for (Patient patient : selector.getItems()) {
      if (patient.id() == id) {
        selector.select(patient);
        return true;
      }
    }
    selector.clearSelection();
    return false;
  }

  private static boolean samePatient(Patient first, Patient second) {
    return Objects.equals(first, second)
        || (first != null && second != null && first.id() == second.id());
  }
}
