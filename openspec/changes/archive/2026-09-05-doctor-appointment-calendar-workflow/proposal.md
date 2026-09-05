## Why

The doctor Calendar currently presents appointments as a mostly read-only view, while appointment state changes and scheduling are split across role-specific screens. It also shows cancelled appointments, exposes unnecessary patient identifiers, and allows appointment blocks to overflow their day columns. This change unifies appointment scheduling and coordination around the clinic workflow while enforcing doctor ownership and receptionist responsibilities.

## What Changes

- Add `DECLINED` as an appointment lifecycle state and define the complete booking, decision, check-in, consultation, checkout, rescheduling, and cancellation rules.
- Allow a doctor to schedule an appointment only for their own schedule; those appointments start as `ACCEPTED`. Receptionist-created appointments remain `PENDING` and may target any doctor.
- Restrict doctors to appointments assigned to them. Receptionists remain able to coordinate appointments for all doctors and are the only role allowed to reschedule `DECLINED` appointments.
- Allow assigned doctors to check in their own eligible `ACCEPTED` appointments from the Doctor Dashboard. Receptionists retain the existing check-in queue workflow.
- Add Calendar appointment creation through an `Add appointment` button and empty-slot selection, using a shared appointment form.
- Add doctor appointment-card actions for accepting, declining, rescheduling, and cancelling appointments where the actor and state permit the operation.
- Open an appointment details popup when a doctor selects a visible appointment card, with rescheduling and pre-check-in cancellation controls.
- Hide `DECLINED` and `CANCELLED` appointments from the doctor Calendar and Schedule views while retaining declined appointments in receptionist management views for rescheduling and retaining cancelled appointments as soft-deleted history.
- Display only the patient name on doctor appointment cards and constrain cards to their assigned day/lane so they cannot overflow adjacent days.
- Remove the unnecessary visual gap between the Calendar time axis and the first day column.
- **BREAKING** Change the existing doctor Schedule behavior from read-only summaries to appointment interactions where the relevant appointment action is authorized.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `clinic-workflow`: Extend appointment states and transitions, including `DECLINED`, doctor-owned scheduling and check-in, receptionist rescheduling rules, and the pre-check-in cancellation boundary.
- `doctor-calendar`: Add doctor-owned appointment creation and actions, empty-slot scheduling, appointment details/rescheduling/cancellation, patient-name-only display, column clipping, spacing correction, and filtering of declined/cancelled appointments.
- `doctor-calendar-schedule`: Apply the same doctor ownership, status visibility, appointment interaction, and hidden declined/cancelled rules to Schedule view.
- `receptionist-scheduling-dashboard`: Keep declined appointments available for receptionist rescheduling and reset receptionist-rescheduled appointments to `PENDING`.

## Impact

- Appointment domain, transition, authorization, and service logic, including booking, decision, rescheduling, cancellation, and check-in operations.
- Appointment persistence schema and calendar projections, including support for `DECLINED` and separate doctor/receptionist visibility filters.
- Doctor Calendar, Schedule, and Dashboard UI plus reusable appointment dialog and action handling.
- Receptionist scheduling and appointment-management UI.
- Existing workflow, calendar, Schedule, and receptionist scheduling specifications and their automated/unit/UI test coverage.
