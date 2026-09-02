## MODIFIED Requirements

### Requirement: Registration and patient management use separate tabs

The shared administrative patient area SHALL present one `Patient directory`
page rather than registration and search/manage sub-tabs. By default, the page
SHALL show the patient search controls, patient results, and a `Register new
patient` action at the bottom. Selecting that action SHALL switch the page to a
blank registration view containing the existing patient-registration fields, a
register action, and a cancel action. The registration view SHALL not show the
directory search or patient results. Selecting a patient result SHALL still
open a separate patient-details window containing permitted administrative
details, editing, and activate/deactivate actions. The registration and
details-window forms SHALL use independent controls and state.

#### Scenario: Open the search and manage tab

- **WHEN** an authorized Doctor or Receptionist opens the patient area
- **THEN** the system displays one `Patient directory` page with search
  controls, patient results, and a `Register new patient` action at the bottom
- **AND** the page does not display registration or search/manage sub-tabs

#### Scenario: Open the registration tab

- **WHEN** an authorized Doctor or Receptionist selects `Register new patient`
  from the Patient directory
- **THEN** the system switches to a blank registration form and register action
  without Patient ID, search results, save-changes, or deactivation controls

#### Scenario: Cancel registration

- **WHEN** a user selects `Cancel` from the registration view before submitting
  the form
- **THEN** the system returns to the Patient directory without creating or
  changing a patient

#### Scenario: Return to the directory after successful registration

- **WHEN** an authorized Doctor or Receptionist submits valid registration data
- **THEN** the system creates the patient using the existing validation,
  identity, and generated-ID rules
- **AND** the system returns to the Patient directory and shows refreshed
  patient results and dependent patient selectors

#### Scenario: Keep the registration view after a failed submission

- **WHEN** registration fails validation, identity uniqueness, or persistence
  checks
- **THEN** the system keeps the user on the registration view, displays an
  actionable error, and does not partially create a patient

#### Scenario: Select a search result

- **WHEN** an authorized Doctor or Receptionist selects a patient in the
  directory results
- **THEN** a separate patient-details window opens with the permitted
  administrative fields, generated Patient ID, save action, and an active-
  status action
- **AND** the active-status action reads `Deactivate patient` for an active
  patient and `Activate patient` for an inactive patient
