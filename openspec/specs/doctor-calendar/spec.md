# doctor-calendar Specification

## Purpose

Provides Doctors with a personal, configurable weekly calendar for reviewing assigned appointments and understanding working and non-working periods without changing appointment scheduling rules.

## Requirements

### Requirement: Doctors SHALL have a separate weekly Calendar page

An authenticated Doctor SHALL be able to open a Calendar page in addition to the existing Dashboard and Patients pages. The Calendar page SHALL show a seven-day time grid for the signed-in Doctor and SHALL not expose appointments belonging to another Doctor.

#### Scenario: Doctor opens the Calendar page

- **WHEN** an authenticated Doctor selects Calendar from the Doctor workspace navigation
- **THEN** a separate Calendar page is displayed without replacing or removing the Dashboard or Patients page

#### Scenario: Calendar shows only the signed-in Doctor's appointments

- **WHEN** the Calendar page loads a selected week
- **THEN** it displays appointments assigned to the signed-in Doctor only

#### Scenario: Calendar has no appointments for the selected week

- **WHEN** the selected week contains no appointments for the signed-in Doctor
- **THEN** the time grid remains usable and displays an informative empty state without an application error

### Requirement: The Calendar SHALL support week navigation and custom week selection

The Calendar SHALL initially display the current week using the Doctor's saved first-day-of-week preference. It SHALL provide Today, previous-week, next-week, and week-range controls. The week-range control SHALL open a custom popup containing a month calendar with week numbers, selected-week highlighting, month navigation, year navigation, a year month grid, and a Today action. Selecting a week SHALL update the time grid to that week.

#### Scenario: Calendar opens on the current preferred week

- **WHEN** a Doctor opens Calendar without a previously selected week
- **THEN** the Calendar displays the week containing the current Singapore clinic date, ordered from the Doctor's preferred first day

#### Scenario: Doctor navigates to the adjacent week

- **WHEN** the Doctor selects the previous-week or next-week arrow
- **THEN** the Calendar displays the immediately preceding or following seven-day period and refreshes its appointments

#### Scenario: Doctor returns to the current week

- **WHEN** the Doctor selects Today
- **THEN** the Calendar displays the current preferred week and highlights the current date

#### Scenario: Doctor opens the custom week picker

- **WHEN** the Doctor selects the displayed week range
- **THEN** the custom picker opens with the displayed week selected, visible week numbers, month navigation, year navigation, and a year month grid

#### Scenario: Doctor selects a week from the custom picker

- **WHEN** the Doctor selects a date or week row in the custom picker
- **THEN** the picker closes and the Calendar displays the seven-day week containing that selection according to the Doctor's preferred first day

### Requirement: Calendar appointments SHALL be presented as status-aware time blocks

Each appointment overlapping the displayed week SHALL be represented in the appropriate day and time position, with its start and end times, permitted administrative patient display information, and lifecycle status. All `AppointmentStatus` values SHALL remain distinguishable through both text and visual treatment. Cancelled appointments SHALL be visibly muted but SHALL remain identifiable when the Calendar is displaying the relevant period. Clinical records, diagnoses, consultation notes, follow-up notes, and prescriptions SHALL not be displayed.

#### Scenario: Doctor views appointments in the time grid

- **WHEN** an appointment falls within or overlaps the displayed week
- **THEN** the Calendar shows a distinct block spanning its scheduled interval in the corresponding day column

#### Scenario: Doctor views appointments with different lifecycle states

- **WHEN** appointments in the displayed week have different lifecycle statuses
- **THEN** each block shows a readable status and a distinguishable status treatment, including for pending, accepted, checked-in, completed, checked-out, and cancelled appointments

#### Scenario: Appointment is outside configured working hours

- **WHEN** an appointment is scheduled outside the Doctor's configured working intervals
- **THEN** the appointment remains visible at its scheduled time on the greyed non-working background

#### Scenario: Calendar displays only non-clinical appointment information

- **WHEN** a Doctor views an appointment block
- **THEN** the block contains appointment timing, permitted administrative patient information, and status only, and contains no clinical information

### Requirement: The Calendar SHALL distinguish elapsed and non-working periods visually

The Calendar SHALL grey dates before the current Singapore clinic date, elapsed time on the current date, disabled working days, and intervals outside configured working hours. The Calendar SHALL show a current-time line in the current date column when the current date is displayed, and SHALL keep that indicator current while the page is open.

#### Scenario: Calendar greys past dates and elapsed time

- **WHEN** the displayed week contains dates before the current Singapore clinic date or time periods already elapsed today
- **THEN** those dates or time periods are visibly greyed while future periods remain readable

#### Scenario: Calendar greys disabled days and breaks

