## Context

The completed weekly Calendar implementation is a JavaFX page composed by `DoctorView`. `DoctorCalendarView` owns the selected `CalendarWeek`, Today/previous/next controls, the custom week picker, the settings entry point, and a current-time `Timeline`; it currently loads one `DoctorCalendarWeek` and renders it with `CalendarTimeGrid`. The calendar projection already contains only appointment timing, administrative Patient display information, and `AppointmentStatus`.

`AppointmentRepository.findCalendarByDoctor` currently performs an authorized-by-service, bounded overlap query ordered by start time and identifier. SQLite already has `idx_appointments_doctor_time` on `(doctor_id, starts_at)`. The existing Dashboard schedule is a clinical action surface and must remain separate from this read-only Calendar projection. See `proposal.md` and `specs/doctor-calendar-schedule/spec.md` for the behavior contract.

## Goals / Non-Goals

**Goals:**

- Add a Google Calendar-inspired chronological Schedule renderer beside the existing weekly grid.
- Start Schedule at the current Singapore clinic date and support an unbounded forward stream through bounded lazy pages.
- Keep ordering deterministic, Doctor-scoped, administrative-only, read-only, and accessible.
- Reuse the current Calendar navigation, week picker, settings boundary, clock, and status treatment where their semantics remain appropriate.
- Keep page traversal efficient and stable when multiple appointments share a start time or appointments are added later.

**Non-Goals:**

- Replacing, simplifying, or adding actions to the weekly grid or Doctor Dashboard.
- Adding appointment recurrence, locations, all-day events, reminders, external synchronization, search, or filters.
- Making working hours or breaks hard availability constraints.
- Loading clinical records or introducing a second Patient/Doctor authorization path.
- Introducing an appointment or calendar-settings schema migration.

## Decisions

### Keep one Calendar page with two renderers

Add a compact, non-editable `Week` / `Schedule` selector to the existing Calendar toolbar. The selected mode determines whether the center contains `CalendarTimeGrid` or a new schedule-list renderer. The weekly renderer and its selected `CalendarWeek` remain intact; Schedule maintains a separate anchor date so switching modes does not turn the weekly week into an artificial data limit.

The Schedule toolbar retains Today, previous/next, the custom week-picker trigger, and settings. In Week mode the range trigger continues to show the selected range and week number. In Schedule mode it shows the anchor month/year, matching the reference layout, while the existing picker remains the jump surface. Today anchors at the current Singapore date. Previous and next shift the Schedule anchor by seven days; a picker date anchors to that date, while a picker week-row selection anchors to that week's first day. Every anchor change resets the loaded pages and scroll position.

Using the existing toolbar avoids a second navigation model and makes the Schedule view an additional presentation of the same Doctor Calendar page. It also allows the compact selector styling already used elsewhere in the application.

### Use keyset pagination rather than offsets or date windows

Introduce immutable schedule page values containing a list of `CalendarAppointment` projections, a nullable next cursor, and a `hasMore` indicator. A cursor contains the last emitted appointment's `startsAt` and `appointmentId`.

The repository query uses the signed-in Doctor scope, an inclusive anchor timestamp at the start of the selected Singapore clinic date, and a strict cursor condition:

```text
starts_at >= anchor
and (starts_at > cursorStartsAt
     or (starts_at = cursorStartsAt and id > cursorAppointmentId))
order by starts_at, id
limit pageSize + 1
```

The extra row determines `hasMore` without a count query. The service validates a bounded page size, returns only the authenticated Doctor's administrative projection, and keeps cancelled appointments in the result. The existing `(doctor_id, starts_at)` index supports the main range predicate; a new index or migration is not required for this feature.

Keyset pagination is preferred over `OFFSET` because inserting another appointment with an equal or earlier start time while the Doctor scrolls must not shift the page boundary and cause a duplicate or skipped row. Date-window pagination is also avoided because sparse future schedules would require repeated empty-window queries and would not provide a useful row-based page size.

### Use a virtualized list with explicit date-header and appointment rows

