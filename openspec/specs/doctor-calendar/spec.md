# doctor-calendar Specification

## Purpose

Provides Doctors with personal Calendar and Agenda presentations for reviewing assigned appointments, time off, and visual working intervals without changing appointment scheduling rules.

## Requirements

### Requirement: Doctors SHALL have a separate Calendar page

An authenticated Doctor SHALL be able to open a Calendar page in addition to the existing Dashboard and Patients pages. The Calendar page SHALL show a configurable date-range time grid and chronological Agenda for the signed-in Doctor and SHALL not expose appointments belonging to another Doctor.

#### Scenario: Doctor opens the Calendar page

- **WHEN** an authenticated Doctor selects Calendar from the Doctor workspace navigation
- **THEN** a separate Calendar page is displayed without replacing or removing the Dashboard or Patients pages

#### Scenario: Calendar shows only the signed-in Doctor's appointments

- **WHEN** the Calendar page loads a selected date range
- **THEN** it displays appointments assigned to the signed-in Doctor only

#### Scenario: Calendar has no appointments for the selected range

- **WHEN** the selected range contains no appointments for the signed-in Doctor
- **THEN** the time grid remains usable and displays an informative empty state without an application error

### Requirement: Calendar SHALL support explicit date-range navigation

Calendar mode SHALL provide explicit From and To date controls for an inclusive range. It SHALL provide Today and previous/next controls while keeping the selected range visible and usable without a week-number column or week-mode selector.

#### Scenario: Calendar opens on the current range

- **WHEN** a Doctor opens Calendar without a previously selected range
- **THEN** Calendar displays a range containing the current Singapore clinic date

#### Scenario: Doctor navigates the displayed range

- **WHEN** the Doctor selects the previous or next control
- **THEN** Calendar moves the displayed range and refreshes its appointments and time off

#### Scenario: Doctor returns to today

- **WHEN** the Doctor selects Today
- **THEN** Calendar displays a range containing and highlighting the current Singapore clinic date

#### Scenario: Doctor selects an explicit range

- **WHEN** the Doctor selects valid From and To dates
- **THEN** Calendar displays every date in that inclusive range without a week-number column

### Requirement: Calendar appointments SHALL be presented as status-aware time blocks

Each appointment with a status other than `DECLINED` or `CANCELLED` that overlaps the displayed range SHALL be represented in the appropriate day and time position, with its start and end times, the patient's name, and lifecycle status. Patient identifiers SHALL NOT be displayed in the appointment block. Displayed `AppointmentStatus` values SHALL remain distinguishable through both text and visual treatment. Declined and cancelled appointments SHALL not be rendered in the Doctor Calendar. Clinical records, diagnoses, consultation notes, follow-up notes, and prescriptions SHALL not be displayed.

#### Scenario: Doctor views appointments in the time grid

- **WHEN** a non-declined, non-cancelled appointment falls within or overlaps the displayed range
- **THEN** the Calendar shows a distinct block spanning its scheduled interval in the corresponding day column

#### Scenario: Doctor does not see declined or cancelled appointments
- **WHEN** the displayed range contains a `DECLINED` or `CANCELLED` appointment
- **THEN** that appointment is absent from the Doctor Calendar while remaining available to authorized receptionist management views when applicable

#### Scenario: Doctor views appointments with different lifecycle states

- **WHEN** appointments in the displayed range have `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` lifecycle statuses
- **THEN** each block shows the patient's name, a readable status, and a distinguishable status treatment

#### Scenario: Appointment is outside configured working hours

- **WHEN** an appointment is scheduled outside the Doctor's configured working intervals
- **THEN** the appointment remains visible at its scheduled time on the greyed non-working background

#### Scenario: Calendar displays only non-clinical appointment information

- **WHEN** a Doctor views an appointment block
- **THEN** the block contains appointment timing, the patient's name, and status only, and contains no clinical information or patient identifier

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

- **WHEN** the Doctor opens a Calendar range
- **THEN** the first day column begins immediately after the time-axis region without an additional empty white column or gap

#### Scenario: Appointment blocks are narrower than their day column

- **WHEN** overlapping appointments are rendered in one day column
- **THEN** every block is clipped or sized to its assigned lane and no block content or action control extends into another day column

