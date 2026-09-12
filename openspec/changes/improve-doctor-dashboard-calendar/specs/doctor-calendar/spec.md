## ADDED Requirements

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

#### Scenario: Doctor refreshes Schedule mode
- **WHEN** the Doctor activates refresh while viewing Schedule mode
- **THEN** Calendar reloads the Schedule using the same anchor and retains Schedule mode
