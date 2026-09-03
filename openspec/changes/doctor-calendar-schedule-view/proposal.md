## Why

The Doctor Calendar currently offers a weekly time-grid view, but reviewing a long run of upcoming appointments requires repeatedly changing weeks. A Google Calendar-inspired Schedule view will give Doctors a continuous chronological agenda while loading only the portion they have reached.

## What Changes

- Add a `Week` / `Schedule` view selector to the existing Doctor Calendar page while preserving the weekly grid.
- Add a Schedule view that initially starts at the current Singapore clinic date and displays today's and all later appointments without a seven-day limit.
- Load Schedule appointments in bounded pages as the Doctor scrolls near the end of the currently loaded content, using a stable chronological cursor so appointments are not skipped or duplicated.
- Group schedule rows by date, sort appointments by start time, and show time, permitted administrative Patient information, lifecycle status text, and status treatment.
- Keep Today, previous/next navigation, and the custom week picker available as Schedule anchor controls: Today returns to the current date, while other date controls re-anchor the unbounded stream.
- Show cancelled appointments as muted entries and keep appointments outside configured working hours visible. Past appointments from the current date may remain visible but are visually muted.
- Preserve Doctor-only authorization, the non-clinical calendar projection, existing appointment lifecycle rules, and visual-only working-hours behavior.
- Provide loading, empty, end-of-results, and recoverable-error states for the lazy stream.
- Do not add work locations, all-day events, recurrence, external calendar integration, or appointment mutation actions.

## Capabilities

### New Capabilities

- `doctor-calendar-schedule`: Lazy chronological Schedule view for a Doctor's appointments from a selected anchor date forward.

### Modified Capabilities

- None. The existing weekly Calendar behavior remains available and unchanged; the Schedule view is an additional capability.

## Impact

- Extend the existing `DoctorCalendarView` with view-mode state and a Schedule renderer while retaining the current `CalendarTimeGrid`.
- Add an authorized, cursor-based appointment-page API in the calendar service/repository layer and small page/cursor projections; use the existing Doctor/start-time index and administrative Patient projection.
- Add JavaFX styling and stable identifiers for the selector, date groups, appointment rows, loading states, and stream controls.
- Add repository, service, calculation, and TestFX coverage for ordering, page boundaries, authorization, navigation anchors, lazy loading, status presentation, and empty/error states.
- Update Doctor user and developer documentation. No appointment schema, status, booking, rescheduling, or calendar-settings migration is required.
