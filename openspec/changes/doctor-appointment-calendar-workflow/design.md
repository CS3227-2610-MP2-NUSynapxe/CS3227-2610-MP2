## Context

See `proposal.md` for the motivation and user-visible scope. The current application already has a central appointment service and transition policy, SQLite persistence, a Doctor Dashboard, a weekly `CalendarTimeGrid`, a paginated `CalendarScheduleList`, and receptionist scheduling controls. The current appointment status set has no `DECLINED` value; booking and check-in authorization do not yet match the clarified role rules; doctor calendar projections include cancelled appointments; patient display text includes the patient identifier; and the calendar block layout can exceed a narrow overlap lane.

`CalendarAppointmentBlock` is a pure value object for one-day appointment geometry. It should remain free of authorization and mutation behavior. Appointment visibility should be decided before calendar blocks are calculated.

## Goals / Non-Goals

**Goals:**

- Make appointment state transitions and actor ownership authoritative in the service layer.
- Persist `DECLINED` appointments while exposing them to receptionist management and hiding them from Doctor Calendar and Schedule projections.
- Let doctors schedule and check in only appointments assigned to themselves, with doctor-created appointments immediately accepted.
- Reuse one appointment form and one details interaction across Calendar, Schedule, Dashboard, and receptionist coordination where their permitted actions differ.
- Make the Calendar layout deterministic at the time-axis boundary and for overlapping/narrow appointment lanes.
- Preserve non-clinical data boundaries and existing Singapore-local time behavior.

**Non-Goals:**

- Changing clinical records, consultation completion, prescriptions, checkout payments, or revenue reporting beyond their existing appointment-state prerequisites.
- Making configured working hours enforce booking availability; they remain visual preferences.
- Transferring an appointment to another Doctor through rescheduling.
- Physically deleting cancelled or declined records.
- Adding a new UI toolkit or external dependency.

## Decisions

### Centralize the actor and state policy in AppointmentService

Extend the existing transition table with `DECLINED`, then keep all mutations behind explicit service operations rather than exposing a generic status update to the UI.

- Receptionist booking for any Doctor creates `PENDING`.
- Doctor booking is allowed only when the target Doctor is the authenticated Doctor and creates `ACCEPTED`.
- The assigned Doctor may accept or decline their own `PENDING` or `ACCEPTED` appointment before check-in. A declined appointment is not presented to that Doctor, so it returns to the Doctor workflow only after a Receptionist reschedules it to `PENDING`.
- The assigned Doctor may reschedule their own `PENDING` or `ACCEPTED` appointment; the resulting state is `ACCEPTED`.
- A Receptionist may reschedule `PENDING`, `ACCEPTED`, or `DECLINED` appointments for any Doctor; the resulting state is `PENDING`.
- Only the Receptionist or assigned Doctor may cancel, and only before `CHECKED_IN`.
- A Receptionist may check in any eligible `ACCEPTED` appointment. The assigned Doctor may check in only their own eligible `ACCEPTED` appointment. Both remain subject to the scheduled-start-time rule.

The service will validate actor identity and appointment ownership even when a UI control is hidden or disabled. This is preferred over UI-only authorization because Calendar, Schedule, Dashboard, and receptionist controls all share the same mutation boundary. A generic repository status mutation remains an internal persistence primitive, not a user-facing authorization path.

### Use versioned SQLite schema migration for DECLINED

Add `DECLINED` to the domain enum and appointment status constraint through the existing schema-version mechanism. Existing appointment rows retain their current states; no historical row is deleted or rewritten. The migration must leave conflict indexes and other appointment columns intact.

The conflict query continues to exclude only `CANCELLED` appointments. Consequently, a declined appointment remains reserved at its original interval until a Receptionist reschedules or cancels it. This prevents a declined appointment from silently creating a second booking at the same time while it waits for receptionist coordination.

A clean rollback is only available before any new `DECLINED` row is persisted, because an older binary cannot interpret that status. After declined data exists, prefer rolling forward with a compatible fix; if an emergency rollback is required, back up the database and explicitly migrate declined rows to a supported state rather than silently dropping them.

