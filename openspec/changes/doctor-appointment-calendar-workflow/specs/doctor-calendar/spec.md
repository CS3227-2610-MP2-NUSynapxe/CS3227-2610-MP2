## MODIFIED Requirements

### Requirement: Calendar appointments SHALL be presented as status-aware time blocks

Each appointment with a status other than `DECLINED` or `CANCELLED` that overlaps the displayed week SHALL be represented in the appropriate day and time position, with its start and end times, the patient's name, and lifecycle status. Patient identifiers SHALL NOT be displayed in the appointment block. Displayed `AppointmentStatus` values SHALL remain distinguishable through both text and visual treatment. Declined and cancelled appointments SHALL not be rendered in the Doctor Calendar. Clinical records, diagnoses, consultation notes, follow-up notes, and prescriptions SHALL not be displayed.

#### Scenario: Doctor views appointments in the time grid

- **WHEN** a non-declined, non-cancelled appointment falls within or overlaps the displayed week
- **THEN** the Calendar shows a distinct block spanning its scheduled interval in the corresponding day column

#### Scenario: Doctor does not see declined or cancelled appointments

- **WHEN** the displayed week contains a `DECLINED` or `CANCELLED` appointment
- **THEN** that appointment is absent from the Doctor Calendar while remaining available to authorized receptionist management views when applicable

#### Scenario: Doctor views appointments with different lifecycle states

- **WHEN** appointments in the displayed week have `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` lifecycle statuses
- **THEN** each block shows the patient's name, a readable status, and a distinguishable status treatment

#### Scenario: Appointment is outside configured working hours

- **WHEN** an appointment is scheduled outside the Doctor's configured working intervals
- **THEN** the appointment remains visible at its scheduled time on the greyed non-working background

#### Scenario: Calendar displays only non-clinical appointment information

- **WHEN** a Doctor views an appointment block
- **THEN** the block contains appointment timing, the patient's name, and status only, and contains no clinical information or patient identifier

## ADDED Requirements

### Requirement: Doctors SHALL be able to create and coordinate appointments from Calendar

An authenticated Doctor SHALL be able to create an appointment for the Doctor's own schedule from an `Add appointment` action or an empty time slot. A valid appointment created by the Doctor SHALL be assigned to that Doctor and start in the `ACCEPTED` state. Selecting a visible `PENDING` or `ACCEPTED` appointment SHALL open appointment details with the actions permitted for the assigned Doctor. Appointment actions SHALL be authorized by the service and SHALL refresh the Calendar after a successful change.

#### Scenario: Doctor opens the Add appointment form

- **WHEN** the Doctor selects `Add appointment` on Calendar
- **THEN** an appointment form opens with patient, date, start, and end inputs and does not permit assigning the appointment to another Doctor

#### Scenario: Doctor starts an appointment from an empty time slot

- **WHEN** the Doctor selects an empty time slot in their Calendar
- **THEN** the appointment form opens with that day and time used as the initial appointment interval

#### Scenario: Doctor creates an appointment from Calendar

- **WHEN** the Doctor submits valid patient and interval values for an available slot
- **THEN** the appointment is created for the signed-in Doctor in the `ACCEPTED` state and the Calendar displays it after refreshing

#### Scenario: Doctor opens appointment details

- **WHEN** the Doctor selects a visible `PENDING` or `ACCEPTED` appointment block
- **THEN** a popup opens showing the patient's name, scheduled interval, and status with authorized rescheduling and pre-check-in cancellation actions

#### Scenario: Doctor accepts or declines an appointment from its block

- **WHEN** the assigned Doctor selects an enabled `Accept` or `Decline` action on a visible appointment block in an eligible decision state
- **THEN** the service changes the appointment to the selected decision state and the Calendar refreshes; a declined appointment is no longer displayed

#### Scenario: Doctor reschedules an appointment from its popup

- **WHEN** the assigned Doctor submits a valid new interval for their own `PENDING` or `ACCEPTED` appointment
- **THEN** the appointment is moved if the interval is available, its state is `ACCEPTED`, and the popup and Calendar refresh

#### Scenario: Doctor cancels an appointment from its popup

- **WHEN** the assigned Doctor cancels their own `PENDING` or `ACCEPTED` appointment before check-in
- **THEN** the appointment changes to `CANCELLED`, the popup closes, and the appointment is absent from the Calendar

### Requirement: Calendar layout SHALL keep appointment content within its day column

The Calendar SHALL align the first day column directly after the time axis without an unnecessary blank gap. Each appointment block SHALL remain fully within its assigned day column and overlap lane, including when multiple appointments share a time interval. Patient names and appointment actions SHALL remain usable within the available block width without drawing into an adjacent day column.

#### Scenario: Calendar renders the first day column

- **WHEN** the Doctor opens a Calendar week
- **THEN** the first day column begins immediately after the time-axis region without an additional empty white column or gap

#### Scenario: Appointment blocks are narrower than their day column

- **WHEN** overlapping appointments are rendered in one day column
- **THEN** every block is clipped or sized to its assigned lane and no block content or action control extends into another day column

#### Scenario: Narrow appointment block displays administrative content

- **WHEN** an appointment block has limited width
- **THEN** the patient's name and status remain readable or are safely truncated, and no patient identifier is added to compensate for the limited space
