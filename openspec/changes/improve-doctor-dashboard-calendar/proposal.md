## Why

The Doctor Dashboard's appointment list does not communicate the shape of a working day, while time-off controls are separated from the Calendar that doctors use to understand schedule availability. A compact day timeline on Dashboard and visible, reversible time-off blocks on Calendar will make daily clinical work and schedule planning clearer without changing appointment or working-hours policy.

## What Changes

- Replace the Dashboard's left-hand appointment list with a compact single-day timeline that defaults to the current Singapore clinic date.
- Add Today, previous-day, next-day, date-selection, and accessible manual-refresh controls, with useful initial scrolling to the current time or first relevant appointment.
- Polish the Dashboard card with a Dashboard heading, wrapped supporting copy, and clear separation between its controls and day timeline.
- Give every application date-picker field a shared minimal presentation while preserving its date-selection and accessibility behavior.
- Let selecting a Dashboard appointment block load the existing authorized clinical context and appointment actions in the right-hand pane.
- Move Doctor time-off creation from Dashboard to the Calendar page through a dedicated, prefillable dialog.
- Include the signed-in Doctor's time-off intervals in Calendar data, render them as labelled blocks distinct from visual working-hours shading, and allow the owning Doctor to remove a block.
- Release an appointment's former interval when it becomes `DECLINED`, while retaining the declined record for Receptionist coordination and applying normal conflict validation if it is rescheduled.
- Preserve proportional appointment duration, current-time and elapsed-period presentation, Doctor ownership, all other appointment lifecycle/conflict rules, and the rule that configured working hours are visual preferences only.

## Capabilities

### New Capabilities

- `doctor-dashboard-calendar`: Covers the Doctor Dashboard's compact single-day timeline, date navigation, refresh behavior, appointment selection, and clinical-detail coordination.

### Modified Capabilities

- `doctor-calendar`: Adds visible time-off blocks, Calendar-based time-off management, and manual schedule refresh while retaining existing privacy and presentation rules.
- `clinic-workflow`: Makes declined appointments non-blocking and adds authorized removal of a Doctor's own time-off interval while preserving all other appointment and time-off conflict behavior.
- `modernize-clinic-ui`: Applies the shared minimal date-picker treatment across application views and dialogs.

## Impact

- Doctor workspace composition and appointment-selection coordination in `DoctorView`.
- Shared calendar projection, calculations, timeline rendering, dialogs, styles, and Doctor Calendar controls.
- Appointment conflict queries plus service and repository reads/deletion for ranged, Doctor-owned time-off data.
- Doctor Dashboard, Calendar, service, persistence, and accessibility-focused tests.
- Shared date-picker factories, stylesheet rules, and cross-role UI regression tests.
- User and developer documentation describing the revised Dashboard and Calendar time-off workflow.
- No new external dependency or database column is expected; time-off removal uses the existing `doctor_time_off` identity and ownership data.

## Follow-up UI refinements

The completed change is extended with four presentation and navigation refinements:

- Let the Patient Directory results table consume all remaining vertical space in its full-height page card while retaining its own scrolling for larger result sets.
- Make the Doctor Calendar toolbar responsive so the Add appointment and Block time actions remain together and move to a second row when the available width is too small.
- Rename the Calendar modes to `Calendar` (the date-range time grid) and `Agenda` (the chronological stream).
- Replace the Agenda mode's week-oriented picker with a compact date picker for its inclusive start date; previous and next move that anchor by one day while Today and refresh retain their existing meanings.
