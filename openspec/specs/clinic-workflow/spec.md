# clinic-workflow Specification

## Purpose

Provides the shared patient, appointment, clinical consultation, and checkout workflow that lets Doctors and Receptionists coordinate care while preserving the boundary between administrative and medical information.
## Requirements
### Requirement: The system SHALL maintain patients with separate administrative and clinical information

The system SHALL maintain a patient identity with contact and billing information separately from medical records, consultation notes, diagnoses, follow-up notes, and prescriptions. A Receptionist SHALL be able to register a patient and create or update administrative information. A Doctor SHALL be able to view and update clinical information for a patient linked to the Doctor's appointment. Receptionist operations SHALL NOT read or write clinical fields.

#### Scenario: Receptionist registers a patient
- **WHEN** an authenticated Receptionist submits valid patient identity, contact, and billing information
- **THEN** the patient is persisted with administrative information and no medical note is created

#### Scenario: Receptionist updates administrative information only
- **WHEN** a Receptionist changes a patient's contact or billing information
- **THEN** the administrative fields are updated while existing medical notes, diagnoses, follow-up notes, and prescriptions remain unchanged

#### Scenario: Doctor records clinical information for an assigned patient
- **WHEN** an authenticated Doctor submits a diagnosis, consultation note, follow-up note, or prescription for a patient on that Doctor's appointment
- **THEN** the clinical information is persisted and associated with the patient and consultation

#### Scenario: Clinical data is not exposed to a Receptionist
- **WHEN** a Receptionist requests a patient record through an administrative workflow
- **THEN** the response contains only permitted administrative fields and excludes medical notes, diagnoses, follow-up notes, and prescriptions

### Requirement: The system SHALL support conflict-free appointment scheduling across the clinic

An authenticated Receptionist SHALL be able to book, cancel, and reschedule appointments for any Doctor. An authenticated Doctor SHALL be able to view and manage only that Doctor's schedule, accept assigned appointments, reschedule assigned appointments, and block time off. The system SHALL reject an appointment whose time interval overlaps another appointment or blocked time for the same Doctor, and SHALL leave the prior schedule unchanged when a booking or reschedule is rejected.

#### Scenario: Receptionist books an appointment for any Doctor
- **WHEN** a Receptionist submits a valid patient, Doctor, date, time, and duration for an available slot
- **THEN** an appointment is created for that Doctor and patient in the pending state

#### Scenario: Doctor accepts an assigned appointment
- **WHEN** the assigned Doctor accepts a pending appointment
- **THEN** the appointment changes to the accepted state and remains on that Doctor's schedule

#### Scenario: Overlapping appointment is rejected
- **WHEN** a Receptionist or Doctor attempts to book or reschedule an appointment into a slot overlapping another appointment for the same Doctor
- **THEN** the service reports a scheduling conflict and the original appointment schedule remains unchanged

#### Scenario: Doctor blocks time off
- **WHEN** a Doctor submits a valid time-off interval that does not overlap an existing appointment
- **THEN** the interval is persisted as unavailable time and future bookings in that interval are rejected

#### Scenario: Doctor cannot manage another Doctor's schedule
- **WHEN** a Doctor attempts to view or change an appointment or time-off interval belonging to another Doctor
- **THEN** the service rejects the request without returning or changing the other Doctor's schedule

### Requirement: The system SHALL enforce the appointment workflow from booking through checkout

Appointments SHALL progress through pending, accepted, checked-in, completed, checked-out, and cancelled states. A Receptionist SHALL be able to check in an accepted appointment through the dedicated check-in queue at or after its scheduled time and perform checkout after completion. A Doctor SHALL be able to record the consultation and mark a checked-in appointment completed. Invalid state transitions SHALL be rejected without changing the appointment state.

#### Scenario: Receptionist checks in an accepted appointment from the queue
- **WHEN** a Receptionist checks in an accepted appointment from the Check-in Queue at or after its scheduled time
- **THEN** the appointment changes to checked-in and becomes available to the assigned Doctor for consultation

#### Scenario: Receptionist checks in an accepted appointment
- **WHEN** a Receptionist checks in an accepted appointment at or after its scheduled time
- **THEN** the appointment changes to checked-in and becomes available to the assigned Doctor for consultation

