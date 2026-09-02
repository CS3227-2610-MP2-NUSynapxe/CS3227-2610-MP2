## 1. Shared patient-directory page states

- [x] 1.1 Replace the shared patient area's internal registration/manage `TabPane` with a single default Patient directory view containing the search controls, results list, and bottom `Register new patient` action; verify Doctor and Receptionist views expose no patient sub-tab headers.
- [x] 1.2 Add the registration view transition and `Cancel` action while retaining independent registration and patient-details form controls; verify entering registration hides directory controls and cancel returns without changing persisted patients.
- [x] 1.3 Handle registration outcomes so success clears the draft and search query, refreshes the directory and dependent selectors, and returns to the directory, while validation or persistence failure stays on the form with feedback; verify these success, cancel, and failure states through TestFX and service-backed assertions.

## 2. Doctor navigation rail presentation

- [x] 2.1 Adjust the Doctor navigation layout and styles so Dashboard and Patients buttons fill the complete panel width, have no gap, and appear as contiguous rail destinations while preserving active, hover, focus, and resize behavior; verify computed layout and active-state assertions in Doctor UI tests.

## 3. Regression coverage and documentation

- [x] 3.1 Update `DoctorViewTest` to cover the default single-page directory, registration transition, cancellation, successful return, failed submission, and absence of patient sub-tabs or clinical controls; verify the focused Doctor TestFX suite passes.
- [x] 3.2 Update `ReceptionistViewTest` for the shared single-page directory flow and ensure existing search, details, status, deletion, and appointment-selector refresh behavior remains covered; verify the focused Receptionist TestFX suite passes.
- [x] 3.3 Update `docs/UserGuide.md` and `docs/DeveloperGuide.md` with the contiguous Doctor navigation rail and single-page patient-directory registration flow; verify labels and actions match the implemented UI states.
- [x] 3.4 Run `spotlessApply`, the focused and full test/quality gates, and strict OpenSpec validation; verify no service, persistence, schema, authorization, clinical-workflow, or dependency changes were introduced outside the approved UI/documentation scope.
