## 1. Appointment State and Persistence

- [x] 1.1 Add the `DECLINED` appointment state and update the transition policy for doctor accept/decline, receptionist rescheduling, doctor rescheduling, and the pre-check-in cancellation boundary; verify focused transition tests cover every permitted and rejected state change.
- [x] 1.2 Extend the versioned SQLite schema migration to allow `DECLINED` without rewriting existing appointment history; verify a fresh database and an upgraded database both initialize successfully and preserve existing rows.
- [x] 1.3 Add or update appointment service tests for doctor-owned booking (`ACCEPTED`), receptionist booking for any Doctor (`PENDING`), cross-doctor authorization, declined-appointment rescheduling restrictions, and conflict preservation; verify unauthorized and invalid operations leave state and timestamps unchanged.

## 2. Appointment Service Authorization

- [x] 2.1 Implement explicit accept and decline operations for the assigned Doctor's eligible appointments, including the behavior that a successful decline becomes available to receptionist coordination; verify only the assigned Doctor can perform these decisions.
- [x] 2.2 Update rescheduling authorization and status outcomes so an assigned Doctor can reschedule only their own `PENDING`/`ACCEPTED` appointments to `ACCEPTED`, while a Receptionist can reschedule any `PENDING`/`ACCEPTED`/`DECLINED` appointment to `PENDING`; verify declined appointments cannot be rescheduled by Doctors.
- [x] 2.3 Restrict cancellation to Receptionists or the assigned Doctor while the appointment is `PENDING`, `ACCEPTED`, or `DECLINED`; verify cancellation attempts after check-in preserve `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` state.
- [x] 2.4 Allow the assigned Doctor to check in their own eligible `ACCEPTED` appointment while preserving Receptionist queue check-in and the scheduled-start-time guard; verify another Doctor and early check-in attempts are rejected.

## 3. Appointment Projections and Visibility

- [x] 3.1 Update Doctor Calendar week and Schedule page queries to exclude `DECLINED` and `CANCELLED`, while leaving receptionist management queries able to return `DECLINED`; verify projection tests distinguish Doctor and Receptionist visibility.
- [x] 3.2 Keep declined appointments in conflict detection until they are rescheduled or cancelled, and change Doctor-facing administrative display text to the patient name without the patient identifier; verify conflict and projection tests cover both behaviors.

## 4. Shared Appointment Interaction UI

- [x] 4.1 Extract reusable patient/date/start/end appointment form and details-dialog behavior from the receptionist flow, allowing each caller to supply actor, Doctor assignment, initial values, permitted actions, and refresh handling; verify the existing receptionist booking/reschedule behavior remains functional.
- [x] 4.2 Add the Doctor Calendar `Add appointment` action and empty-slot entry point, defaulting the signed-in Doctor and clicked half-hour interval and creating valid appointments as `ACCEPTED`; verify invalid input and conflict feedback do not mutate the schedule.
- [x] 4.3 Update Doctor appointment cards to show patient name only, provide compact accessible `Accept`/`Decline` controls for eligible visible states, and open the details popup for card selection without triggering it from an inline button; verify successful actions refresh the Calendar and declining removes the card.
- [x] 4.4 Correct Calendar time-axis/day-column spacing and constrain each appointment node to its day column and overlap lane, including narrow lanes and action controls; verify UI/layout tests show no gap after the time axis and no horizontal overflow.
- [x] 4.5 Make Doctor Schedule rows use the same filtered projection and authorized appointment details/actions as Calendar while preserving non-mutating navigation and lazy loading; verify declined/cancelled rows are absent and authorized row actions refresh the Schedule.
- [x] 4.6 Add the Doctor Dashboard `Check In` action for the selected own accepted appointment, with eligibility feedback and automatic refresh; verify the control is disabled or rejected for other statuses, other Doctors' appointments, and appointments before their start time.

## 5. Receptionist Coordination and Verification

- [x] 5.1 Update the Receptionist scheduling dashboard and popup to display/select `DECLINED` appointments, enable their rescheduling, reset successful receptionist reschedules to `PENDING`, and keep cancellation limited to pre-check-in states; verify the declined appointment remains visible to Receptionist and disappears from Doctor Calendar/Schedule.
- [x] 5.2 Add unit, repository, service, and UI coverage for the complete role/state matrix, including doctor-created accepted appointments, receptionist-created pending appointments, decline/remediation, check-in, cancellation, hidden calendar states, patient-name-only rendering, and lane containment; verify the targeted test suite passes.
- [x] 5.3 Run the project quality checks and OpenSpec validation for the completed change, including `openspec validate "doctor-appointment-calendar-workflow" --type change --strict` and the applicable Gradle test/check tasks; verify the change reports all required artifacts complete and no whitespace or specification-format errors remain.
