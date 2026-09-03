# Implementation Tasks

## 1. Schedule page contract and calculations

- [x] 1.1 Define immutable Schedule cursor and page projections containing the last `(startsAt, appointmentId)` position, bounded appointment results, `hasMore`, and validation for page limits; verify unit tests cover empty pages, terminal pages, equal start times, and invalid cursor/page values.
- [x] 1.2 Define Schedule anchor and row-grouping calculations for the Singapore clinic date, seven-day anchor movement, date-group boundaries, deterministic ordering, and cross-midnight display ranges; verify unit tests cover today, adjacent anchors, month/year boundaries, equal timestamps, and date-crossing appointments.

## 2. Authorized lazy appointment reads

- [x] 2.1 Add a Doctor-scoped keyset query that returns only appointments starting at or after an inclusive anchor and orders by `starts_at, id` with a bounded look-ahead row; verify repository tests prove page boundaries, no duplicates/skips, cancelled-status retention, and exclusion of another Doctor.
- [x] 2.2 Expose the paged read through the calendar service with Doctor ownership checks, bounded page-size validation, Singapore-date anchor handling, and the existing non-clinical Patient projection; verify service tests reject non-Doctors and cross-Doctor access without exposing clinical data.
- [x] 2.3 Wire the new page values and service method through the existing clinic-service composition without changing `getWeek`, appointment mutation APIs, or calendar-settings persistence; verify application-context construction and existing appointment/calendar service tests pass.

## 3. Calendar view-mode integration

- [x] 3.1 Add an accessible compact `Week` / `Schedule` selector to the Doctor Calendar toolbar and preserve the weekly grid as the default/alternate renderer; verify TestFX can switch modes and return to the same weekly Calendar behavior.
- [x] 3.2 Add Schedule anchor state and navigation behavior so initial entry and Today use the current Singapore date, previous/next move seven days, and the custom picker re-anchors by date or week start; verify UI tests reset the stream and scroll position after every anchor change without imposing a seven-day end boundary.
- [x] 3.3 Change the Calendar date label appropriately by mode, retaining the week range and number for Week and showing the Schedule anchor month/year for Schedule; verify UI tests assert both labels and confirm the existing custom picker remains reachable.

## 4. Chronological Schedule renderer

- [x] 4.1 Build a virtualized Schedule list with explicit date-header and appointment-row entries, omitting empty dates and preserving a single date header across page boundaries; verify UI tests show date groups and chronological rows in the expected order.
- [x] 4.2 Render each appointment row with time range, administrative Patient display name, readable lifecycle status, status-specific treatment, cancelled muting, and cross-midnight date information; verify tests cover every `AppointmentStatus`, equal start times, cancelled rows, and absence of clinical/location/all-day content.
- [x] 4.3 Add near-end lazy loading with one in-flight request per cursor, append-only page handling, loading state, empty state, end marker, and retry state that retains previously loaded rows; verify TestFX or service-backed UI tests demonstrate initial bounded loading, next-page append, terminal behavior, and recoverable errors.
- [x] 4.4 Add current-date emphasis and elapsed/past appointment cues using the injected Singapore clock while keeping working hours and breaks visual-only; verify fixed-clock tests identify today, mute elapsed rows, keep outside-hours appointments visible, and do not show weekly-grid-only shading as a scheduling restriction.
- [x] 4.5 Stop Schedule timers/listeners and clear page-owned state when the Calendar is hidden, disposed, re-anchored, or switched back to Week; verify lifecycle tests show no repeated page request or timer activity after disposal.

## 5. Regression, authorization, and accessibility coverage

- [x] 5.1 Add repository and service regressions for a large future appointment set, sparse dates, page-size boundaries, same-start-time appointments, newly inserted later appointments, empty results, and database failures; verify all page results remain Doctor-scoped and deterministic.
- [x] 5.2 Add Doctor Calendar TestFX coverage for mode switching, Today, previous/next, custom-picker re-anchoring, lazy scroll loading, date grouping, status text, current-date cues, empty/end/error states, and preservation of the weekly grid and settings entry point.
- [x] 5.3 Add accessibility and confidentiality assertions for view controls, loading/error/end states, date headers, appointment rows, non-color status cues, and absence of clinical information; verify keyboard-reachable controls expose readable accessible names.
- [x] 5.4 Verify Schedule navigation and view changes do not mutate appointment timestamps, assignments, statuses, or saved Calendar settings; verify the existing booking, rescheduling, status-transition, and visual-only working-hours tests remain green.

## 6. Documentation and delivery validation

- [x] 6.1 Update Doctor user and developer documentation to describe Week/Schedule switching, today-based lazy loading, anchor navigation, date-grouped appointment rows, status cues, loading states, and the absence of locations/all-day/clinical data; verify documentation links and examples match the final controls.
- [x] 6.2 Run the focused Schedule/Calendar tests, full Gradle quality gate, `git diff --check`, and strict OpenSpec validation for `doctor-calendar-schedule-view`; resolve failures before marking the change ready for implementation review.
