## 1. Calendar Availability Data

- [x] 1.1 Extend the Doctor Calendar range projection with an immutable time-off collection; verify constructor tests reject invalid data and preserve defensive copies.
- [x] 1.2 Add a range-bounded repository query for overlapping Doctor time off using strict interval-overlap semantics; verify repository tests cover in-range, boundary-adjacent, cross-midnight, and other-Doctor cases.
- [x] 1.3 Update the shared availability query so `DECLINED` and `CANCELLED` appointments do not block booking, rescheduling, or time-off creation while all active workflow statuses still conflict; verify service tests cover replacement booking, time off over a declined record, active-status conflicts, Receptionist rescheduling, and retained declined history.
- [x] 1.4 Add an owner-scoped time-off delete operation and Doctor-only service method with a safe zero-row outcome; verify service tests cover owner success, other-Doctor isolation, non-Doctor rejection, missing IDs, unchanged appointments, and post-removal conflict behavior.
- [x] 1.5 Populate ranged time off in authorized Doctor and Receptionist Calendar reads; verify `CalendarServiceTest` proves correct ownership, range filtering, and no clinical or patient leakage.

## 2. Shared Timeline Presentation

- [x] 2.1 Extend shared calendar calculations to clip appointment and time-off intervals to each day while preserving duration; verify unit tests cover partial-day, range-boundary, cross-midnight, 30-minute, and 60-minute geometry.
- [x] 2.2 Introduce explicit full-Calendar and compact-Dashboard grid profiles, retaining the full profile's existing row geometry and using minimal select-only appointment content in compact mode; verify `DoctorCalendarViewTest` keeps existing containment assertions and new tests prove compact narrow-column containment plus proportional duration.
- [x] 2.3 Render labelled time-off blocks above working-hours shading and below the current-time line; verify JavaFX tests cover their text, style, accessibility, selection behavior, and day-boundary clipping, and confirm declined/cancelled appointments produce no block.
- [x] 2.4 Add reusable timeline scrolling and empty-state behavior for current time, earliest appointment or time off, first working interval, and an otherwise empty day; verify deterministic clock-based JavaFX tests cover all four initial-scroll paths and full-day accessibility.

## 3. Doctor Calendar Time-Off Workflow

- [x] 3.1 Add a dedicated time-off dialog using date and half-hour time selectors with sensible visible-range defaults and actionable validation; verify JavaFX tests cover prefilling, valid submission, invalid ordering, conflicts, cancellation, and retained input after failure.
- [x] 3.2 Add accessible Block time and icon refresh controls to Doctor Calendar while preserving empty-slot appointment creation and Week/Schedule state; verify UI tests prove refresh retains range/mode/anchor and empty-slot activation still opens the appointment dialog.
- [x] 3.3 Add Doctor time-off detail and confirmed removal interactions, and keep Receptionist Calendar time-off blocks read-only; verify JavaFX tests cover confirmed removal, cancelled confirmation, immediate refresh, and absence of Receptionist removal controls.

## 4. Doctor Dashboard Day Calendar

- [x] 4.1 Build the focused Dashboard day component with Singapore-date state, Today, previous/next, date picker, accessible refresh, compact timeline, current-time ticker, and show/hide lifecycle; verify a dedicated TestFX suite covers default date, navigation, refresh retention, empty state, scrolling, and ticker visibility.
- [x] 4.2 Replace the Dashboard appointment list with the day component, remove the Dashboard time-off form, and move check-in/accept/reschedule controls into the selected-appointment detail pane without changing their service calls; verify `DoctorViewTest` covers layout, removed controls, action availability, and successful existing appointment workflows.
- [x] 4.3 Connect timeline selection to authorized appointment and clinical-detail loading, retaining a still-visible selection on refresh and clearing stale selection on date/status changes; verify `DoctorViewTest` covers pending through checked-out selection, declined/cancelled absence, cross-date clearing, refresh reconciliation, other-Doctor isolation, and no patient identifier in timeline blocks.

## 5. Integrated Quality Verification

- [x] 5.1 Run Spotless and the focused repository, service, Doctor Calendar, Receptionist Calendar, and Doctor Dashboard test classes with the repository JDK; verify every focused test passes without treating a non-reproducing TestFX lookup failure as a product regression.
- [x] 5.2 Run `spotlessCheck check javadoc --offline --no-daemon --console=plain` before documentation updates; verify all tests, Checkstyle, PMD, SpotBugs, JaCoCo verification, formatting, and Javadoc complete successfully.

## 6. Documentation and Final Validation

- [x] 6.1 Update the User Guide and Developer Guide for the single-day Dashboard, declined/cancelled non-blocking semantics, Calendar time-off creation/removal, availability presentation, refresh controls, authorization, projection shape, and stable TestFX IDs; verify the documented workflow and identifiers match the tested implementation.
- [x] 6.2 Rerun `spotlessCheck check javadoc --offline --no-daemon --console=plain`, validate `improve-doctor-dashboard-calendar` strictly with OpenSpec, validate the main specs, and run `git diff --check`; verify all gates pass and review the scoped diff for accidental source, generated-file, or unrelated changes.
- [x] 6.3 Polish the Dashboard heading, wrapped guidance copy, toolbar-to-grid spacing, and all application date-picker fields with the shared minimal treatment; verify representative Doctor, Calendar, Receptionist, appointment-dialog, and time-off-dialog controls retain their behavior and use the common styling marker.
