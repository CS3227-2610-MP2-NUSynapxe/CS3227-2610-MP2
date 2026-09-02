## Context

The shared `PatientDirectoryView` currently has a directory state backed by a
`ListView<Patient>`, a separate registration state, and a patient-details
`Stage` opened when a list row is selected. The application already provides
the `compact-selector` CSS treatment for System Admin ComboBoxes, and patient
services already own validation, authorization, relationship checks, and
dependent-selector refresh callbacks.

## Goals / Non-Goals

**Goals:**

- Make registration and editing forms use compact, consistent dropdowns.
- Present searchable patients as a headed administrative table with a fixed
  far-right row action.
- Move ordinary patient editing into a page state that shares the directory
  context and supports explicit cancellation.
- Preserve all existing save, status, deletion, blocked-deletion, privacy, and
  dependent-selector behavior.

**Non-Goals:**

- Change patient service APIs, validation, persistence, schema, authorization,
  or clinical access rules.
- Display diagnoses, consultation notes, prescriptions, or other clinical data
  in the directory or edit page.
- Remove the confirmation or blocked-deletion dialogs required to make
  destructive actions safe.
- Change the Receptionist top-level workspace navigation or Doctor rail.

## Decisions

### Use the existing compact selector style for every patient-form ComboBox

Apply `compact-selector` to identity type, issuing country, date-of-birth, and
sex controls when constructing both registration and edit forms. This reuses
the established 34px System Admin treatment, keeps country/date controls
consistent, and avoids a second CSS sizing convention. Existing listeners for
Singapore identity types, country-derived phone codes, date synchronization,
and age calculation remain unchanged.

### Replace the patient ListView with a TableView and an action column

Use a constrained-width administrative table with these columns: `Patient ID`,
`Name`, `Date of birth`, `Phone`, `Email`, `Status`, and `Actions`. Keep the
patient ID and status readable, keep the action column fixed at the far right,
and retain the existing empty-state message. The table continues to use the
same service search results, so filtering and ordering do not change.

Each populated action cell creates an `Edit` button whose role-prefixed ID also
includes the patient ID. The button is the only normal edit entry point; table
row selection no longer opens a modal. This avoids ambiguous row interactions
and is compatible with JavaFX's virtualized table rows.

### Represent directory, registration, and editing as explicit page states

Extend the existing patient content `StackPane` with an edit state. The
directory state remains the default and contains search, table, and
`Register new patient`. The registration state keeps its existing independent
form and has `Register patient` and `Cancel`. The edit state is populated when
an Edit action is pressed and contains a separate patient form with the
generated ID, `Save patient changes`, activation/deactivation, `Delete patient`,
and `Cancel` actions.

The page title and state IDs identify whether the user is in `Patient directory`,
`Register new patient`, or `Edit patient`. Cancel clears the relevant draft and
returns to the directory. Successful registration or editing refreshes the
table and invokes the existing callback for dependent patient selectors.
Validation and persistence failures retain the active form and its values with
inline and workspace feedback. A successful deletion returns to the directory;
the existing confirmation and blocked-deletion stages remain modal because
those are safety prompts rather than the normal editing workflow.

### Keep patient display data administrative and concise

The table will show only basic administrative values needed for scanning. Full
identity-document numbers and clinical data remain available only in the edit
form, consistent with the current directory privacy boundary. The edit form
continues to use the existing role-prefixed field IDs so Doctor and Receptionist
flows stay aligned.

## Risks / Trade-offs

- [Virtualized action cells can be difficult to exercise] -> Use stable
  role-and-patient IDs, a fixed action column, and TestFX waits/layout steps
  before firing a row action.
- [A wide table can clip values at the minimum window size] -> Use constrained
  column resizing, fixed width for Actions, wrapping or flexible text columns,
  and retain the existing scrollable page container.
- [Moving edit state into the shared view can regress Receptionist and Doctor]
  -> Preserve leaf control IDs, run both focused UI suites, and keep service
  callbacks and deletion-dialog behavior unchanged.
- [A user may leave an edit page with unsaved changes] -> Make `Cancel`
  explicit, discard only the in-memory draft, and return to the refreshed
  directory without invoking a write.

## Migration Plan

No database or deployment migration is required. Rollback is a UI-only code
rollback restoring the ListView and patient-details stage; existing patient
records and service APIs are unaffected.
