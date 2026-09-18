## Context

See `proposal.md` for motivation and the delta specs for observable behavior.

`DoctorView` currently builds a `ListView<Appointment>` and appointment controls in the Dashboard's left pane, while the right pane contains selection summary, time-off entry, consultation, prescription, and completion cards. `CalendarTimeGrid` already renders an arbitrary non-empty list of dates and owns proportional time geometry, current-time presentation, working-hours shading, and appointment interaction hooks. Its full Calendar presentation uses tall 30-minute rows so inline appointment actions remain contained.

`CalendarService` currently returns `DoctorCalendarWeek` projections containing settings and visible appointments only. `AppointmentRepository` persists `DoctorTimeOff`, enforces it during booking and rescheduling, and can list all intervals for a Doctor, but has no range query or removal operation. Appointment conflict detection currently excludes only cancelled records, so declined appointments reserve intervals even though Doctor Calendar omits them. Calendar Schedule mode is a separate appointment-oriented list and is not a free/busy time grid.

## Goals / Non-Goals

**Goals:**

- Reuse one timeline geometry and styling foundation across full Calendar and compact Dashboard day views.
- Keep Dashboard appointment selection connected to the existing clinical workflow without putting clinical data into calendar projections.
- Make visible Calendar availability correspond directly to appointment and explicit time-off conflict policy.
- Make time-off creation and removal Doctor-owned, reversible, and immediately reflected by Calendar refresh.
- Preserve existing full Calendar duration geometry and give the narrower Dashboard a deliberate compact presentation.

**Non-Goals:**

- Changing appointment conflict, lifecycle, check-in, or clinical authorization policy.
- Making configured working hours enforce booking availability.
- Adding reasons, recurrence, bulk editing, or approval workflow to time off.
- Displaying time off in Calendar Schedule mode, which remains an appointment-status list rather than a free/busy view.
- Changing Receptionist authority to create or remove Doctor time off.

## Decisions

### 1. Extend the authorized calendar projection with ranged time off

`DoctorCalendarWeek` will continue to be the authorized non-clinical range projection and will gain an immutable collection of Doctor-owned `DoctorTimeOff` values, including the identifier needed for an authorized removal action.

The repository range query will use interval overlap semantics (`starts_at < rangeEnd` and `ends_at > rangeStart`) so time off crossing midnight or range boundaries is included. `CalendarService` will populate the collection after the existing Doctor/Receptionist authorization checks.

This is preferred over loading all time off and filtering in Java because range-bounded reads scale with the visible dates. Declined appointments remain absent from the time-grid projection and require no substitute interval because the revised conflict policy releases their former time.

### 2. Share timeline calculations but use explicit full and compact display profiles

`CalendarTimeGrid` will accept a display profile rather than duplicating the timeline. Both profiles will share date columns, interval clipping, current-time and working-hours presentation, proportional duration calculations, keyboard selection, and event-surface clipping.

The existing full Calendar profile will retain its current 100-pixel 30-minute rows and inline decision controls. The compact Dashboard profile will use a smaller row height sufficient for patient name, time, and textual status, omit inline decision controls, and use the whole block as the selection target. In both profiles, the layout formula remains strictly proportional: a 60-minute interval occupies twice the vertical time span of a 30-minute interval.

This is preferred over embedding the full profile unchanged in Dashboard because the 4,800-pixel day and inline buttons are unnecessarily large for the narrow master column. It is preferred over a second day-grid implementation because duplicated time math, clipping, and accessibility behavior would drift.

Time-off placements will share the same day-boundary clipping calculations as appointments. The visual stack will remain:

1. working-hours and elapsed-period background;
2. appointment and time-off blocks;
3. current-time indicator.

Text labels and accessible descriptions will distinguish time off and appointment statuses without relying on colour.

### 3. Give Dashboard day state its own view/controller and preserve the master-detail contract

A focused Dashboard day component will own the selected date, Singapore-zone clock, Today/previous/next/date/refresh controls, compact grid, useful initial scrolling, and refresh lifecycle. It will request a one-day range from `CalendarService` and notify `DoctorView` when an appointment is selected.