#### Scenario: Narrow appointment block displays administrative content

- **WHEN** an appointment block has limited width
- **THEN** the patient's name and status remain readable or are safely truncated, and no patient identifier is added to compensate for the limited space

### Requirement: The Calendar SHALL distinguish elapsed and non-working periods visually

The Calendar SHALL grey dates before the current Singapore clinic date, elapsed time on the current date, disabled working days, and intervals outside configured working hours. The Calendar SHALL show a current-time line in the current date column when the current date is displayed, and SHALL keep that indicator current while the page is open.

#### Scenario: Calendar greys past dates and elapsed time

- **WHEN** the displayed range contains dates before the current Singapore clinic date or time periods already elapsed today
- **THEN** those dates or time periods are visibly greyed while future periods remain readable

#### Scenario: Calendar greys disabled days and breaks

- **WHEN** a day is disabled or a gap exists between two configured working intervals
- **THEN** the disabled day or gap is visibly greyed as non-working time

#### Scenario: Calendar shows the current-time line

- **WHEN** the displayed week contains the current Singapore clinic date
- **THEN** a line is shown at the current local time in that date's column and is updated while the Calendar remains open

#### Scenario: Calendar displays a past or future range

- **WHEN** the Doctor navigates to a range that does not contain the current Singapore clinic date
- **THEN** no current-time line is shown and the dates are shaded according to whether they are past or future

### Requirement: Doctors SHALL be able to configure working intervals

The Calendar SHALL provide a settings icon that opens a separate Calendar settings page for the authenticated Doctor. The settings page SHALL allow the Doctor to configure each day as enabled or disabled with one or more non-overlapping working intervals. The Doctor SHALL be able to add and remove intervals to represent breaks such as lunch. The page SHALL show the fixed Singapore clinic timezone as informational text and SHALL provide no first-day or work-location setting.

#### Scenario: Doctor opens Calendar settings

- **WHEN** the Doctor selects the Calendar settings icon
- **THEN** the separate settings page displays daily working-day controls, working intervals, and Singapore timezone information without first-day or location controls

#### Scenario: Doctor configures a break

- **WHEN** the Doctor enables a day and saves two working intervals separated by a gap
- **THEN** the two intervals are retained and the gap is rendered as grey non-working time in Calendar

#### Scenario: Doctor disables a working day

- **WHEN** the Doctor disables a day and saves the settings
- **THEN** that day is rendered as entirely non-working in Calendar and its disabled state is retained when settings are reopened

#### Scenario: Doctor saves valid calendar settings

- **WHEN** the Doctor submits valid daily working intervals
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

### Requirement: Doctors SHALL manage time off from Calendar

The Doctor Calendar page SHALL provide a dedicated Block time action instead of presenting time-off entry on Dashboard. The action SHALL open a dialog with date, start-time, and end-time inputs. A successful submission SHALL create time off for the signed-in Doctor and refresh the Calendar; invalid or conflicting intervals SHALL be rejected without changing the schedule.

#### Scenario: Doctor opens the Block time dialog
- **WHEN** the Doctor selects Block time from Calendar
- **THEN** a dialog opens with date, start-time, and end-time inputs for the Doctor's own schedule

#### Scenario: Doctor blocks an available interval
- **WHEN** the Doctor submits a valid interval that does not overlap another appointment or time-off interval
- **THEN** the interval is persisted for that Doctor and appears on Calendar after refresh

#### Scenario: Doctor attempts to block an occupied interval
- **WHEN** the submitted interval overlaps an appointment or existing time-off interval for the Doctor
- **THEN** the operation is rejected with actionable feedback and no interval is added

#### Scenario: Doctor removes time off
- **WHEN** the Doctor selects one of their time-off blocks, chooses Remove, and confirms the action
- **THEN** the interval is deleted and Calendar refreshes without that block

#### Scenario: Doctor cancels time-off removal
- **WHEN** the Doctor declines the removal confirmation
- **THEN** the time-off interval remains unchanged

### Requirement: Calendar SHALL present Doctor time off

