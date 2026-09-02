## Purpose

Provides Receptionists with a clear all-Doctor appointment coordination view while keeping appointment lifecycle and scheduling rules in the service layer.

## ADDED Requirements

### Requirement: The Receptionist dashboard SHALL summarize and filter all appointments

An authenticated Receptionist SHALL see appointments across all Doctors with Patient ID/name, Doctor, start/end time, and lifecycle status. The dashboard SHALL provide filters for clinic date, Doctor, patient name or Patient ID, and status, and SHALL show counts for the filtered or selected-day appointments by relevant status.

#### Scenario: Receptionist views a filtered schedule
- **WHEN** a Receptionist selects a date, Doctor, patient query, or status filter
- **THEN** the dashboard shows only matching appointments in chronological order and updates its summary counts

#### Scenario: Receptionist sees no matching appointments
- **WHEN** the active filters match no appointments
- **THEN** the dashboard shows an empty schedule and zero counts without an application error

### Requirement: The dashboard SHALL support Receptionist appointment coordination

A Receptionist SHALL be able to choose a patient and any Doctor, enter a valid date and interval, and book an appointment. For pending or accepted appointments, the Receptionist SHALL be able to reschedule or cancel; for accepted appointments at or after their start time, the Receptionist SHALL be able to check in. Existing service-layer lifecycle and Doctor-conflict rules SHALL remain authoritative.

#### Scenario: Receptionist books an available appointment
- **WHEN** valid patient, Doctor, date, start, and end values identify an available interval
- **THEN** a pending appointment is created and the dashboard refreshes to show it

#### Scenario: Receptionist attempts an unavailable interval
- **WHEN** booking or rescheduling overlaps a non-cancelled appointment or Doctor time-off
- **THEN** a clear scheduling-conflict message is shown and the previous schedule remains unchanged

#### Scenario: Receptionist books an inactive patient
- **WHEN** an inactive patient is selected for a new appointment
- **THEN** the booking is rejected with a validation message and no appointment is created

### Requirement: Scheduling inputs SHALL provide actionable validation

The dashboard SHALL provide calendar-based date selection and validated time inputs. It SHALL reject missing or malformed values, an end time that is not after its start, and invalid lifecycle actions with field-specific, non-sensitive feedback.

#### Scenario: Receptionist enters an invalid interval
- **WHEN** the end is equal to or earlier than the start, or a date/time cannot be parsed
- **THEN** the dashboard reports the invalid input and does not call a mutating appointment operation

### Requirement: Appointment selectors and times SHALL support rapid entry

Patient and Doctor selectors SHALL be editable searchable dropdowns. Appointment start and end
times SHALL be selectable in 30-minute increments from `00:00` through `23:30`, while validation
continues to enforce a valid interval.

#### Scenario: Receptionist searches a selector
- **WHEN** a Receptionist types a patient or Doctor name or identifier in its dropdown
- **THEN** the selector accepts the search text without requiring manual scrolling through every option

#### Scenario: Receptionist chooses a half-hour time
- **WHEN** a Receptionist opens a start or end time dropdown
- **THEN** options are available at each half-hour from midnight through 23:30

### Requirement: Rescheduling SHALL use a patient-context popup

Selecting reschedule for an eligible appointment SHALL open a separate popup containing a patient
selector, the selected patient's permitted administrative details, new date/start/end controls, a
reschedule action, and a cancel-appointment action. Successful actions SHALL close the popup and
refresh the dashboard.

#### Scenario: Receptionist opens rescheduling popup
- **WHEN** a pending or accepted appointment is selected and reschedule is chosen
- **THEN** a popup shows the appointment patient and permitted administrative details

#### Scenario: Receptionist cancels from popup
- **WHEN** cancel appointment is selected in the popup
- **THEN** the appointment becomes cancelled, the popup closes, and the dashboard refreshes

### Requirement: Dashboard updates SHALL be automatic and non-clinical

After a successful booking, reschedule, cancellation, check-in, or filter change, the dashboard SHALL reload affected data automatically. Receptionist results SHALL contain only administrative patient information and appointment coordination fields; diagnoses, consultation notes, follow-up notes, and prescriptions SHALL never be returned or displayed.

#### Scenario: Dashboard refreshes after a write
- **WHEN** a Receptionist successfully changes an appointment
- **THEN** the appointment list and summary reflect the new state without a manual refresh action

#### Scenario: Receptionist cannot access clinical information
- **WHEN** a Receptionist uses the dashboard or its services
- **THEN** clinical records remain unavailable and unchanged