### Keep separate doctor and receptionist read projections

Apply status visibility in the repository queries used by Doctor Calendar and Doctor Schedule:

- Doctor week and paginated Schedule queries exclude `DECLINED` and `CANCELLED`.
- Receptionist management queries continue returning `DECLINED` so it can be selected and rescheduled. Existing receptionist visibility of other statuses is preserved.

This keeps hidden appointments out of layout calculation, lane allocation, and lazy-loaded Schedule pages. It also avoids a global repository filter that would accidentally remove declined appointments from the receptionist workflow. The appointment projection retains the patient identifier internally for joins and actions, but its Doctor-facing display name contains only the patient's name.

### Share appointment form and mutation callbacks across Doctor views

Extract the common patient/date/start/end form behavior from the existing receptionist rescheduling interaction into a reusable appointment dialog/controller boundary. The caller supplies:

- the acting user and permitted Doctor assignment;
- the initial patient and interval values;
- whether the dialog is creating or editing;
- the allowed actions for the selected appointment state; and
- a success callback that refreshes the owning view.

The Doctor Calendar toolbar `Add appointment` action opens the form with no Doctor selector and defaults the Doctor to the signed-in Doctor. An empty day/time slot invokes the same form with that date and half-hour interval pre-filled. Appointment-card and Schedule-row selection invoke the details form; button events must be consumed so an inline Accept/Decline click does not also open the popup.

The Doctor Dashboard adds a `Check In` action for the selected appointment. It is enabled only for the signed-in Doctor's eligible accepted appointment, while the service remains the final authority. The existing Receptionist Check-in Queue remains a separate receptionist-only entry point.

### Make Calendar layout constraints explicit

Set the Calendar grid's horizontal spacing and time-axis dimensions explicitly so the first day column starts directly after the time-axis region. Remove excess header/label padding only where it creates a second visual gap; retain the time-axis border that communicates the column boundary.

For each overlap lane, calculate the appointment width from the available day-column width and cap it to that lane's width. Use a clip on the day event surface as a final containment boundary. Patient labels use a bounded width with safe wrapping or ellipsis. Compact decision controls are placed in the card's lower-right action row; short appointment blocks use compact sizing and expose full timing/details through the popup without allowing content to draw into another day column.

The `CalendarAppointmentBlock` lane and interval calculations remain the source of geometry. The UI layout layer, rather than the domain block record, owns clipping and control presentation.

### Preserve accessible, non-clinical appointment interactions

All inline buttons, empty-slot targets, appointment cards/rows, popup fields, and popup actions receive readable accessible names and keyboard reachability. Appointment cards and receptionist management views expose only administrative patient information, appointment timing, status, and permitted actions; no clinical information is added to the shared dialog.

## Risks / Trade-offs

- **[Risk]** A declined appointment is hidden from the Doctor Calendar while still reserving its original interval. → **Mitigation:** Keep it visible and actionable in receptionist management, show clear declined status there, and retain the conflict rule until rescheduling or cancellation.
- **[Risk]** Two text action buttons are tight in a 30-minute block. → **Mitigation:** Use a compact two-row card layout, bounded patient text, and UI tests at the smallest appointment height and narrowest overlap lane; retain the details popup as the complete interaction.
- **[Risk]** Filtering at the wrong repository layer could hide declined appointments from receptionists. → **Mitigation:** Add separate projection tests for Doctor Calendar/Schedule and receptionist management rather than filtering all appointment reads globally.
- **[Risk]** A stale view could re-enable an action after another actor changes the appointment. → **Mitigation:** Re-fetch before mutation where practical, rely on service transition validation, show non-sensitive failure feedback, and refresh after every successful or rejected operation.
- **[Risk]** Existing local databases may be opened by an older build after a declined appointment is created. → **Mitigation:** Treat the schema change as forward-compatible deployment work, back up before migration, and document the forward-only rollback limitation.

