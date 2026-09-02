## Context

The application is a Java 25 JavaFX desktop client backed by SQLite. `AppointmentService` already authorizes Receptionists for all-Doctor queries, booking, rescheduling, cancellation, and check-in; `AppointmentRepository` already enforces interval overlap rules and persists explicit lifecycle states. `ReceptionistView` currently has a simple appointment form and list.

## Goals / Non-Goals

**Goals:** make all-Doctor scheduling scannable, filterable, and safe to operate; reuse existing services and semantic TestFX IDs; keep all lifecycle and conflict decisions outside the UI.

**Non-Goals:** Doctor time-off editing by Receptionists, clinical record access, patient self-service, reminders, recurring appointments, drag-and-drop scheduling, hard deletion, or new external dependencies.

## Decisions

### Dashboard query model

Add a service/repository read path that accepts optional date, Doctor, patient-query, and status filters, returning appointment rows in chronological order. Use existing Patient and Account lookups or a deliberately administrative appointment projection; do not join clinical tables. Summary counts should be derived from the same filtered result set so the numbers and rows cannot disagree.

### Controls and actions

Use a JavaFX `DatePicker` for the appointment date and validated text or selector controls for start/end times. Keep stable IDs for filters, summary labels, schedule list, booking controls, reschedule controls, cancel, and check-in. Selecting a row sets the action target; actions remain enabled only for valid lifecycle states, while the service rechecks every rule.

### Active patients and confidentiality

The booking patient selector should exclude inactive patients, and the service must independently reject an inactive patient at booking to prevent UI bypasses. Existing appointments remain viewable for history. Appointment rows expose only patient administrative display data, Doctor identity, interval, and status.

### Refresh and feedback

Use one refresh function driven by the current filter state after successful writes and filter changes. No manual refresh button is added. Validation, authorization, conflict, and SQL failures map to existing non-sensitive feedback patterns.

## Testing Strategy

- Repository tests for filtered ordering, each filter, status counts, and empty results.
- Service tests for Receptionist authorization, inactive-patient rejection, conflict preservation, lifecycle permissions, and invalid intervals.
- Integration tests proving appointment operations do not expose or modify clinical records.
- TestFX tests for dashboard controls, filtering, booking, rescheduling, cancellation, check-in, feedback, and automatic refresh.
- Run Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Javadoc, and strict OpenSpec validation.
