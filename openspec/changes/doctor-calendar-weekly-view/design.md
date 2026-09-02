## Context

The existing JavaFX application builds views programmatically and uses only `javafx.controls`. The Doctor workspace currently keeps a chronological appointment `ListView` and clinical master-detail controls in `DoctorView`, with Dashboard and Patients navigation. `Appointment` stores local start/end timestamps, patient and Doctor identifiers, and an `AppointmentStatus`; the appointment service already enforces Doctor ownership for schedule access. SQLite schema version 4 stores appointments and one-off `doctor_time_off` intervals, but no recurring calendar preferences or working hours. See `proposal.md` and `specs/doctor-calendar/spec.md` for the motivation and behavior contract.

## Goals / Non-Goals

**Goals:**

- Add a maintainable Calendar page without replacing the existing Dashboard workflow.
- Render a readable, scrollable seven-day time grid with status-aware appointment blocks, elapsed-time shading, configurable work-period shading, and a live Singapore-time indicator.
- Reproduce the reference week-picker interaction with keyboard-accessible week-row selection, month navigation, year navigation, and a year month grid.
- Persist Doctor-owned first-day and multiple daily working intervals, including breaks, with atomic validation and save behavior.
- Keep appointment and patient access behind service-layer authorization and limit calendar content to permitted administrative information.
- Make calendar calculations and time-dependent behavior deterministic in unit and TestFX tests.

**Non-Goals:**

- Appointment creation, drag-and-drop, rescheduling, cancellation, check-in, consultation, checkout, or other mutation actions from the Calendar page; the Dashboard remains the action surface.
- Using working hours as appointment availability or conflict rules. Existing booking and rescheduling behavior remains authoritative.
- Work locations, doctor-selected time zones, reminders, recurrence, external calendar synchronization, or a Receptionist calendar-settings page.
- Replacing or modifying the lifecycle values in `AppointmentStatus`.

## Decisions

### Separate calendar and settings views

Add a focused `DoctorCalendarView` for the weekly grid and a `DoctorCalendarSettingsView` for preferences. `DoctorView` remains the Doctor workspace composition root and adds Calendar navigation alongside Dashboard and Patients. The settings page is another page in the authenticated workspace, reached by an icon button from Calendar and returning through a visible Back action; it is not a separate application window.

This keeps the current Dashboard's appointment-selection and clinical state independent from calendar navigation. A calendar appointment block is read-only in this change. A future change can add a hand-off to Dashboard selection without coupling the renderer to clinical controls.

### Calendar service boundary and read projection

Introduce a calendar-facing service that owns authorized weekly reads and Doctor-owned preference reads/writes. The service checks that the actor is a Doctor and uses the actor's account identifier as the Doctor scope; the UI never supplies an arbitrary Doctor identifier for these operations. Existing `AppointmentService` remains responsible for appointment lifecycle and scheduling mutations.

The weekly appointment read uses an overlap range rather than filtering only by start date:

```text
appointment.startsAt < weekEnd
and appointment.endsAt > weekStart
```

This includes appointments crossing a day or week boundary. The result is a safe calendar projection containing appointment timing, Patient ID/name or other permitted administrative display data, and status. It does not load clinical records or prescriptions. Cancelled appointments remain in the projection and use a muted treatment; because cancelled records do not participate in existing conflict checks, the renderer must keep overlapping cancelled and active blocks separately identifiable.

### Persist preferences as a Doctor-owned aggregate

Use additive schema version 5 tables rather than adding calendar columns to `users`:

- `doctor_calendar_settings`: one row per Doctor containing `doctor_id` and a validated `first_day_of_week` value.
- `doctor_working_intervals`: zero or more rows per Doctor/day containing the day and start/end minute-of-day values.

A day with no interval rows is disabled. Multiple rows allow split shifts and breaks. Store times as integer minutes from `00:00` through `24:00`, so an interval ending at midnight can be represented without treating Java `LocalTime.MIDNIGHT` as earlier than a morning start. A saved day must have intervals with `0 <= start < end <= 1440`, and intervals on one day must not overlap. Replacing a Doctor's complete settings snapshot occurs in one transaction after validation, so a failed save cannot leave a partially updated week.

For a Doctor with no saved settings, use a deterministic initial profile of Sunday as the first day, Monday through Friday from 08:00 to 18:00, and no weekend intervals. These are display defaults only and can be changed immediately from Calendar settings. The fixed informational timezone is `Asia/Singapore`; it is not a persisted preference.

### Week model and navigation

Represent the displayed week as a start date plus the saved first-day preference. `Today` computes the current Singapore date and the containing preferred week. Previous and next move exactly seven days. Week numbers are calculated using the configured first day with a one-day minimum-week rule so the number corresponds to the visible week rows even when the Doctor changes between Sunday and Monday starts.

The toolbar contains Today, previous/next buttons, a range button showing the date range and week number, and an icon-only settings button with accessible text. Opening Calendar starts at the current preferred week; entering Calendar or selecting a different week reloads the range.

### Custom week-picker popup