#### Scenario: Receptionist attempts check-in before the scheduled time
- **WHEN** a Receptionist attempts to check in an accepted appointment before its scheduled start
- **THEN** the service rejects the transition and preserves the accepted state

#### Scenario: Doctor completes a consultation
- **WHEN** the assigned Doctor records the consultation and marks a checked-in appointment completed
- **THEN** the appointment changes to completed and the clinical record is linked to that consultation

#### Scenario: Receptionist cancels an appointment before completion
- **WHEN** a Receptionist cancels a pending or accepted appointment
- **THEN** the appointment changes to cancelled and it cannot be checked in or completed

#### Scenario: Invalid transition is rejected
- **WHEN** a user attempts to check in a pending, cancelled, completed, or checked-out appointment, to complete an appointment that is not checked in, or to check out an appointment that is not completed
- **THEN** the service rejects the transition and preserves the existing appointment state

### Requirement: The system SHALL protect clinical records and prescriptions by doctor ownership

Only the Doctor assigned to a consultation SHALL be able to create or edit its diagnosis, consultation notes, follow-up notes, and prescriptions. A prescription SHALL include enough information to identify the medication, dosage, frequency, duration, and instructions, and invalid clinical submissions SHALL be rejected without replacing valid existing data.

#### Scenario: Assigned Doctor adds a prescription
- **WHEN** the assigned Doctor submits a prescription with all required medication and usage details
- **THEN** the prescription is persisted for the consultation and is visible in the permitted Doctor clinical view

#### Scenario: Unassigned Doctor cannot edit clinical data
- **WHEN** a Doctor who is not assigned to the consultation attempts to read or edit its clinical record
- **THEN** the service rejects the request without exposing or changing the record

#### Scenario: Invalid prescription is rejected
- **WHEN** a Doctor submits a prescription missing a required medication or usage detail
- **THEN** validation feedback is returned and any previously saved clinical information remains unchanged

### Requirement: A Receptionist SHALL be able to record checkout payments and generate a daily revenue summary

After an appointment is completed, a Receptionist SHALL be able to record a valid checkout charge and payment method for the patient. The service SHALL reject negative, zero, malformed, or otherwise invalid payment amounts and SHALL reject a second successful checkout for the same appointment. A successful checkout SHALL create a receipt with a unique Singapore-local daily sequence number and persisted receipt timestamp. Receptionists SHALL be able to retrieve and view receipts without creating another payment. A daily revenue summary SHALL total successful checkout payments recorded on the selected local clinic date and SHALL exclude cancelled appointments and unsuccessful payment attempts.

#### Scenario: Receptionist completes checkout
- **WHEN** a Receptionist records a valid positive payment for a completed appointment
- **THEN** the payment is persisted, a receipt is generated, the payment is associated with the appointment and patient, and the appointment is marked checked out

#### Scenario: Invalid payment amount is rejected
- **WHEN** a Receptionist submits a zero, negative, malformed, or missing payment amount
- **THEN** checkout is rejected and no payment, receipt, or checked-out state is persisted

#### Scenario: Duplicate checkout is rejected
- **WHEN** a Receptionist submits checkout for an appointment with an existing successful payment
- **THEN** checkout is rejected and the original payment, receipt, and checked-out state remain unchanged

#### Scenario: Receipt can be viewed
- **WHEN** a Receptionist selects a receipt for a previously successful checkout
- **THEN** the persisted receipt details are displayed without creating another payment or changing appointment state

#### Scenario: Daily revenue includes successful payments for the selected date
- **WHEN** a Receptionist requests the revenue summary for a local clinic date
- **THEN** the summary reports the count and total of successful payments recorded on that date, grouped from persisted checkout records

#### Scenario: Revenue report reconciles to checkout records
- **WHEN** a Receptionist requests a report for a selected date or date range
- **THEN** the report totals and detail rows are derived from successful persisted payments and linked receipts

#### Scenario: Revenue excludes cancelled or unsuccessful transactions
- **WHEN** the selected date contains a cancelled appointment or an unsuccessful payment attempt
- **THEN** those records contribute zero to the successful payment count and total

