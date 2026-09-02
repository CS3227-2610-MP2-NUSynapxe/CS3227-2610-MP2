## Why

The Receptionist appointment workflow exists but is difficult to use at clinic scale: appointments are shown in one unfiltered list and scheduling relies on free-form date/time text. A dashboard should let Receptionists coordinate every Doctor's schedule quickly while preserving the existing service-layer conflict, lifecycle, and confidentiality rules.

## What Changes

- Add a Receptionist scheduling dashboard with summary counts and a chronological appointment view.
- Add filters for date, Doctor, patient name/Patient ID, and appointment status.
- Improve booking and rescheduling controls with calendar/date and validated time input.
- Keep booking, rescheduling, cancellation, and check-in available across all Doctors where lifecycle rules permit.
- Show clear conflict, invalid-time, inactive-patient, and invalid-transition feedback.
- Refresh dashboard data automatically after writes and filter changes without a manual refresh button.
- Add repository, service/authorization, integration, and TestFX coverage plus guide updates.

## Capabilities

### New Capabilities

- `receptionist-scheduling-dashboard`: Receptionist dashboard presentation, filtering, appointment coordination, lifecycle actions, refresh behavior, and scheduling feedback.

### Modified Capabilities

- `clinic-workflow`: Clarify Receptionist-facing appointment filtering, inactive-patient booking behavior, and dashboard presentation while retaining the existing appointment lifecycle and conflict requirements.

## Impact

- `src/main/java/nusynapxe/ui/ReceptionistView.java` and appointment controls.
- Appointment repository/service APIs may gain filtered query and dashboard-summary operations; authorization remains service-layer enforced.
- Appointment and patient integration fixtures and TestFX tests.
- `docs/UserGuide.md`, `docs/DeveloperGuide.md`, and the interaction log.
- No new runtime dependency or network service; SQLite and the existing JavaFX/Gradle stack remain in use.
