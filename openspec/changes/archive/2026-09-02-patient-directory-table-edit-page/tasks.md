## 1. Patient form and directory presentation

- [x] 1.1 Apply the shared compact selector styling to every patient registration and edit dropdown, preserving identity-country, date, age, and phone behavior; verify the compact controls render in the focused UI tests.
- [x] 1.2 Replace the shared patient result `ListView` with a constrained administrative `TableView` containing Patient ID, Name, Date of birth, Phone, Email, Status, and Actions headers; retain the empty state and search behavior, and verify the table structure and row values with TestFX.
- [x] 1.3 Add a fixed far-right `Edit` action cell with stable role-and-patient selectors and ensure normal row selection no longer opens an edit popup; verify each populated row exposes its action.

## 2. In-page patient editing

- [x] 2.1 Add an explicit Edit patient page state reached from a row's Edit action, using the existing populated administrative fields and generated Patient ID; verify the directory is replaced by the edit view without opening the old details window.
- [x] 2.2 Move save, activate/deactivate, delete, and cancel behavior to the edit page, preserving service validation, inline/workspace feedback, dependent-selector refresh, and guarded deletion popups; verify successful save/cancel/failure/status/deletion outcomes.
- [x] 2.3 Keep registration as an independent page state with its current success/failure/cancel behavior and compact controls; verify switching between registration and editing does not mix drafts or expose clinical fields.

## 3. Regression coverage and documentation

- [x] 3.1 Update `DoctorViewTest` for compact patient selectors, table headers/rows, row-level Edit navigation, in-page edit outcomes, and the absence of the normal details popup; verify the focused Doctor TestFX suite passes.
- [x] 3.2 Update `ReceptionistViewTest` for the shared table/edit-page flow while retaining search, registration, status, deletion, and appointment-selector refresh coverage; verify the focused Receptionist TestFX suite passes.
- [x] 3.3 Update `docs/UserGuide.md` and `docs/DeveloperGuide.md` with the table columns, row Edit action, compact form controls, and in-page edit workflow; verify labels and selectors match the implementation.
- [x] 3.4 Run `spotlessApply`, the focused and full quality gates, and strict OpenSpec validation; verify the diff remains limited to the approved UI, tests, documentation, and OpenSpec artifacts with no service, persistence, schema, authorization, clinical, or dependency changes.