`DoctorView` will resolve the selected calendar appointment through the existing appointment service before loading clinical information. Check-in, accept, reschedule, consultation, prescription, and completion controls will move into the selected-appointment side of the master-detail layout; their service calls, validation, enablement rules, and feedback behavior will remain unchanged. The Dashboard time-off form and its parsing code will be removed.

Changing date clears a selection that is not present on the new date. Manual refresh retains and reloads a still-visible selection, but clears it when the appointment no longer appears. Dashboard navigation will start/pause its minute ticker in the same way that full Calendar already does, avoiding background updates for hidden pages.

The compact timeline will initially scroll near the current time when showing today. For another date it will scroll to the earliest visible appointment or time-off interval; if none exists, it will use the first configured working interval and otherwise the start of day. The full day remains scrollable.

### 4. Keep empty-slot appointment creation unchanged and make time off an explicit Calendar action

The Calendar toolbar will gain an accessible Block time button and refresh icon. Empty-slot activation will continue to open appointment creation, avoiding an ambiguous chooser on the grid's most frequent scheduling interaction.

A dedicated time-off dialog will use the existing date plus separate hour/minute selector pattern and accept an optional initial start for safe prefilling. From the toolbar it will choose a sensible displayed date: today's date when it is in the visible range, otherwise the first visible date. Submission will call the existing block operation and refresh only after success; validation feedback will leave the dialog available for correction.

Selecting a time-off block in the signed-in Doctor's Calendar will open non-clinical details with a Remove action. Removal requires explicit confirmation. Receptionist Calendar will render the same block but will receive no removal handler, so it remains read-only.

### 5. Treat declined and cancelled appointments as non-blocking history

