## MODIFIED Requirements

### Requirement: Schedule rows SHALL be grouped and presented as appointment summaries

Schedule SHALL group appointments by their calendar date, omit date groups with no displayed appointments, and order appointments within each group by start time and appointment identifier. Each displayed appointment row SHALL show its time range, the patient's name, and readable lifecycle status. Patient identifiers SHALL NOT be displayed in the doctor Schedule. Schedule SHALL not expose clinical records, diagnoses, consultation notes, follow-up notes, prescriptions, work locations, or invented all-day event data. `DECLINED` and `CANCELLED` appointments SHALL be excluded from the doctor Schedule.

#### Scenario: Doctor views a date group

- **WHEN** one or more non-declined, non-cancelled appointments occur on a date in the loaded stream
- **THEN** Schedule displays one date group with the date and day name followed by that day's appointments in chronological order

#### Scenario: Appointment rows have equal start times

- **WHEN** multiple appointments have the same start time
- **THEN** Schedule presents them in a deterministic order without dropping or duplicating either appointment

#### Scenario: Appointment crosses midnight

- **WHEN** an appointment starts on one date and ends on the following date
- **THEN** Schedule displays one row under its start date with a readable range that communicates the date change

#### Scenario: Doctor views lifecycle statuses

- **WHEN** future appointments have different displayed `AppointmentStatus` values, including `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT`
- **THEN** each row displays the patient's name, readable status text, and distinguishable status treatment

#### Scenario: Declined or cancelled appointments are excluded

- **WHEN** future appointments include a `DECLINED` or `CANCELLED` appointment
- **THEN** that appointment is not returned to or displayed in the Doctor Schedule

#### Scenario: Appointment is outside configured working hours

- **WHEN** an appointment is scheduled outside configured working intervals
- **THEN** Schedule still displays the appointment at its scheduled time because working hours remain visual preferences only

### Requirement: Schedule data and controls SHALL remain Doctor-owned and support authorized appointment actions

Schedule SHALL load only the signed-in Doctor's authorized administrative appointment projection. Viewing, scrolling, navigating, or changing the view mode SHALL not mutate appointments or calendar settings. Selecting an eligible appointment row SHALL open the same authorized appointment details and action behavior as Calendar. Only the assigned Doctor may accept, decline, reschedule, or cancel their own eligible appointment, and cancellation SHALL be available only before check-in. Schedule controls, actions, and states SHALL have accessible names or readable text.

#### Scenario: Doctor views only their own Schedule

- **WHEN** a Doctor opens or pages through Schedule
- **THEN** every returned and displayed appointment belongs to that signed-in Doctor and no other Doctor's data is exposed

#### Scenario: Schedule navigation does not mutate data

- **WHEN** a Doctor switches views, navigates, scrolls, or retries a Schedule page
- **THEN** appointment timestamps, assignments, statuses, and saved Calendar settings remain unchanged

#### Scenario: Doctor opens an eligible Schedule appointment

- **WHEN** the Doctor selects a displayed `PENDING` or `ACCEPTED` appointment row
- **THEN** an appointment details popup opens with the actions authorized for the assigned Doctor

#### Scenario: Doctor uses Schedule controls accessibly

- **WHEN** a Doctor uses the view selector, navigation controls, lazy-loading states, appointment rows, or popup actions with keyboard navigation or assistive technology
- **THEN** each interactive control and state has a reachable action or readable accessible description

## ADDED Requirements

### Requirement: Schedule appointment actions SHALL refresh the Doctor's schedule

After a successful Doctor appointment decision, reschedule, or cancellation from Schedule, the Schedule SHALL close or update the details popup and reload the affected appointment data. A declined or cancelled appointment SHALL disappear from the Doctor Schedule after the refresh.

#### Scenario: Doctor reschedules from Schedule

- **WHEN** the assigned Doctor successfully reschedules their own `PENDING` or `ACCEPTED` appointment
- **THEN** the row reflects the new interval and `ACCEPTED` status without exposing another Doctor's appointment

#### Scenario: Doctor declines from Schedule

- **WHEN** the assigned Doctor successfully declines a displayed eligible appointment
- **THEN** the appointment becomes `DECLINED` and is removed from the Doctor Schedule

#### Scenario: Doctor cancels from Schedule

- **WHEN** the assigned Doctor successfully cancels a displayed appointment before check-in
- **THEN** the appointment becomes `CANCELLED` and is removed from the Doctor Schedule
