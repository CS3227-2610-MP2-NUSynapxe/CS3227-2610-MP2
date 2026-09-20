## 1. Clinical read authorization and history data

- [x] 1.1 Split Doctor clinical read authorization from assigned-Doctor write authorization, allowing authenticated Doctors to read only `COMPLETED` and `CHECKED_OUT` consultations while preserving assigned-Doctor checks for saving notes, adding prescriptions, and all other clinical mutations; verify with focused `ClinicalServiceTest` authorization cases.
- [x] 1.2 Add a clinical-history repository projection that filters by patient, joins the appointment and assigned Doctor context, includes only completed or checked-out appointments, and orders newest records first with a stable appointment-ID tie-breaker; verify with repository tests for filtering, ordering, and empty history.
- [x] 1.3 Add the service/domain projection needed to return each history entry with appointment metadata, Doctor display information, the clinical record, and prescriptions without changing Receptionist-facing administrative projections; verify with service and integration tests for complete data and no in-progress leakage.

## 2. Doctor clinical-history workflow

- [x] 2.1 Create the Doctor-only clinical-history view with patient selection, a compact chronological history list, selected-record detail, explicit empty/loading/error states, and read-only clinical and prescription fields; verify the view exposes stable IDs/accessibility labels and no edit or completion controls.
- [x] 2.2 Integrate the history view into the Doctor Patients destination as a separate clinical state or panel while preserving the existing administrative directory, registration, editing, activation, deactivation, and deletion flows; verify existing patient-directory tests and new Doctor history UI tests.
- [x] 2.3 Add a Dashboard patient-history action that opens the shared history view for the selected patient without changing the existing assigned-Doctor consultation editing workflow; verify Dashboard navigation and cross-Doctor historical selection in `DoctorViewTest` or an equivalent TestFX suite.
- [x] 2.4 Ensure a historical record selected from another Doctor remains read-only and that in-progress records from another Doctor are not displayed or loaded; verify UI behavior together with service-layer authorization tests.

## 3. Documentation and regression verification

- [x] 3.1 Update the User Guide and relevant developer documentation to describe Doctor clinical history, terminal-record visibility, assigned-Doctor editing, and the unchanged Receptionist checkout boundary; verify documentation links and wording against the implemented UI.
- [x] 3.2 Add regression coverage proving any Doctor can read another Doctor's completed/checked-out consultation, cannot read another Doctor's in-progress consultation, and cannot edit another Doctor's clinical data; verify the tests pass through the normal Gradle test task.
- [x] 3.3 Add regression coverage proving Receptionists still receive only administrative patient/appointment and checkout data, with no consultation notes, diagnoses, follow-up notes, or prescriptions; verify existing checkout and confidentiality tests remain passing.
- [x] 3.4 Run the focused clinical, patient-directory, Doctor UI, and workflow integration tests, then run the project quality gate and OpenSpec validation; verify all required checks pass and the change introduces no checkout, receipt, or revenue-report modifications.
