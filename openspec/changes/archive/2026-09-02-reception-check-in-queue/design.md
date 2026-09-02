## Context

The application is a Java 25 JavaFX desktop client backed by SQLite. `AppointmentService` already authorizes Receptionists to check in appointments and enforces lifecycle and start-time rules. `AppointmentRepository.search` already supports administrative date, Doctor, patient, and status filters; the existing Receptionist workspace has separate feature tabs and automatic refresh patterns.

## Goals / Non-Goals

**Goals:**

- Add a dedicated queue view optimized for front-desk arrivals.
- Reuse service-layer authorization and appointment transitions rather than duplicating rules in JavaFX.
- Keep queue rows and details administrative-only.
- Provide stable semantic IDs and deterministic TestFX interactions.
- Refresh rows and counts after filters and successful check-ins.

**Non-Goals:**

- No new appointment lifecycle state such as `NO_SHOW`.
- No clinical record editing or display.
- No check-in audit timestamp unless the existing domain model is later expanded in a separate change.
- No notifications, recurring appointments, triage, or patient self-service.

## Decisions

Use a new top-level `Check-in Queue` feature tab rather than adding more controls to the scheduling dashboard. This separates booking and schedule coordination from the arrival workflow while preserving the existing appointment tab.

Build the queue from the existing administrative appointment search path. The default date is `LocalDate.now(Asia/Singapore)`, and the default status view includes `ACCEPTED` and `CHECKED_IN`; explicit status filtering can narrow the result. Derive waiting and checked-in counts from the same result set used for the list.

Use a patient/appointment list with stable IDs and a single-selection details popup. The popup loads the administrative patient projection and appointment data only. A `Check in patient` action is disabled unless the appointment is accepted and the current Singapore time is at or after its scheduled start.

Keep check-in authorization and early/invalid transition validation in `AppointmentService`. The UI catches validation, authorization, and SQL failures and displays existing non-sensitive feedback. On success, close or update the popup and invoke the queue refresh callback.

Reuse the existing patient and Doctor searchable selector patterns for filters. Do not add a manual refresh button; date, selector, query, and successful writes trigger the same refresh function.

## Risks / Trade-offs

- [Risk] The queue can become stale while it is open → refresh after every filter change and successful check-in; provide a date filter so staff can reopen the current day.
- [Risk] A patient may have multiple appointments on the same day → display scheduled time and Doctor in every row and operate on the selected appointment ID.
- [Risk] Administrative details could accidentally expose clinical fields → use only the existing administrative patient service and explicitly test that clinical data is absent.
- [Risk] Current lifecycle has no no-show state → leave no-show tracking out of scope rather than inventing a status that affects checkout and reporting.

## Migration Plan

No database migration is required. Additive UI, repository/service query reuse, and tests are backward compatible. If implementation fails, remove the queue tab and related presentation code; existing appointment scheduling and check-in service behavior remains intact.
