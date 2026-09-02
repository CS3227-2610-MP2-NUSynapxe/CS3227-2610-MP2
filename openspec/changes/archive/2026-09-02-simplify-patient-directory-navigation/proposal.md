## Why

The Doctor Patients screen currently presents registration and search/manage as
rounded sub-tabs, while the Doctor navigation buttons look like separate
sub-buttons inside the navigation rail. This makes both areas feel less like a
cohesive workspace and adds an unnecessary navigation step for the common
patient-directory flow.

## What Changes

- Make the Doctor navigation rail a single visual panel whose `Dashboard` and
  `Patients` buttons span its full width, have no gap between them, and retain a
  clear active state without looking like nested cards.
- Replace the shared patient directory's registration and search/manage tabs
  with one default Patient directory view containing the search controls,
  results, and a `Register new patient` action at the bottom.
- Switch the shared directory content to the existing registration form when
  `Register new patient` is selected, with a `Cancel` action returning to the
  directory without changing data.
- Return to the Patient directory after successful registration, refresh the
  results and dependent patient selectors, and keep the user on the form when
  validation or persistence fails.
- Apply the simplified patient-directory flow consistently to Doctors and
  Receptionists while preserving administrative privacy, patient details
  modals, guarded deletion, activation/deactivation, and existing service
  authorization.
- Update TestFX coverage, user/developer documentation, and OpenSpec evidence
  for the revised navigation and page states.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reception-patient-directory`: Replace the separate registration and
  search/manage tabs with a single stateful directory and registration flow for
  the shared administrative patient page.
- `modernize-clinic-ui`: Make Doctor navigation destinations visually occupy
  the complete navigation panel while preserving active-destination feedback
  and resize usability.

## Impact

- `PatientDirectoryView`: replace the internal `TabPane` with directory and
  registration view states, navigation actions, and success/cancel behavior.
- `DoctorView` and `ui.css`: adjust navigation spacing, width, alignment, and
  active-state styling.
- `DoctorViewTest` and `ReceptionistViewTest`: update selectors and add
  directory/register transition and cancellation coverage.
- `docs/UserGuide.md` and `docs/DeveloperGuide.md`: document the single-page
  directory flow and full-width Doctor navigation.
- No service, persistence, schema, dependency, authorization, or clinical-data
  changes are expected.
