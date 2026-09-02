## MODIFIED Requirements

### Requirement: Receptionists can create, view, and edit basic patient data

The system SHALL allow an authenticated Receptionist to register, search,
view, and edit a patient's identity type, identity number, issuing country,
name, date of birth, sex, phone country code, phone number, email, address,
height, and weight. All fields except height and weight SHALL be required and
marked with `*`. Sex SHALL be either `FEMALE` or `MALE`. Patient billing
information SHALL not be collected or stored; appointment payment and checkout
records remain separate. An update SHALL either persist all validated changes
or leave the existing patient unchanged. Patient removal SHALL deactivate rather
than physically delete a patient with retained history. The shared directory
presentation SHALL provide an explicit row-level edit action and an in-page
edit view rather than requiring a patient-details popup for ordinary editing.

#### Scenario: View an administrative patient record

- **WHEN** an authenticated Receptionist selects a patient's `Edit` action in
  the directory table
- **THEN** the system displays that patient's generated Patient ID and
  permitted identity, demographic, measurement, contact, and address
  information in the Patient directory's edit view

#### Scenario: Save valid administrative changes

- **WHEN** an authenticated Receptionist submits valid changes to a selected
  patient's administrative information from the edit view
- **THEN** the system persists the changes, refreshes the directory table and
  dependent patient selectors, and returns to the Patient directory

#### Scenario: Reject changing to another patient's identity document

- **WHEN** an authenticated Receptionist changes a patient's document details
  to a normalized identity combination assigned to another patient
- **THEN** the system rejects the complete update with the duplicate-identity
  error
- **AND** the selected patient's existing information remains unchanged
- **AND** the edit view remains open with the entered values available for
  correction

#### Scenario: Reject an invalid administrative update

- **WHEN** an authenticated Receptionist submits missing or invalid required
  administrative information from the edit view
- **THEN** the system shows a field-appropriate validation error
- **AND** the selected patient's existing information remains unchanged
- **AND** the edit view remains open

#### Scenario: Cancel editing

- **WHEN** an authenticated Receptionist selects `Cancel` from the edit view
  before saving
- **THEN** the system discards unsaved values and returns to the Patient
  directory without changing the patient

#### Scenario: Select date of birth and calculate age

- **WHEN** a Receptionist selects a date of birth using the calendar control
- **THEN** the system displays the patient's age calculated against the current
  date in the `Asia/Singapore` time zone
- **AND** age is read-only and is not persisted as a second source of truth
- **AND** the age control has no placeholder text when no date is selected

#### Scenario: Navigate date of birth by month and year

- **WHEN** a Receptionist chooses a month or year from the date-of-birth
  dropdowns
- **THEN** the calendar moves directly to that month and year without repeated
  previous/next navigation
- **AND** the selected day is preserved when valid or clamped to the last valid
  day of the selected month

#### Scenario: Save optional measurements

- **WHEN** an authenticated Receptionist supplies a positive whole-number
  height in centimetres or a positive weight with at most one decimal place, or
  leaves either optional measurement blank
- **THEN** the system saves the patient using the supplied measurement values or
  null for omitted values

#### Scenario: Reject invalid measurements

- **WHEN** an authenticated Receptionist submits a zero, negative, non-numeric,
  fractional height, or weight with more than one decimal place
- **THEN** the system rejects the update without changing the patient

#### Scenario: Populate and edit a phone country code

- **WHEN** a Receptionist selects an issuing country
- **THEN** the phone country-code field is populated with digits from
  international calling-code metadata, such as `65` for Singapore
- **AND** the Receptionist may edit the populated country code
- **AND** a fixed non-editable `+` is displayed immediately before the
  country-code control

#### Scenario: Reject unsupported phone characters

- **WHEN** an authenticated Receptionist submits a blank country code, a
  country code containing a plus sign or non-digit, a country code longer than
  3 digits, or a blank/non-digits phone number
- **THEN** the system rejects the registration or update with a phone
  validation error

#### Scenario: Reject missing required data or invalid email