The shared appointment-availability query used by booking, rescheduling, and time-off creation will consider `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, and `CHECKED_OUT` appointments as occupying their recorded intervals. It will exclude both `DECLINED` and `CANCELLED` records.

Declining an appointment will not delete or rewrite it; the record remains available to Receptionist search and coordination. If a Receptionist later reschedules it, the target interval goes through the normal conflict query before the appointment becomes `PENDING`. This is preferred over treating `DECLINED` as an implicit hold because an explicit pending appointment or time-off interval already represents reserved availability more clearly.

### 6. Remove time off through an owner-scoped repository mutation

`AppointmentService` will expose a Doctor-only removal operation. The repository mutation will delete with both interval ID and `doctor_id` in its predicate and return whether a row was affected. The service will derive the owner from the authenticated session rather than accepting a caller-supplied Doctor ID. A zero-row result will become the same safe not-found validation outcome for a missing interval and an interval belonging to another Doctor.

No database migration is required because `doctor_time_off` already has a stable identifier and owner. Removal changes no appointment records and does not bypass any remaining conflict checks.

### 7. Refresh operations preserve the active view state

Dashboard refresh will retain the selected date; Doctor Calendar refresh will retain Week/Schedule mode, selected range, and Schedule anchor. Both refresh controls will use the existing error-feedback surface, an accessible name, and a tooltip. Successful time-off creation or removal will reuse the same Calendar refresh path rather than incrementally mutating UI collections.

### 8. Verify behavior at projection, service, geometry, and workflow boundaries

Repository/service tests will cover ranged time-off reads, midnight/range overlap, owner-scoped removal, non-Doctor rejection, declined/cancelled non-blocking behavior, active-status conflicts, Receptionist rescheduling, and unchanged historical records. JavaFX tests will cover day navigation, Today, refresh state retention, useful scrolling, appointment-to-clinical selection, selection clearing, compact proportional geometry, time-off rendering, dialog validation, confirmation, and Receptionist read-only presentation. Existing Calendar tests will continue to prove full-profile geometry and appointment workflows.

User and developer guides will be updated only after the implementation and quality checks pass, in keeping with the repository's test-before-documenting policy.

### 9. Standardize date-picker presentation through the shared UI factory

All JavaFX `DatePicker` controls will be created through a small
`UiComponents` factory that adds a stable `compact-date-picker` style marker.
The shared stylesheet will give those controls the same compact height, spacing,
border, and quiet embedded calendar-button treatment used by the Calendar's
compact selectors. The embedded text field will remain transparent so focus is
shown by the outer control rather than nested borders. This applies to Dashboard,
Calendar, appointment and time-off dialogs, Receptionist filters, and any other
current date-picker field; it does not alter parsing, popup behavior, keyboard
navigation, accessible labels, or date values.

The Dashboard card will use the existing wrapped supporting-text factory with an
explicit line break before the Calendar guidance. Its day grid will receive a
small top margin from the toolbar so the controls and timeline remain visually
separate at narrow master-pane widths.

### 10. Keep the Patient Directory on one full-height content surface

The shared `PatientDirectoryView` will use its existing results card as the page
surface: the dynamic page title will be the first child of that card, while the
standalone description and `Patient results` section heading will be removed.
Registration, details, and edit states will retain the same title slot so the
directory workflow remains labelled while switching views.

The patient search field will grow into the available search-row space while
the search and clear actions keep their intrinsic widths. Doctor and
Receptionist containers will fit the directory view to their page viewport so
the white surface stretches to the available height and only the results table
needs to scroll when content exceeds it.

The shared compact date-picker class will also set a shorter preferred and
maximum width for every application date-picker. This width is presentation
only; date values, popup behavior, keyboard interaction, and the existing
calendar/report layout semantics remain unchanged.

### 11. Allow the Patient Directory results table to fill its page card

The shared `PatientDirectoryView` will keep its existing vertically growing
content/card structure but remove the fixed maximum height from the results
`TableView`. The table will retain its fixed row geometry and a sensible
minimum/preferred height, then consume all remaining card height through the
existing `VBox.setVgrow` constraint. The table's own vertical scrollbar remains
the overflow mechanism when results exceed the available space; no synthetic
rows or data changes are introduced.

### 12. Wrap Calendar actions as a responsive group

The Doctor Calendar toolbar will separate navigation/date/view controls from
the primary Add appointment and Block time actions. At wide widths the action
group remains on the main toolbar row. When the available width cannot satisfy
the measured preferred widths, the action group moves intact to a second row;
refresh and settings remain discoverable with the navigation row. The layout
will use JavaFX sizing/listener behavior rather than unsupported CSS media
queries, and the group will never split the two scheduling actions across rows.

### 13. Use Calendar and Agenda as the mode names

The date-range time grid will be labelled `Calendar`, and the chronological
lazy list will be labelled `Agenda`. `Week` is not accurate for the grid's
configurable `From`/`To` range, while `Agenda` communicates the list's
date-grouped stream without implying a separate scheduling policy. Existing
mode state, projections, and privacy boundaries remain unchanged.

### 14. Make Agenda navigation date-based

Agenda mode will use the shared compact `DatePicker` as its inclusive start
date, positioned between the previous and next controls. Selecting a date
resets the lazy list from that date; the arrows add or subtract exactly one
day, Today restores the current Singapore clinic date, and refresh preserves
the selected anchor. The existing service query remains an inclusive
`starts_at >= anchor` stream, so a past anchor may show elapsed rows with the
existing Past cue and an empty anchor date may have no date header until the
first appointment. The custom week/month picker and seven-day anchor movement
are removed because they no longer describe the interaction.

### 15. Bind Patient Directory actions to the row value

The Patient Directory Actions column will expose the `Patient` as its cell value
instead of relying on `TableCell.getTableRow().getItem()` while the cell is being
reused. The View button will therefore be created and identified from the value
passed to `updateItem`, so replacing the table items after a search refreshes
every visible action reliably. Existing row IDs, navigation, and ownership
behavior remain unchanged.

### 16. Remove the obsolete first-day preference from Calendar settings

The Doctor Calendar settings page will no longer render the Calendar Preferences
card or its “Show the first day of the week as” selector. The current Calendar
view has an explicit From/To range and Agenda has an explicit inclusive start
date, so this preference no longer changes either Doctor presentation. Work
hours and the fixed `Asia/Singapore` timezone remain visible. The existing
`DoctorCalendarSettings` field and persistence path stay intact and are carried
through saves using the loaded value to avoid an unnecessary schema/API change.

### 17. Expand the local-development seed dataset

The Java `DemoDataSeeder` remains the single source of truth invoked by
`scripts/seed-demo-data.ps1`. A successful seed will create 18 deterministic
patients, including inactive directory examples, and two non-overlapping
appointments per Doctor for each date in the inclusive rolling window from
`today.minusDays(7)` through `today.plusDays(14)` in `Asia/Singapore`. Appointment
statuses will rotate across the full lifecycle so Calendar, Dashboard, search,
and clinical workflows have useful data on every day. Declined and cancelled
rows remain stored as history but continue to follow the existing non-blocking
conflict policy.

The seeder will retain the current settings and lunch-break examples, then use
the returned appointment IDs to save deterministic `ClinicalRecord` values for
historical `CHECKED_IN`, `COMPLETED`, and `CHECKED_OUT` appointments. Each
historical `COMPLETED` or `CHECKED_OUT` appointment will also receive at least
one `Prescription`; pending, accepted, declined, cancelled, and future rows
will not receive clinical data.

Showcase credentials will be concise while still satisfying the eight-character
password policy: `ada` / `ada1234!`, `grace` / `grace123!`, and `reception` /
`recept123!`. The System Admin credentials remain unchanged. The PowerShell
wrapper and developer/user documentation will print and describe the same
values.

### 18. Make selected Dashboard details status-driven

The selected side of the Dashboard master-detail view will be rendered from
the freshly resolved appointment and its authorized administrative patient.
The existing appointment projection remains patient-identifier-free; after a
Doctor selects a block, `DoctorView` will call
`PatientService.getAdministrative(...)` using the appointment's patient ID and
will render the returned fields through the shared read-only details grid.

The existing selection summary label will become a status banner. Its text
will contain only the patient's full name, scheduled interval, and humanized
status, while a normalized status class will reuse the Calendar palette for
the banner background and border. Text and the status wording remain present
so colour is not the only state cue.

The pane will use one status-driven content surface. Pending and accepted
appointments receive only the lifecycle actions valid for that state. The
reschedule action opens `AppointmentDialog.showDoctorEdit(...)`, the same
validated editor used by Calendar. Checked-in appointments receive the
existing consultation, prescription, and completion cards. Completed and
checked-out appointments reuse those cards in a read-only mode for history;
declined and cancelled appointments receive patient details plus a clear
non-actionable status message. The service layer remains the final authority
for every action, and successful actions refresh the Dashboard so status
changes re-render the appropriate branch or clear a declined/cancelled
selection that is no longer projected.

This keeps the right pane focused on the selected patient's context, avoids
duplicating appointment validation, and prevents controls for an inapplicable
lifecycle state from appearing as disabled clutter.

### 19. Enforce package boundaries with ArchUnit

The project will add `com.tngtech.archunit:archunit:1.5.0` as a test-only,
pinned dependency and run named rules from
`nusynapxe.architecture.ArchitectureTest` through the normal JUnit and Gradle
`check` lifecycle. The rules import production classes only and require domain
independence from outer packages, persistence independence from UI/services/
tools, service independence from UI/tools, and UI independence from tools and
direct persistence access except for `ApplicationRouter`. Slices for domain,
persistence, service, UI, and tools must remain free of cycles. The demo-data
package remains an explicit bootstrap composition utility and is not treated as
a production request layer.

## Risks / Trade-offs

- **Risk: A shared grid gains too many conditional branches.** -> Encapsulate size and interaction differences in an immutable display profile and small availability renderers rather than scattering Dashboard checks.
- **Risk: Compact rows make text unreadable or reintroduce overflow.** -> Keep content deliberately minimal, retain clipping, test narrow-width bounds, and assert proportional 30/60-minute geometry separately for both profiles.
- **Risk: Refresh or date changes leave stale clinical data visible.** -> Make selection reconciliation an explicit part of every Dashboard refresh and clear clinical controls before showing a new date without that appointment.
- **Risk: Existing callers rely on declined appointments retaining a slot.** -> Encode the revised status set in one shared conflict query and add booking, rescheduling, and time-off regression tests that prove declined records remain stored while releasing availability.
- **Risk: Time-off removal races with another read or booking.** -> Use a single owner-scoped delete transaction and always reload Calendar after the mutation; subsequent bookings still execute their normal transactional conflict check.
- **Risk: Cross-midnight blocks are incorrectly sized at day boundaries.** -> Reuse interval clipping calculations and add geometry tests for both start and end dates.

## Migration Plan

No schema or data migration is required. Existing `doctor_time_off` rows become visible when the expanded projection is deployed. Rollback consists of restoring the previous UI/projection and service code; existing rows remain compatible because their storage format is unchanged.