Calendar time-grid mode SHALL display time-off intervals as labelled foreground blocks at their actual date, start time, end time, and proportional duration. Time-off blocks SHALL be visually and textually distinct from appointments and from visual working-hours shading. Declined and cancelled appointments SHALL create no appointment or availability block because neither status reserves its former interval.

#### Scenario: Time off appears in a displayed range
- **WHEN** a Doctor-owned time-off interval overlaps the displayed Calendar range
- **THEN** each overlapping day displays the applicable portion as a labelled time-off block

#### Scenario: Time off crosses midnight
- **WHEN** a time-off interval crosses from one displayed date into another
- **THEN** Calendar displays correctly clipped portions on both dates with their combined geometry representing the full interval

#### Scenario: Calendar contains a declined appointment
- **WHEN** a declined appointment overlaps the displayed range
- **THEN** Calendar displays no appointment or availability block for that record and its former interval appears free unless another appointment or time-off interval occupies it

#### Scenario: Doctor distinguishes working-hours shading from time off
- **WHEN** a time-off block occurs inside or outside configured working hours
- **THEN** the labelled block remains distinguishable from the background working-hours treatment without changing either conflict policy

#### Scenario: Receptionist views a Doctor Calendar
- **WHEN** a Receptionist views a selected Doctor's time grid
- **THEN** the same non-clinical time-off presentation is shown without exposing a time-off removal action

### Requirement: Calendar mode labels SHALL describe their presentations

The Doctor Calendar mode selector SHALL label the date-range time grid
`Calendar` and the chronological appointment stream `Agenda`. The labels SHALL
not change the authorized projection, lifecycle behavior, or privacy boundary
of either mode.

#### Scenario: Doctor chooses the date-range grid
- **WHEN** the Doctor opens the mode selector and chooses `Calendar`
- **THEN** the configurable From/To time grid is shown

#### Scenario: Doctor chooses the chronological stream
- **WHEN** the Doctor opens the mode selector and chooses `Agenda`
- **THEN** the chronological date-grouped appointment stream is shown

### Requirement: Agenda SHALL navigate by an inclusive start date

Agenda mode SHALL provide a compact date picker between previous and next
controls for its inclusive start date. Selecting a date SHALL reload the
stream from that date. Previous and next SHALL move the anchor exactly one
calendar day, Today SHALL restore the current Singapore clinic date, and
manual refresh SHALL retain the anchor. The existing lazy pagination and
elapsed-row presentation SHALL remain unchanged.

#### Scenario: Doctor selects an Agenda start date
- **WHEN** the Doctor chooses a valid date in the Agenda date picker
- **THEN** the stream reloads from that inclusive date

#### Scenario: Doctor moves Agenda by one day
- **WHEN** the Doctor activates previous or next in Agenda mode
- **THEN** the inclusive start date moves exactly one day backward or forward

#### Scenario: Doctor refreshes an Agenda anchor
- **WHEN** the Doctor activates refresh after selecting an Agenda start date
- **THEN** the stream reloads from the same date and remains in Agenda mode

### Requirement: Doctor Calendar SHALL support accessible manual refresh

The Doctor Calendar SHALL provide a manual refresh control that reloads the active Calendar mode and current selected range without changing the selected mode, range, or schedule anchor. An icon-only control SHALL have an accessible name and tooltip.

#### Scenario: Doctor refreshes time-grid mode
- **WHEN** the Doctor activates refresh while viewing a selected time-grid range
- **THEN** Calendar reloads appointments and availability for the same range

#### Scenario: Doctor refreshes Agenda mode
- **WHEN** the Doctor activates refresh while viewing Agenda mode
- **THEN** Calendar reloads the Agenda using the same anchor and retains Agenda mode

### Requirement: Calendar settings SHALL omit the obsolete first-day preference

The Doctor Calendar settings page SHALL not expose a Calendar Preferences card or
a “Show the first day of the week as” selector. Calendar date ranges and Agenda
start dates are selected explicitly. Work-hours editing and the fixed
`Asia/Singapore` timezone information SHALL remain available, and saving SHALL
preserve the existing stored first-day value internally for compatibility.

#### Scenario: Doctor opens Calendar settings

- **WHEN** the Doctor opens Calendar settings
- **THEN** no first-day selector or Calendar Preferences card is shown, while Work hours and the timezone information remain visible