Build a custom, anchored JavaFX popup rather than using the stock date picker. Its layout is:

```text
+------------------------------------------------+
| month calendar with week numbers | year/months |
| selected week highlighted        | Today       |
+------------------------------------------------+
```

The month pane renders seven day columns in the Doctor's preferred order, a week-number column, month navigation, and a highlighted row for the displayed week. The year pane provides year navigation and a twelve-month grid. Choosing a date or week row resolves to the containing preferred week, updates the Calendar, and closes the popup. The popup owns focus while open, exposes readable labels for its controls, and closes through an explicit close action or an outside click without losing the previously selected week.

### Time-grid rendering

Render the grid as a layered, scrollable surface with a fixed time axis and one column per displayed date. Use a minute-to-pixel mapping for appointment blocks so intervals are positioned proportionally even when their duration is not exactly one grid slot. Keep a minimum day-column width and horizontal scrolling at the existing application minimum window size so event text remains legible.

The background layer draws hour and half-hour guides. A shading layer marks past dates, elapsed time today, disabled days, and gaps/outside portions of each configured working interval. Appointment blocks are rendered above the shading layer and retain their timing and status text, so an appointment outside working hours is visible rather than hidden. A small lane-allocation step prevents overlapping blocks from obscuring one another.

Use existing semantic status styling as the visual starting point, while also placing readable status text on every block so status is not conveyed by color alone. The current-time layer is shown only when the displayed week contains the current Singapore date. It is positioned from the injected clock and refreshed once per minute; the refresh timeline is stopped when the Calendar view is removed or disposed.

### Calendar settings interaction

The settings page has a first-day `ComboBox`, a read-only Singapore timezone label, and one working-hours section per day. Each day row has an enabled checkbox, one or more start/end selectors, an Add interval action, and Remove actions for extra intervals. Disabled rows disable or hide interval editing and save with no interval rows. Time selectors use consistent half-hour increments and include a midnight end option represented as minute 1440.

Save validates the complete draft before persistence. Invalid or overlapping intervals keep the page open with field-level feedback and leave the stored snapshot untouched. Cancel/back discards the draft. After a successful save, the Calendar reloads the current week using the new ordering and shading rules; it does not reload or mutate appointments.

### Authorization and confidentiality

The calendar service is wired into `ClinicServices` and is the only application-facing path used by the Doctor views for calendar reads and settings. Doctor ownership is checked for both schedule and preferences. Calendar projections join or retrieve only the administrative Patient fields needed for display. No calendar operation calls clinical services, and settings are not exposed through Receptionist or System Admin screens.

### Testability

Keep week normalization, week-number calculation, interval validation, shading classification, and event placement in small deterministic collaborators. Provide a fixed-clock construction path for tests. Cover persistence and schema migration with temporary SQLite databases; cover Doctor ownership and non-blocking working hours at the service layer; and cover navigation, picker visibility/selection, settings save/cancel, break shading, status text, grey periods, and current-time rendering with TestFX and stable node identifiers.

## Risks / Trade-offs

- [Risk] A custom popup has more focus and boundary behavior than a stock date picker. -> Mitigation: implement explicit focus/keyboard handling, accessible labels, outside-click dismissal, and TestFX coverage for month/year/week-row selection.
- [Risk] Seven columns may be too narrow at the current 980-pixel minimum window width. -> Mitigation: use minimum day widths with horizontal scrolling and preserve a fixed time axis.
- [Risk] `LocalDateTime` appointment values do not carry a timezone. -> Mitigation: use the fixed Singapore clinic zone consistently for Today, elapsed shading, week initialization, and the current-time line; inject the clock for tests.
- [Risk] Cancelled appointments can overlap active appointments because existing conflict checks ignore cancelled rows. -> Mitigation: retain all status blocks but allocate separate visual lanes so no block is hidden.
- [Risk] Visual-only working hours could be mistaken for hard availability limits. -> Mitigation: label the settings as calendar display preferences and keep the non-blocking behavior covered by a service regression test.
- [Risk] A malformed settings draft could partially replace valid preferences. -> Mitigation: validate all days and replace the persisted snapshot in one transaction.
- [Risk] A minute-of-day model must handle midnight correctly. -> Mitigation: use the explicit 1440 end-minute representation and validate the full range before saving.
- [Risk] A live refresh timer could outlive a removed page. -> Mitigation: expose view disposal or visibility lifecycle handling and stop the timer when Calendar is no longer displayed.

## Migration Plan

1. Add schema version 5 with the two calendar preference tables and indexes/constraints required for Doctor ownership, day ordering, and valid interval ranges.
2. Migrate existing databases transactionally and create the deterministic default preference snapshot for existing Doctor accounts without changing appointment rows or statuses.
3. Deploy the read-only Calendar and settings paths. On first Calendar access, load the persisted snapshot and display the current preferred week.
4. If the feature is rolled back, the additive tables can remain unused by an older application build; restoring a database backup is the rollback path for removing the new preference data. Appointment data is never rewritten by this feature.
