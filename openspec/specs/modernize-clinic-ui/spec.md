# modernize-clinic-ui Specification

## Purpose

Provides NUSynapxe staff with a consistent, modern, and scannable desktop workspace for carrying out existing clinic workflows safely and efficiently.

## Requirements

### Requirement: Application screens SHALL use a consistent visual language

Authentication screens and authenticated workspaces SHALL present a coherent clinical visual language with clear hierarchy between page titles, section headings, content surfaces, form labels, primary actions, secondary actions, and feedback. Controls that perform equivalent actions SHALL have consistent visual treatment across roles.

#### Scenario: Staff opens an authentication screen
- **WHEN** the application shows first-run setup or login
- **THEN** the screen presents the NUSynapxe identity, a focused form surface, clearly labelled fields, a prominent primary action, and visibly associated validation or service feedback

#### Scenario: Staff opens an authenticated workspace
- **WHEN** a Doctor, Receptionist, or System Admin signs in
- **THEN** the workspace presents a consistent header with the current role and signed-in identity, a clearly identifiable logout action, and visually separated content sections

### Requirement: Workspaces SHALL provide clear navigation and adapt to available space

Authenticated workspaces SHALL provide role-appropriate navigation for their
available features, keep the active destination visually identifiable, and
remain usable when the application window is resized within its supported
range. Content SHALL not rely on horizontal clipping to expose essential labels
or actions. The Doctor navigation rail SHALL present its `Dashboard` and
`Patients` destinations as full-width, contiguous items within the rail rather
than padded sub-buttons.

#### Scenario: Receptionist changes work areas

- **WHEN** a Receptionist chooses Patients, Appointments, Checkout, or Revenue
- **THEN** the selected destination is clearly indicated and its existing
  controls and data are shown without exposing controls from another work area
  as if they belonged to the selected destination

#### Scenario: Doctor changes workspace destinations

- **WHEN** a Doctor chooses `Dashboard` or `Patients`
- **THEN** the selected destination is clearly indicated and its corresponding
  content and actions are shown
- **AND** the two navigation items span the complete navigation-rail width with
  no gap between them and do not appear as nested sub-buttons

#### Scenario: Staff resizes a workspace

- **WHEN** an authenticated staff member enlarges or reduces the application
  window within the supported range
- **THEN** the header, navigation, forms, lists, and actions remain reachable,
  with scrollable content used where the available height is insufficient

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

### Requirement: Patient form selectors SHALL use compact controls

The registration and edit views SHALL render their dropdown controls with the
same compact height and spacing treatment used by the System Admin role
selector. Compact styling SHALL preserve each control's label, option set,
keyboard focus treatment, and current identity-country behavior.

#### Scenario: Staff opens a patient form

- **WHEN** an authorized Doctor or Receptionist opens registration or editing
- **THEN** the patient form's identity, country, date, and sex dropdowns use the
  compact selector presentation and remain fully usable

### Requirement: Doctor consultation work SHALL use a coordinated selected-appointment view

The Doctor workspace SHALL keep the Doctor's assigned schedule visibly coordinated with the selected appointment's consultation, prescription, availability, and completion actions. Selecting a different appointment SHALL make the corresponding consultation context and available actions clear without mixing records between appointments.

#### Scenario: Doctor selects an appointment
- **WHEN** a Doctor selects an assigned appointment
- **THEN** the selected appointment is visually distinguished and the consultation and prescription areas show only the selected appointment's permitted data and actions

#### Scenario: Doctor has no selected appointment
- **WHEN** no appointment is selected
- **THEN** consultation and prescription actions communicate that an appointment must be selected and do not imply that an unrelated record is active

### Requirement: Setup, login, and staff administration SHALL share the workspace language

First-run setup, login, and System Admin staff-account management SHALL use the same visual hierarchy, field treatment, action emphasis, and feedback conventions as the role workspaces while retaining their existing validation and account-management behavior.

#### Scenario: System Admin manages staff accounts
- **WHEN** a System Admin opens account management
- **THEN** account creation and the current staff-account list are visually distinct, readable sections with clear role and account information and an unambiguous create action

#### Scenario: Authentication validation fails
- **WHEN** login or setup rejects the submitted values
- **THEN** the existing non-sensitive message is shown in a clearly associated feedback area and the user can correct and resubmit the form

### Requirement: The redesign SHALL preserve workflow behavior and confidentiality

The redesigned presentation SHALL preserve existing service-layer authorization, patient administrative versus clinical-data boundaries, appointment lifecycle and scheduling rules, payment and revenue semantics, automatic refresh behavior, and successful or failed action outcomes. Receptionist views SHALL not display diagnoses, consultation notes, follow-up notes, or prescriptions.

#### Scenario: Receptionist uses the redesigned workspace
- **WHEN** a Receptionist searches patients, coordinates an appointment, checks in a patient, completes checkout, or views revenue
- **THEN** the same authorized operations, validation rules, lifecycle transitions, feedback outcomes, and refresh behavior remain available without clinical information becoming visible

#### Scenario: A Doctor or System Admin uses the redesigned workspace
- **WHEN** a Doctor or System Admin performs an existing authorized operation
- **THEN** the operation continues to use the existing role and ownership rules and the redesign does not grant additional data access
