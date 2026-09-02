## Why

Receptionists need a focused front-desk view for patients arriving for accepted appointments. The current all-Doctor scheduling dashboard is useful for coordination but does not make today's waiting patients, arrival status, and check-in action easy to operate safely.

## What Changes

- Add a Receptionist check-in queue for the clinic date, using Singapore local time.
- Show waiting and checked-in appointments with Patient ID/name, Doctor, scheduled time, and status.
- Add date, Doctor, patient, and queue-status filters with summary counts.
- Open an administrative appointment details popup when a queue item is selected.
- Allow authorized Receptionists to check in eligible accepted appointments and refresh the queue automatically.
- Reject invalid lifecycle and early check-in attempts with clear feedback.
- Preserve the confidentiality boundary by excluding clinical records from queue results and popup details.
- Add repository, service, integration, and TestFX coverage plus User Guide, Developer Guide, and check-in queue log updates.

## Capabilities

### New Capabilities

- `reception-check-in-queue`: Receptionist queue presentation, filtering, administrative details, check-in workflow, authorization, and refresh behavior.

### Modified Capabilities

- `clinic-workflow`: Extend the Receptionist appointment workflow with a dedicated check-in queue while retaining existing lifecycle and confidentiality rules.

## Impact

- `AppointmentRepository` and `AppointmentService` may gain queue-specific filtered reads while reusing existing appointment status transitions.
- `ReceptionistView` gains a new queue tab and details popup.
- SQLite schema changes are not required unless a dedicated check-in audit timestamp is approved during design; existing status persistence remains compatible.
- Tests and receptionist-facing documentation will be updated. No external dependencies are needed.
