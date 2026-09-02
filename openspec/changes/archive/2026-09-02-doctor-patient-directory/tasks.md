## 1. Authorization and service contracts

- [x] 1.1 Add a central patient-administration authorization check that accepts authenticated Doctors and Receptionists while continuing to reject missing sessions and System Admin sessions; verify `AuthorizationTest` and direct `PatientService` authorization tests cover every administrative operation.
- [x] 1.2 Update patient registration, administrative update, search, list, retrieval, activation, and deactivation service methods to use the shared authorization policy without changing validation, identity uniqueness, generated Patient ID, or clinical-ownership rules; verify existing `PatientServiceTest` cases plus Doctor-role cases pass.
- [x] 1.3 Define structured deletion-check data and a service-level blocked-deletion outcome that carries safe relationship categories and counts without exposing clinical contents or complete identity numbers; verify unit tests can distinguish not-found, blocked, unauthorized, and successful outcomes.

## 2. Relationship-safe patient persistence

- [x] 2.1 Add repository deletion-preflight queries for appointments, clinical records, prescriptions reached through clinical records, payments, receipts, and the current schema's other patient-reference paths; verify persistence fixtures return zero blockers for an unused patient and accurate counts for each populated relationship category.
- [x] 2.2 Implement an atomic repository delete operation that repeats the preflight in the final write transaction, deletes only the patient row when no blockers exist, and never deletes child rows; verify successful deletion removes the unused patient while all relationship fixtures remain unchanged when deletion is blocked.
- [x] 2.3 Translate a final SQLite foreign-key violation into the structured blocked-deletion outcome, roll back the transaction, and keep foreign-key enforcement and restrictive delete behavior intact; verify tests assert `PRAGMA foreign_keys` is enabled, no `ON DELETE CASCADE` is introduced, and a failed delete leaves the database unchanged.

## 3. Patient service behavior and booking semantics

- [x] 3.1 Expose deletion preflight and confirmed deletion through `PatientService` for Doctors and Receptionists, preserving the administrative projection and translating persistence failures into user-safe messages; verify service tests cover eligible deletion, each blocker category, stale-state failure, and authorization denial.
- [x] 3.2 Extend activation and deactivation authorization to Doctors while preserving the Patient ID, all appointments, clinical records, prescriptions, payments, receipts, and other history; verify service and integration tests cover both transitions for both authorized roles.
- [x] 3.3 Verify inactive patients remain searchable and viewable in administrative workflows but cannot be selected for new appointment booking, while reactivation restores booking eligibility subject to normal scheduling rules; verify `AppointmentServiceTest` and patient integration tests cover rejection, restoration, and unchanged existing appointments.
- [x] 3.4 Verify Doctor administrative edits cannot read or write clinical fields and do not change clinical or payment history; verify an integration test compares the clinical-record, prescription, payment, and receipt projections before and after a Doctor edit.

## 4. Shared patient-directory interface

- [x] 4.1 Extract the existing Receptionist patient form, search/results state, selected-patient details window, status action, and refresh hooks into a reusable administrative directory component while preserving existing Receptionist labels and TestFX IDs; verify the current Receptionist patient-directory TestFX flows still pass.
- [x] 4.2 Configure the reusable directory for Doctor sessions with independent Doctor-prefixed controls for registration, search, administrative details, edit, activation/deactivation, and deletion; verify a Doctor can create, search, select, and edit a patient through TestFX without clinical controls appearing in the directory.
- [x] 4.3 Add a separate Delete action to the selected-patient details window for both Doctors and Receptionists, require explicit confirmation for an eligible unused patient, and refresh the result list and dependent patient selectors after success; verify TestFX covers canceling confirmation and successful deletion.
- [x] 4.4 Add the owned blocked-deletion modal that lists every relationship category and count, states that the patient and history were preserved, and presents deactivation as the safe alternative without silently changing status; verify TestFX covers appointment, clinical, payment, receipt, prescription, and other-blocker feedback.
- [x] 4.5 Refresh the selected patient and visible directory after validation, authorization, database, or stale-state failures so displayed data matches persistence; verify failed edit/delete flows leave the patient list, details window, and appointment selectors consistent.

## 5. Doctor workspace navigation

- [x] 5.1 Wrap the current Doctor schedule and consultation workspace in a navigation shell with clearly labeled `Dashboard` and `Patients` destinations, preserving the current Dashboard controls and logout behavior; verify `DoctorViewTest` confirms the existing appointment/clinical workflow remains reachable.
- [x] 5.2 Wire the Patients destination to the shared directory, show the active destination distinctly, and refresh directory data when the destination is opened or revisited; verify TestFX can switch destinations, find Doctor Patients controls, and return to the same Dashboard workflow.
- [x] 5.3 Confirm the Doctor Patients destination does not expose clinical-record or prescription controls and that Doctor clinical data remains available only for the assigned appointment workflow; verify UI and service integration tests cover both the administrative view and Doctor ownership checks.

## 6. Documentation and OpenSpec alignment

- [x] 6.1 Update `docs/UserGuide.md` with Doctor Dashboard/Patients navigation, Doctor and Receptionist patient CRUD/search permissions, activation/deactivation meaning, guarded deletion, confirmation, blocked-deletion modal, and the clinical privacy boundary; verify documented labels and actions match the implemented UI.
- [x] 6.2 Update `docs/DeveloperGuide.md` with the shared authorization policy, administrative projection, relationship-count preflight, atomic non-cascading delete, foreign-key fallback, active-status booking rule, and test strategy; verify the guide names the current schema relationships and no migration is claimed.
- [x] 6.3 Record the implementation and verification evidence in the project interaction/development log without including real patient identity or clinical data; verify the log identifies the approved OpenSpec decisions, tests run, and any remaining manual UI limitations.

## 7. Integrated verification and quality gates

- [x] 7.1 Run focused domain, repository, service, authorization, integration, and Doctor/Receptionist TestFX tests and map their evidence to every scenario in both change specs; verify no required scenario is left undocumented.
- [x] 7.2 Run `gradlew.bat spotlessApply` followed by `gradlew.bat check javadoc --no-daemon --console=plain`; inspect generated quality reports and resolve failures attributable to this change.
- [x] 7.3 Run `openspec validate doctor-patient-directory --type change --strict`, review the complete diff for unintended clinical-data exposure, cascading deletion, unrelated files, or stale documentation, and verify the change is ready for implementation.
