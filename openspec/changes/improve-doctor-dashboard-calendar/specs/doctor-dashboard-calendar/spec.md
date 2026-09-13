## Purpose

Provides Doctors with a compact single-day schedule on Dashboard that coordinates appointment selection with the existing clinical workflow.

## ADDED Requirements

### Requirement: The Doctor Dashboard SHALL open on a navigable single-day timeline

The Doctor Dashboard SHALL replace its appointment list with a compact timeline for one selected Singapore clinic date. The initially selected date SHALL be the current Singapore clinic date. The timeline SHALL provide Today, previous-day, next-day, date-selection, and manual-refresh controls, and refresh SHALL retain the selected date.

#### Scenario: Doctor opens Dashboard
- **WHEN** an authenticated Doctor opens Dashboard
- **THEN** the timeline displays the current Singapore clinic date and the Doctor's schedule for that date

#### Scenario: Doctor navigates by one day
- **WHEN** the Doctor selects the previous-day or next-day control
- **THEN** the timeline moves exactly one day in that direction and refreshes its schedule

#### Scenario: Doctor selects a date
- **WHEN** the Doctor chooses a different valid date with the date selector
- **THEN** the timeline displays that date and refreshes its schedule

#### Scenario: Doctor returns to today
- **WHEN** the Doctor selects Today while viewing another date
- **THEN** the timeline returns to the current Singapore clinic date

#### Scenario: Doctor manually refreshes the selected day
- **WHEN** the Doctor activates the refresh control
- **THEN** the system reloads the selected day's appointments and availability while keeping that date selected

#### Scenario: Selected day has no schedule entries
- **WHEN** the selected day has no visible appointment or time-off interval
- **THEN** the timeline remains navigable and communicates that the day has no scheduled entries

### Requirement: The Dashboard timeline SHALL coordinate with the clinical detail pane

The Dashboard timeline SHALL display the signed-in Doctor's `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, and `CHECKED_OUT` appointments as proportional time blocks containing the patient's name, scheduled time, and readable lifecycle status without a patient identifier. Appointment blocks SHALL act as selection targets; actions and clinical information SHALL remain in the existing selected-appointment detail pane and SHALL continue to follow service-layer authorization and lifecycle rules.

#### Scenario: Doctor selects a visible appointment
- **WHEN** the Doctor selects an appointment block assigned to that Doctor
- **THEN** the detail pane loads the appointment's authorized clinical context and displays only the actions allowed for its lifecycle state

#### Scenario: Doctor changes the selected day
- **WHEN** the Doctor navigates to a day that does not contain the selected appointment
- **THEN** the stale appointment selection and its clinical detail are cleared

#### Scenario: Dashboard refresh retains a visible selection
- **WHEN** the Doctor refreshes and the selected appointment remains visible on the selected date
- **THEN** the appointment remains selected and its detail is reloaded from current data

#### Scenario: Appointment duration is presented
- **WHEN** the selected day contains appointments of different durations
- **THEN** each appointment block occupies timeline height proportional to its scheduled interval, with a 60-minute appointment occupying twice the time span of a 30-minute appointment

#### Scenario: Another Doctor has an appointment at the same time
- **WHEN** another Doctor has an appointment overlapping the selected date
- **THEN** that appointment and its patient information are absent from the signed-in Doctor's Dashboard

### Requirement: The Dashboard timeline SHALL communicate actual availability

The Dashboard timeline SHALL render the signed-in Doctor's time-off intervals as labelled time blocks distinct from appointments and from visual working-hours shading. Declined and cancelled appointments SHALL not be represented and SHALL leave their former intervals visually free unless another appointment or time-off interval occupies them.

#### Scenario: Selected day contains Doctor time off
- **WHEN** a Doctor-owned time-off interval overlaps the selected day
- **THEN** the overlapping portion is displayed as a labelled time-off block at its actual time and duration

#### Scenario: Selected day contains a declined appointment
- **WHEN** a declined appointment overlaps the selected day
- **THEN** the declined appointment creates no appointment or availability block on the Dashboard timeline

#### Scenario: Selected day contains a cancelled appointment
- **WHEN** a cancelled appointment overlaps the selected day
- **THEN** the cancelled appointment creates no appointment or unavailable block on the Dashboard timeline

### Requirement: The Dashboard timeline SHALL remain usable in its compact column

The Dashboard timeline SHALL use a compact presentation that keeps block content inside the day column, preserves proportional duration, distinguishes state without relying on colour alone, and supports keyboard appointment selection. On initial display it SHALL scroll to a useful position near the current time for today or the earliest appointment or time-off entry for another date, while retaining access to the entire day.

#### Scenario: Dashboard displays the current day
- **WHEN** the current day timeline first becomes visible
- **THEN** it scrolls near the current Singapore clinic time and shows an updating current-time indicator

#### Scenario: Dashboard displays another day
- **WHEN** a non-current day with scheduled entries becomes visible
- **THEN** it scrolls to the earliest visible appointment or time-off entry

#### Scenario: Doctor uses keyboard navigation
- **WHEN** the Doctor focuses an appointment block and presses Enter or Space
- **THEN** the appointment is selected and its detail is loaded without requiring pointer input

#### Scenario: Doctor uses an icon-only refresh control
- **WHEN** the refresh control is presented as an icon
- **THEN** it has an accessible name and tooltip that communicate its refresh action

### Requirement: The selected Dashboard appointment SHALL show authorized, status-specific detail

When a Doctor selects an appointment block, the Dashboard detail pane SHALL
show a status-coloured header containing the patient's name, scheduled time,
and readable lifecycle status, without the generated Patient ID or the text
"Selected appointment". The pane SHALL show the selected patient's
administrative details as read-only content. It SHALL render only the
workflow content applicable to the appointment status: pending appointments
have Accept, Decline, and Reschedule actions; accepted appointments have
Decline, Reschedule, and an eligible Check in action; checked-in appointments
have the existing consultation, prescription, and completion workflow.

#### Scenario: Doctor selects a pending appointment
- **WHEN** a pending appointment is selected
- **THEN** the header uses the pending status presentation, the patient's name and read-only details are visible, and Accept, Decline, and Reschedule are available

#### Scenario: Doctor selects an accepted appointment
- **WHEN** an accepted appointment is selected
- **THEN** the header uses the accepted status presentation, the patient's name and read-only details are visible, and Decline, Reschedule, and Check in are shown with Check in following the existing start-time eligibility rule

#### Scenario: Doctor selects a checked-in appointment
- **WHEN** a checked-in appointment is selected
- **THEN** the header uses the checked-in status presentation, the patient's name and read-only details are visible, and the existing consultation, prescription, and completion controls are available

#### Scenario: Doctor selects a terminal appointment
- **WHEN** a declined, cancelled, completed, or checked-out appointment is selected or retained after refresh
- **THEN** no lifecycle-mutating action is presented; completed and checked-out records remain readable without editing controls, while declined and cancelled appointments show a non-actionable status explanation

#### Scenario: Doctor reschedules from Dashboard details
- **WHEN** the Doctor activates Reschedule for a pending or accepted selection
- **THEN** the Calendar-style appointment editor opens for that appointment and a successful update refreshes the Dashboard selection

#### Scenario: Appointment status changes after an action
- **WHEN** an action changes the selected appointment's lifecycle status
- **THEN** the Dashboard refreshes the same selection when it remains projected and renders the new status branch, or clears the pane when the appointment is no longer projected
