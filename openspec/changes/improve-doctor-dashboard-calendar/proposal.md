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
- `demo-data`: Covers representative local-development patients, appointments, historical clinical links, and showcase credentials.

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
- ArchUnit 1.5.0 is added as a test-only dependency; no database column or runtime dependency is added. Time-off removal uses the existing `doctor_time_off` identity and ownership data.

## Follow-up UI refinements

The completed change is extended with four presentation and navigation refinements:

- Let the Patient Directory results table consume all remaining vertical space in its full-height page card while retaining its own scrolling for larger result sets.
- Make the Doctor Calendar toolbar responsive so the Add appointment and Block time actions remain together and move to a second row when the available width is too small.
- Rename the Calendar modes to `Calendar` (the date-range time grid) and `Agenda` (the chronological stream).
- Replace the Agenda mode's week-oriented picker with a compact date picker for its inclusive start date; previous and next move that anchor by one day while Today and refresh retain their existing meanings.

## Follow-up reliability and settings simplification

The completed change is extended with two focused follow-ups:

- Keep every Patient Directory View action present after search results are refreshed, including when JavaFX reuses table cells for a new result set.
- Remove the obsolete Doctor Calendar first-day-of-week preference presentation now that Calendar uses an explicit date range and Agenda uses an explicit start date. Preserve the stored settings field and service/API shape for compatibility while keeping Work hours and the fixed clinic timezone available.

## Follow-up demo data and credentials

The completed change is extended with richer local-development seed data:

- Seed 18 representative patients, including both active and inactive directory rows.
- Seed appointments for every date from seven days before today through fourteen days after today, with both Doctors represented each day and varied lifecycle statuses.
- Link eligible historical `CHECKED_IN`, `COMPLETED`, and `CHECKED_OUT` appointments to deterministic clinical records, and link prescriptions to completed or checked-out consultations.
- Replace the long showcase Doctor and Receptionist usernames and passwords with shorter, clearly documented credentials that still satisfy the application's password policy.

## Follow-up selected appointment details

The Dashboard's selected-appointment pane will provide a clearer patient and
workflow context:

- Replace the patient identifier and "Selected appointment" label with a
  status-coloured banner containing the patient's name, appointment time, and
  readable lifecycle status.
- Add an authorized, read-only administrative patient-details view to the
  selected pane.
- Show lifecycle-specific Doctor actions: Accept, Decline, and Reschedule for
  pending appointments; Decline, Reschedule, and Check in for accepted
  appointments; and the existing consultation, prescription, and completion
  workflow for checked-in appointments.
- Keep declined, cancelled, completed, and checked-out selections
  non-mutating. Terminal selections retain patient context and expose saved
  clinical information read-only when available, while declined/cancelled
  selections show a short status explanation.
- Reuse the Calendar-style appointment editor for rescheduling so date/time
  validation and feedback remain consistent across Doctor entry points.

## Follow-up architecture checks

The project will add a pinned ArchUnit test dependency and executable package
boundary checks:

- Keep `domain` independent from UI, services, persistence, and tools.
- Keep `persistence` independent from UI, services, and tools.
- Keep `service` independent from UI and tools.
- Keep UI independent from tools and direct persistence access, except for the
  `ApplicationRouter` composition root.
- Keep the core package slices free of dependency cycles.