- **WHEN** any required patient field is missing or the email lacks a non-empty
  local part, `@`, and non-empty domain part
- **THEN** the system rejects the registration or update with a field-specific
  validation error

#### Scenario: Deactivate a patient

- **WHEN** an authenticated Receptionist removes a patient who has retained
  clinic history from the edit view
- **THEN** the system marks the patient inactive instead of physically deleting
  the patient or related records

#### Scenario: Reactivate an inactive patient

- **WHEN** an authenticated Receptionist activates an inactive patient from the
  edit view
- **THEN** the system marks the patient active again without changing the
  Patient ID or retained history
- **AND** the status action changes back to `Deactivate patient`

#### Scenario: Restrict sex choices

- **WHEN** a Receptionist registers or edits a patient
- **THEN** the interface offers only Male and Female
- **AND** Male is the first option
- **AND** the service rejects any other or missing sex value

### Requirement: Registration and patient management use separate tabs

The shared administrative patient area SHALL present one `Patient directory`
page rather than registration and search/manage sub-tabs. By default, the page
SHALL show a headed patient table with `Patient ID`, `Name`, `Date of birth`,
`Phone`, `Email`, `Status`, and `Actions` columns, plus a `Register new
patient` action at the bottom. Each populated row SHALL provide an `Edit`
action in the far-right `Actions` column. Selecting `Register new patient`
SHALL switch the page to a blank registration view containing the existing
patient-registration fields, a register action, and a cancel action. Selecting
`Edit` SHALL switch the page to an in-page edit view containing the selected
patient's permitted administrative details, generated Patient ID, save action,
active-status action, delete action, and cancel action. Registration, edit, and
directory forms SHALL use independent controls and state. Ordinary editing
SHALL not open a patient-details popup. Deletion confirmation and blocked
deletion explanation popups SHALL remain available for their safety purpose.

#### Scenario: Open the search and manage tab

- **WHEN** an authorized Receptionist or Doctor opens the patient area
- **THEN** the system displays one `Patient directory` page with search
  controls, a headed patient table, and a `Register new patient` action at the
  bottom
- **AND** the page does not display registration or search/manage sub-tabs

#### Scenario: Display patient rows

- **WHEN** the directory search returns one or more administrative patients
- **THEN** each result appears as one table row under the documented column
  headers
- **AND** the far-right cell contains an `Edit` action for that patient
- **AND** the table does not display clinical information

#### Scenario: Open the registration tab

- **WHEN** an authorized Receptionist or Doctor selects `Register new patient`
  from the Patient directory
- **THEN** the system switches to a blank registration form and register action
  without Patient ID, search results, save-changes, or deactivation controls

#### Scenario: Select a search result

- **WHEN** an authorized Receptionist or Doctor selects a patient's `Edit`
  action
- **THEN** the system replaces the directory content with an in-page edit form
  populated with that patient's administrative values
- **AND** no ordinary patient-details popup is opened
- **AND** clinical fields remain unavailable

#### Scenario: Cancel registration or editing

- **WHEN** a user selects `Cancel` from either the registration or edit view
  before submitting changes
- **THEN** the system clears the unfinished form and returns to the Patient
  directory without creating or changing a patient

#### Scenario: Return to the directory after successful registration or edit

- **WHEN** an authorized Receptionist or Doctor submits valid registration or
  edit data
- **THEN** the system completes the existing atomic service operation
- **AND** the system returns to the Patient directory with refreshed table
  results and dependent patient selectors

#### Scenario: Keep the active form after a failed submission

- **WHEN** registration or editing fails validation, identity uniqueness, or
  persistence checks
- **THEN** the system keeps the user on the corresponding form view, displays
  an actionable error, and does not partially create or change a patient

#### Scenario: Preserve guarded deletion feedback

- **WHEN** a user requests deletion from the in-page edit view
- **THEN** the system allows permanent deletion only when no related clinic
  data exists
- **AND** a patient with related data remains preserved and a popup lists each
  blocking category and count
