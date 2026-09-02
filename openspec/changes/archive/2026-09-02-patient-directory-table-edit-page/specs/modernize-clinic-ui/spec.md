## MODIFIED Requirements

### Requirement: Receptionist features SHALL be presented as focused operational pages

The Receptionist workspace SHALL present the Patient directory, appointment
booking and management, checkout, and daily revenue as distinct but
consistently designed operational pages. The Patient directory SHALL combine
search results, registration entry, and row-level editing while keeping
registration and editing as explicit in-page states. Existing appointment
subflows that are intentionally separate SHALL remain distinguishable, and
frequent actions SHALL be grouped near the data they affect.

#### Scenario: Receptionist manages patients

- **WHEN** a Receptionist opens the patient area
- **THEN** registration entry, directory search, a headed patient table,
  row-level edit actions, and permitted patient-management actions are
  presented in a scannable layout
- **AND** registration and editing remain independent from clinical records

#### Scenario: Receptionist coordinates appointments

- **WHEN** a Receptionist opens the appointment area
- **THEN** booking, filters, summary counts, schedule results, selection-based
  lifecycle actions, and scheduling feedback are presented in a readable layout
  without changing the existing lifecycle rules

#### Scenario: Receptionist completes checkout or views revenue

- **WHEN** a Receptionist opens Checkout or Daily revenue
- **THEN** appointment selection, payment controls, date entry, and revenue
  results are visually grouped so the selected record and the action target are
  unambiguous

### Requirement: Records and workflow states SHALL be scannable

Patient, appointment, prescription, and staff-account results SHALL use
human-readable summaries appropriate to their role, clear selection states,
explicit text labels for lifecycle or account status, and an informative empty
state when no results are available. Patient-directory results SHALL be shown
in a table with column headers for Patient ID, Name, Date of birth, Phone,
Email, Status, and Actions; each row SHALL place its `Edit` action in the far-
right Actions column. Status SHALL not be communicated by color alone.

#### Scenario: Staff views a patient directory

- **WHEN** an authorized Doctor or Receptionist searches the patient directory
- **THEN** each administrative patient result appears as one scannable table
  row beneath the documented column headers
- **AND** the row contains readable status text and a far-right `Edit` action
- **AND** the table does not expose clinical information

#### Scenario: Staff views an appointment list

- **WHEN** an appointment list contains one or more records
- **THEN** each record shows the permitted identifying context, relevant date or
  time, and a readable status label without exposing raw internal object
  formatting

#### Scenario: A filter returns no records

- **WHEN** the active search or filter criteria match no records
- **THEN** the relevant area shows a clear no-results message and keeps its
  search, filter, and navigation controls usable

## ADDED Requirements

### Requirement: Patient form selectors SHALL use compact controls

The registration and edit views SHALL render their dropdown controls with the
same compact height and spacing treatment used by the System Admin role
selector. Compact styling SHALL preserve each control's label, option set,
keyboard focus treatment, and current identity-country behavior.

#### Scenario: Staff opens a patient form

- **WHEN** an authorized Doctor or Receptionist opens registration or editing
- **THEN** the patient form's identity, country, date, and sex dropdowns use the
  compact selector presentation and remain fully usable
