# doctor-calendar-schedule Specification

## Purpose

Provides Doctors with a scalable chronological agenda of appointments from a selected date forward without replacing the existing weekly Calendar time grid.

## Requirements

### Requirement: Doctors SHALL be able to switch between Week and Schedule views

An authenticated Doctor SHALL be able to choose a Schedule view in addition to the existing Week view on the Calendar page. Schedule view SHALL initially anchor to the current Singapore clinic date and SHALL not be limited to the selected seven-day week.

#### Scenario: Doctor opens Schedule view

- **WHEN** an authenticated Doctor selects Schedule from the Calendar view selector
- **THEN** the Calendar replaces the weekly time grid with a chronological Schedule view while keeping the Week view available

#### Scenario: Schedule view starts at the current date

- **WHEN** a Doctor enters Schedule view without a previously selected Schedule anchor
- **THEN** the first date in the stream is the current date in the Singapore clinic timezone and the stream includes appointments starting on that date or later

#### Scenario: Doctor returns to Week view

- **WHEN** a Doctor selects Week after viewing Schedule
- **THEN** the existing seven-day time grid remains available with its selected week, working-hour shading, breaks, and current-time behavior unchanged

### Requirement: Schedule appointments SHALL be loaded lazily in chronological pages

Schedule view SHALL request and display a bounded first page of appointments from its anchor date. When the Doctor scrolls near the end of the loaded content, the Calendar SHALL append the next bounded page in chronological order. Page traversal SHALL use a stable chronological position so an appointment is not displayed twice or skipped because another appointment has the same start time.

#### Scenario: Schedule loads an initial bounded page

- **WHEN** Schedule view is opened
- **THEN** it requests only an initial bounded page of the signed-in Doctor's appointments rather than loading every future appointment

#### Scenario: Doctor reaches the end of loaded Schedule content

- **WHEN** the Doctor scrolls near the end of loaded Schedule content and more appointments exist after the current page
- **THEN** the next page is loaded once and appended without removing or reordering previously displayed appointments

#### Scenario: Schedule reaches the end of future appointments

- **WHEN** the final page has been appended and no later appointment exists for the stream anchor
- **THEN** Schedule displays an informative end-of-schedule state and does not repeatedly request more pages

#### Scenario: Schedule has no appointments

- **WHEN** no appointment starts on or after the Schedule anchor for the signed-in Doctor
- **THEN** Schedule displays an informative empty state without an application error

#### Scenario: A later page request fails

- **WHEN** loading another Schedule page fails because of a recoverable data-access error
- **THEN** the already loaded rows remain visible, an actionable loading error is shown, and the Doctor can retry the failed page

### Requirement: Schedule navigation SHALL re-anchor the unbounded stream

Schedule view SHALL keep Today, previous/next navigation, and the custom week picker available. Today SHALL reset the stream anchor to the current Singapore clinic date. Previous and next SHALL move the Schedule anchor by one preferred calendar week, and selecting a date or week from the custom week picker SHALL re-anchor the stream to that selected date or week's first day. Re-anchoring SHALL reload from the new anchor without imposing a seven-day end boundary.

#### Scenario: Doctor returns Schedule to today

- **WHEN** the Doctor selects Today in Schedule view
- **THEN** the stream is cleared, reloaded from the current Singapore clinic date, and scrolled to its first date group

#### Scenario: Doctor moves the Schedule anchor

- **WHEN** the Doctor selects the previous-week or next-week control in Schedule view
- **THEN** the Schedule anchor moves exactly seven days in the corresponding direction and the stream reloads from that date forward

#### Scenario: Doctor jumps with the custom week picker

- **WHEN** the Doctor selects a date or week row from the custom week picker while Schedule view is active
- **THEN** the stream reloads from the selected date or selected week's first day, retains chronological loading, and is not truncated to that week

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

### Requirement: Schedule SHALL communicate current and past state without color alone

Schedule SHALL identify the current date and SHALL visually distinguish appointments whose scheduled time has elapsed while the Calendar is open. These distinctions SHALL also have readable text, date, or status cues so they remain understandable without color perception.

#### Scenario: Doctor views today's appointments

- **WHEN** Schedule contains the current Singapore clinic date
- **THEN** that date group is visibly identified as today and appointments earlier than the current local time have a readable elapsed or past cue

#### Scenario: Doctor views a future date

- **WHEN** a loaded date is after the current Singapore clinic date
- **THEN** its date group and appointments remain presented as future schedule entries without being marked elapsed

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
