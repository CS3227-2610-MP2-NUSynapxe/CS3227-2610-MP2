## Purpose

Provides Receptionists with a focused, privacy-safe view of patients arriving for accepted appointments and a reliable check-in workflow for the clinic front desk.

## ADDED Requirements

### Requirement: The check-in queue SHALL show today's operational appointments

An authenticated Receptionist SHALL be able to open a check-in queue for a selected clinic date, defaulting to the current Singapore date. The queue SHALL show accepted appointments awaiting arrival and checked-in appointments, with Patient ID/name, Doctor, scheduled interval, and status. It SHALL provide summary counts for waiting and checked-in appointments.

#### Scenario: Receptionist opens today's queue
- **WHEN** a Receptionist opens the Check-in Queue without changing the date
- **THEN** the queue uses the current Singapore date and shows matching accepted and checked-in appointments in chronological order

#### Scenario: Receptionist sees queue counts
- **WHEN** the queue contains waiting and checked-in appointments
- **THEN** the summary reports counts derived from the displayed filtered appointments

### Requirement: The check-in queue SHALL support administrative filtering

The queue SHALL allow filtering by clinic date, Doctor, Patient ID or patient name, and queue status. Changing any filter SHALL refresh the rows and counts automatically. Queue results SHALL contain only administrative patient and appointment information.

#### Scenario: Receptionist filters the queue
- **WHEN** a Receptionist selects a date, Doctor, patient query, or queue status
- **THEN** only matching appointments remain visible and the summary counts update

#### Scenario: Clinical information is excluded
- **WHEN** a Receptionist views a queue row or its details popup
- **THEN** diagnoses, consultation notes, follow-up notes, prescriptions, and other clinical fields are not returned or displayed

### Requirement: The queue SHALL provide an appointment details popup

Selecting a queue appointment SHALL open a separate details view showing permitted patient contact information, Doctor, scheduled interval, and lifecycle status. The popup SHALL expose a check-in action only when the selected appointment is eligible.

#### Scenario: Receptionist views appointment details
- **WHEN** a Receptionist selects a queue appointment
- **THEN** an administrative details popup displays the selected patient's permitted information and appointment context

#### Scenario: Receptionist selects an ineligible appointment
- **WHEN** the selected appointment is pending, cancelled, completed, or checked out
- **THEN** the popup does not offer an enabled check-in action

### Requirement: The queue SHALL authorize and process check-in safely

Only an authenticated Receptionist SHALL check in an accepted appointment at or after its scheduled start time. A successful check-in SHALL transition the appointment to `CHECKED_IN`, close or update the details popup, and refresh the queue. Invalid or unauthorized attempts SHALL return non-sensitive feedback and preserve the existing state.

#### Scenario: Receptionist checks in an eligible appointment
- **WHEN** a Receptionist checks in an accepted appointment at or after its scheduled start time
- **THEN** the appointment becomes `CHECKED_IN` and the queue refreshes without manual refresh

#### Scenario: Receptionist attempts early check-in
- **WHEN** a Receptionist checks in an accepted appointment before its scheduled start time
- **THEN** the action is rejected with feedback and the appointment remains accepted

#### Scenario: Non-Receptionist attempts queue check-in
- **WHEN** a Doctor or unauthenticated user attempts the Receptionist queue check-in operation
- **THEN** authorization rejects the request without changing the appointment
