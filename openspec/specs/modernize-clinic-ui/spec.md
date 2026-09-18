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
in a table with columns for Name, Date of birth, Phone, Email, Status, and
Actions, without generated Patient or Doctor ID columns; each row SHALL place
its `View` action in the far-right Actions column. Status SHALL not be
communicated by color alone.

#### Scenario: Staff views a patient directory

- **WHEN** an authorized Doctor or Receptionist searches the patient directory
- **THEN** each administrative patient result appears as one scannable table
  row beneath the documented column headers
- **AND** the row contains readable status text and a far-right `View` action
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

### Requirement: Date-picker fields SHALL use a shared minimal presentation

Every JavaFX date-picker field in an application view or dialog SHALL use the
shared minimal field treatment: compact height and spacing, a consistent rounded
border, an unobtrusive embedded calendar button, and a single outer focus
indicator. The treatment SHALL preserve the field's date value, popup behavior,
keyboard interaction, accessible name, and existing validation semantics.

#### Scenario: Staff views a date-picker field

- **WHEN** a Doctor, Receptionist, or other staff member opens a view or dialog containing a date-picker field
- **THEN** the field uses the shared minimal presentation consistently with other application selectors

#### Scenario: Staff focuses or opens a date picker

- **WHEN** staff focuses the field, opens its calendar popup, or selects a date
- **THEN** focus, popup, keyboard, accessible-label, and date-selection behavior remain usable and unchanged

### Requirement: Shared date-picker fields SHALL use a compact width

Every JavaFX date-picker field using the shared minimal treatment SHALL use a
short, consistent preferred width that remains large enough for its formatted
date and calendar button. Context-specific filter layouts SHALL NOT expand the
control back to the previous wide field width.

#### Scenario: Staff views date pickers in different application areas

- **WHEN** staff opens a Dashboard, Calendar, appointment dialog, time-off dialog, or Receptionist filter containing a date picker
- **THEN** the date picker uses the same compact width treatment while remaining usable and readable

### Requirement: Patient Directory SHALL use one full-height results surface

The shared Doctor and Receptionist Patient Directory SHALL place its dynamic page
title inside the same white page card that contains the patient search and
results. The standalone directory description and `Patient results` heading
SHALL not be rendered. The page card SHALL fit the available page height, and
the results table SHALL expand to consume the remaining card height while its
own scrollbar handles overflow. The search field SHALL expand across the
available search row while its actions remain visible.

#### Scenario: Staff opens the Patient Directory

- **WHEN** a Doctor or Receptionist navigates to the Patient Directory
- **THEN** the title appears inside the white results card, no duplicate description or results heading is shown, the search field is wide, and the card fills the page viewport

#### Scenario: Staff resizes the Patient Directory page
- **WHEN** the directory page has more vertical space than its minimum table layout requires
- **THEN** the results table grows into that space without changing its rows or search/action controls

#### Scenario: Staff switches directory states

- **WHEN** staff opens registration, patient details, or editing from the directory
- **THEN** the same in-card title slot remains visible with the appropriate state title and the page surface retains its full-height layout

### Requirement: Patient Directory row actions SHALL survive search refreshes

The shared Doctor and Receptionist Patient Directory SHALL render a View action
for every patient currently present in the results table, including after a
search replaces the table items and JavaFX reuses table cells.

#### Scenario: Staff searches patient results

- **WHEN** staff enters a search and selects Search patients
- **THEN** every returned patient row exposes its own View action with the stable row-specific control identifier
