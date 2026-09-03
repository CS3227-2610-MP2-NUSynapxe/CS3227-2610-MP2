## Why

Doctors currently review assigned appointments in a chronological list embedded in the Dashboard. A weekly calendar gives a faster view of workload, gaps, elapsed time, and upcoming visits while keeping the existing Dashboard available for clinical work and appointment actions. Doctors also need personal calendar preferences so the week layout and shaded working periods match their schedule.

## What Changes

- Add a separate Doctor **Calendar** page with a seven-day time-grid view of the signed-in Doctor's appointments.
- Add Today, previous-week, next-week, and week-range navigation controls.
- Reproduce the reference custom month/year week-picker popup, including week numbers and selected-week highlighting.
- Grey past dates, past time periods, disabled days, and configured non-working intervals; show a live current-time indicator for the current day.
- Render appointment cards using the existing `AppointmentStatus` lifecycle values and status-specific visual treatment without changing lifecycle rules.
- Add a settings icon and Doctor-only **Calendar settings** page for preferred week start and multiple working intervals per day, including breaks such as lunch.
- Persist calendar settings per Doctor. Do not add work-location settings or controls.
- Keep working hours display-only: appointment booking, rescheduling, conflict checks, and existing Dashboard behavior remain unchanged, so appointments outside configured hours remain valid and visible.

## Capabilities

### New Capabilities

- `doctor-calendar`: Weekly Doctor calendar viewing, custom week selection, appointment presentation, and Doctor-owned calendar preferences.

### Modified Capabilities

None. Existing appointment scheduling requirements remain unchanged because calendar working hours are visual preferences only.

## Impact

- Extend the Doctor workspace navigation and add calendar/settings JavaFX views while preserving the existing Dashboard and Patients pages.
- Add calendar preference domain, persistence, service authorization, and schema migration support for per-Doctor settings and multiple daily intervals.
- Add an authorized week-range appointment query and administrative patient display data as needed by calendar cards; clinical data must not be exposed.
- Add JavaFX, service, persistence, migration, and calendar-navigation tests. No external calendar dependency is required.
