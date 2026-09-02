## Why

The patient directory is currently difficult to scan because results are shown as an unstructured list, and editing opens a separate popup that takes the user away from the directory context. Registration fields also use oversized default dropdowns compared with the compact controls already used in System Admin.

## What Changes

- Apply the shared compact selector treatment to patient registration and edit dropdowns.
- Replace the patient result `ListView` with a headed, scannable table of administrative patient data.
- Add a far-right `Edit` action to each patient row.
- Replace the normal patient-details edit popup with an in-page `Edit patient` view that uses the existing administrative fields and actions.
- Add a page-level cancel/back action that discards unsaved edits and returns to the Patient directory.
- Preserve guarded deletion confirmation and blocked-deletion popups because deletion safety feedback remains required.
- Update Doctor and Receptionist TestFX coverage and the user/developer guides.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reception-patient-directory`: change patient result presentation to a table with row-level editing and move normal patient editing into the directory page.
- `modernize-clinic-ui`: make patient dropdowns compact and make the patient directory and edit workflow scannable and page-based.

## Impact

The shared `PatientDirectoryView` and `ui.css` are affected, along with Doctor and Receptionist UI tests and documentation. Existing patient service calls, authorization, validation, persistence, deletion blockers, activation/deactivation behavior, clinical privacy boundaries, and dependent-selector callbacks remain the source of truth and require no API, schema, dependency, or database migration changes.