The Schedule renderer uses a virtualized JavaFX list whose entries are either date headers or appointment rows. It appends entries from each page and emits one date header per date, including when a page boundary splits a date group. Dates without appointments are omitted, as in the reference screenshot. An empty result has one informative empty-state node; a completed stream has an end marker.

Each appointment row contains a status marker, time range, Patient display name, and readable status label. The row uses the existing status-specific classes and text rather than color alone. A cross-midnight appointment is emitted once under its start date and shows the end date when the range crosses a date boundary. No location or all-day placeholder is fabricated because those fields are absent from the domain model.

`ListView` is preferable to constructing an ever-growing nested `VBox` in a `ScrollPane`: it virtualizes row cells while the loaded model grows, keeps keyboard navigation natural, and makes the near-end trigger easier to test. A loading row or overlay communicates an in-flight page request, and a retry action retries only the failed cursor page while retaining earlier rows.

### Trigger one bounded load at a time

The list observes its vertical viewport and requests the next page when the Doctor reaches a near-end threshold. A guard prevents multiple requests for the same cursor. The page size is a small application constant, such as 25 rows, so an individual local SQLite query and FX-thread append remain bounded. The renderer does not request a total count or prefetch the entire future schedule.

Because this application currently performs local SQLite reads synchronously through its existing service composition, the first implementation keeps page requests on the established UI path and relies on the bounded indexed query. If profiling shows a visible pause, the repository/service boundary remains isolated enough to move page loading to a JavaFX `Task` later without changing the page contract.

### Share clock and status semantics, not weekly shading

Schedule uses the `Clock` already injected into `DoctorCalendarView` for its initial anchor, Today behavior, current-date emphasis, and elapsed appointment cues. The existing one-minute timeline can update the current-date and elapsed-row classes while Schedule is active and remains stopped when the Calendar page is hidden or disposed.

Working-hour and break shading belongs to the time-grid representation. Schedule does not add artificial grey time bands, but it must retain appointments outside working intervals and may identify elapsed entries with text. This preserves the existing visual-only settings contract while keeping the chronological list legible.

### Keep service authorization and projection boundaries unchanged

The new schedule-page method is exposed through `CalendarService`, which already requires a Doctor session and resolves the actor's account identifier. The UI passes an anchor and cursor, never an arbitrary Doctor identifier. The repository continues joining only the administrative Patient fields needed for the existing `CalendarAppointment` projection. No clinical service, Dashboard selection state, appointment mutation service, or calendar-settings write path is called.

## Risks / Trade-offs

- [Risk] A cursor page can end in the middle of a date group. -> Mitigation: retain the current date-group state while appending and create a header only when the next row's date differs.
- [Risk] A new appointment is inserted while the Doctor is scrolling. -> Mitigation: order and cursor by both `starts_at` and unique appointment ID; refresh or re-anchor starts a new stream when the Doctor wants newly inserted earlier rows.
- [Risk] A large future schedule can grow the loaded list over a long session. -> Mitigation: load only bounded pages and use a virtualized list; the requested scope intentionally keeps already viewed rows for natural backward scrolling.
- [Risk] A synchronous page query could briefly delay JavaFX input. -> Mitigation: retain the indexed query and small page limit, show a bounded loading state, and leave an isolated path for a later background task.
- [Risk] Reusing the existing week-picker label in Schedule could imply a seven-day limit. -> Mitigation: display the Schedule anchor month/year while keeping picker selection as an anchor jump.
- [Risk] Past appointments on the current date may be mistaken for future entries. -> Mitigation: anchor at the start of today to show the complete day's schedule and add readable elapsed/past cues to earlier rows.
- [Risk] A sparse schedule may make a fixed date-window strategy feel empty or appear to stop. -> Mitigation: page by appointment rows with a cursor, not by calendar-day windows.

## Migration Plan

No database migration is required. Add the cursor/page query against the existing appointments and Patient tables and use the existing Doctor/start-time index. Deployment is additive: the weekly view, appointment rows, statuses, and calendar settings remain compatible. If Schedule is rolled back, the new renderer and page-read path can be removed while retaining all existing appointment and settings data.