- **WHEN** a day is disabled or a gap exists between two configured working intervals
- **THEN** the disabled day or gap is visibly greyed as non-working time

#### Scenario: Calendar shows the current-time line

- **WHEN** the displayed week contains the current Singapore clinic date
- **THEN** a line is shown at the current local time in that date's column and is updated while the Calendar remains open

#### Scenario: Calendar displays a past or future week

- **WHEN** the Doctor navigates to a week that does not contain the current Singapore clinic date
- **THEN** no current-time line is shown in that week and the dates are shaded according to whether they are past or future

### Requirement: Doctors SHALL be able to configure calendar preferences and working intervals

The Calendar SHALL provide a settings icon that opens a separate Calendar settings page for the authenticated Doctor. The settings page SHALL allow the Doctor to choose the first day of the week and configure each day as enabled or disabled with one or more non-overlapping working intervals. The Doctor SHALL be able to add and remove intervals to represent breaks such as lunch. The page SHALL show the fixed Singapore clinic timezone as informational text and SHALL provide no work-location setting.

#### Scenario: Doctor opens Calendar settings

- **WHEN** the Doctor selects the Calendar settings icon
- **THEN** the separate settings page displays the saved first-day preference, daily working-day controls, working intervals, and Singapore timezone information without any location controls

#### Scenario: Doctor configures a break

- **WHEN** the Doctor enables a day and saves two working intervals separated by a gap
- **THEN** the two intervals are retained and the gap is rendered as grey non-working time in Calendar

#### Scenario: Doctor disables a working day

- **WHEN** the Doctor disables a day and saves the settings
- **THEN** that day is rendered as entirely non-working in Calendar and its disabled state is retained when settings are reopened

#### Scenario: Doctor changes the first day of the week

- **WHEN** the Doctor saves a different first-day-of-week preference
- **THEN** Calendar headers, week navigation, week numbers, and the custom week picker use the newly saved first day

#### Scenario: Doctor saves valid calendar settings

- **WHEN** the Doctor submits a first-day preference and valid daily working intervals
- **THEN** the settings are persisted for that Doctor and are used by subsequent Calendar views and after the Doctor signs in again

#### Scenario: Doctor abandons calendar-setting edits

- **WHEN** the Doctor cancels or leaves the settings page without saving
- **THEN** the previously persisted settings remain unchanged

#### Scenario: Doctor submits invalid working intervals

- **WHEN** a working interval has a missing or invalid time, an end that is not after its start, or an overlap with another interval on the same day
- **THEN** the settings are rejected with actionable feedback and the previously persisted settings remain unchanged

### Requirement: Calendar working hours SHALL remain visual preferences only

Calendar working intervals SHALL control shading and presentation only. They SHALL not become appointment availability constraints and SHALL not change the existing rules for booking, rescheduling, conflict detection, cancellation, check-in, completion, or checkout.

#### Scenario: Appointment is booked outside working hours

- **WHEN** a Receptionist or authorized Doctor books or reschedules an appointment into a period outside the Doctor's configured working intervals and no existing scheduling rule is violated
- **THEN** the appointment operation succeeds and the Calendar displays the appointment on the greyed period

#### Scenario: Calendar settings do not change appointment state

- **WHEN** a Doctor saves, changes, or removes a working interval
- **THEN** no appointment time, assignment, or lifecycle status is changed

### Requirement: Calendar preferences SHALL be protected by Doctor ownership

Only the Doctor who owns the preferences SHALL be able to read or change them. Attempts to access or change another Doctor's calendar preferences or schedule SHALL be rejected without exposing the other Doctor's data.

#### Scenario: Doctor accesses their own preferences

- **WHEN** an authenticated Doctor opens or saves Calendar settings for their own account
- **THEN** the operation is authorized and uses only that Doctor's preferences

#### Scenario: Doctor attempts to access another Doctor's preferences

- **WHEN** an authenticated Doctor attempts to read or change another Doctor's Calendar settings or appointment schedule
- **THEN** the operation is rejected and the other Doctor's preferences and appointments remain undisclosed and unchanged

### Requirement: Calendar controls SHALL be usable without relying on color alone

Calendar navigation, settings, picker actions, and appointment statuses SHALL have accessible names or readable text. Status and non-working distinctions SHALL remain understandable when color is unavailable.

#### Scenario: Doctor uses Calendar controls with assistive technology or keyboard navigation

- **WHEN** the Doctor navigates Calendar, opens the picker, changes settings, or saves preferences without relying on pointer-only interaction
- **THEN** each control has a readable label and a reachable action or validation outcome

#### Scenario: Doctor views a greyed period without color perception

- **WHEN** the Doctor views past or non-working periods without distinguishing the shading color
- **THEN** date, time, or state labels still communicate the period's meaning
