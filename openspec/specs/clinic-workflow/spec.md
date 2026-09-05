# clinic-workflow Specification

## Purpose

Provides the shared patient, appointment, clinical consultation, and checkout workflow that lets Doctors and Receptionists coordinate care while preserving the boundary between administrative and medical information.
## Requirements
### Requirement: The system SHALL maintain patients with separate administrative and clinical information

The system SHALL maintain a patient identity with non-clinical administrative
information separately from medical records, consultation notes, diagnoses,
follow-up notes, and prescriptions. An authenticated Receptionist or Doctor
SHALL be able to register a patient and create or update permitted
administrative information. A Doctor SHALL be able to view and update
clinical information for a patient linked to the Doctor's appointment.
Administrative patient operations SHALL NOT read or write clinical fields, and
a Receptionist SHALL not be granted access to clinical information.

#### Scenario: Receptionist registers a patient

- **WHEN** an authenticated Receptionist submits valid patient identity,
  demographic, contact, and address information
- **THEN** the patient is persisted with administrative information and no
  medical note is created

#### Scenario: Receptionist updates administrative information only

- **WHEN** a Receptionist changes a patient's permitted identity, demographic,
  contact, or address information
- **THEN** the administrative fields are updated while the Patient ID and
  existing medical notes, diagnoses, follow-up notes, prescriptions, and other
  history remain unchanged

#### Scenario: A Doctor registers or updates administrative information only

- **WHEN** an authenticated Doctor creates a patient or changes a patient's
  permitted identity, demographic, contact, or address information
- **THEN** the administrative fields are created or updated while the Patient
  ID and existing medical notes, diagnoses, follow-up notes, prescriptions,
  and other history remain unchanged

#### Scenario: Doctor records clinical information for an assigned patient
- **WHEN** an authenticated Doctor submits a diagnosis, consultation note, follow-up note, or prescription for a patient on that Doctor's appointment
- **THEN** the clinical information is persisted and associated with the patient and consultation

#### Scenario: Clinical data is not exposed to a Receptionist

- **WHEN** a Receptionist or Doctor requests a patient record through the
  administrative directory
- **THEN** the response contains only permitted administrative fields and
  excludes medical notes, diagnoses, follow-up notes, and prescriptions

### Requirement: The system SHALL support conflict-free appointment scheduling across the clinic

An authenticated Receptionist SHALL be able to book, cancel, and reschedule appointments for any Doctor. An authenticated Doctor SHALL be able to create, view, and manage only appointments assigned to that Doctor. A Doctor-created appointment SHALL start in the `ACCEPTED` state; a Receptionist-created appointment SHALL start in the `PENDING` state. An assigned Doctor SHALL be able to accept or decline their own pending or accepted appointments and reschedule their own pending or accepted appointments. A Receptionist SHALL be able to reschedule pending, accepted, or declined appointments for any Doctor. A Doctor SHALL NOT reschedule a declined appointment or change another Doctor's appointment. A Doctor's reschedule SHALL leave the appointment accepted, while a Receptionist's reschedule SHALL leave it pending. The system SHALL reject an appointment whose time interval overlaps another non-cancelled appointment or blocked time for the same Doctor, including a declined appointment, and SHALL leave the prior schedule unchanged when a booking or reschedule is rejected.

#### Scenario: Receptionist books an appointment for any Doctor
- **WHEN** a Receptionist submits a valid patient, Doctor, date, time, and duration for an available slot
- **THEN** an appointment is created for that Doctor and patient in the `PENDING` state

#### Scenario: Doctor books an appointment for their own schedule
- **WHEN** a Doctor submits a valid patient, date, time, and duration for an available slot on that Doctor's own schedule
- **THEN** an appointment is created for that Doctor and patient in the `ACCEPTED` state

#### Scenario: Doctor accepts an assigned appointment
- **WHEN** the assigned Doctor accepts a `PENDING` appointment
- **THEN** the appointment changes to `ACCEPTED` and remains on that Doctor's schedule

#### Scenario: Doctor declines an assigned appointment
- **WHEN** the assigned Doctor declines a `PENDING` or `ACCEPTED` appointment before check-in
- **THEN** the appointment changes to `DECLINED` and remains available for Receptionist coordination

#### Scenario: Doctor reschedules an assigned appointment
- **WHEN** the assigned Doctor reschedules their own `PENDING` or `ACCEPTED` appointment into an available interval
- **THEN** the appointment is moved to the new interval and its state is `ACCEPTED`

#### Scenario: Receptionist reschedules a declined appointment
- **WHEN** a Receptionist reschedules a `DECLINED` appointment for any Doctor into an available interval
- **THEN** the appointment is moved to the new interval and its state is `PENDING`

#### Scenario: Receptionist reschedules a pending or accepted appointment
- **WHEN** a Receptionist reschedules a `PENDING` or `ACCEPTED` appointment for any Doctor into an available interval
- **THEN** the appointment is moved to the new interval and its state is `PENDING`

#### Scenario: Doctor cannot reschedule a declined appointment
- **WHEN** a Doctor attempts to reschedule a `DECLINED` appointment
- **THEN** the service rejects the request and preserves the appointment interval and state

#### Scenario: Overlapping appointment is rejected
- **WHEN** a Receptionist or assigned Doctor attempts to book or reschedule an appointment into a slot overlapping another non-cancelled appointment for the same Doctor
- **THEN** the service reports a scheduling conflict and the original appointment schedule remains unchanged

#### Scenario: Doctor blocks time off
- **WHEN** a Doctor submits a valid time-off interval that does not overlap an existing appointment
- **THEN** the interval is persisted as unavailable time and future bookings in that interval are rejected

#### Scenario: Doctor cannot manage another Doctor's schedule
- **WHEN** a Doctor attempts to view or change an appointment or time-off interval belonging to another Doctor
- **THEN** the service rejects the request without returning or changing the other Doctor's schedule

### Requirement: The system SHALL enforce the appointment workflow from booking through checkout

Appointments SHALL support `PENDING`, `ACCEPTED`, `DECLINED`, `CHECKED_IN`, `COMPLETED`, `CHECKED_OUT`, and `CANCELLED` states. Receptionist-created appointments SHALL begin as `PENDING`, while appointments created by their assigned Doctor SHALL begin as `ACCEPTED`. A Receptionist SHALL be able to check in any accepted appointment through the dedicated check-in queue at or after its scheduled time. The assigned Doctor SHALL be able to check in their own accepted appointment at or after its scheduled time from the Doctor Dashboard. A Doctor SHALL be able to record the consultation and mark a checked-in appointment completed, and a Receptionist SHALL be able to perform checkout after completion. Receptionists and assigned Doctors SHALL be able to cancel an appointment only while it is `PENDING`, `ACCEPTED`, or `DECLINED`. Invalid state transitions SHALL be rejected without changing the appointment state.

#### Scenario: Receptionist checks in an accepted appointment from the queue
- **WHEN** a Receptionist checks in an `ACCEPTED` appointment from the Check-in Queue at or after its scheduled time
- **THEN** the appointment changes to `CHECKED_IN` and becomes available to the assigned Doctor for consultation

#### Scenario: Receptionist checks in an accepted appointment
- **WHEN** a Receptionist checks in an `ACCEPTED` appointment at or after its scheduled time
- **THEN** the appointment changes to `CHECKED_IN` and becomes available to the assigned Doctor for consultation

#### Scenario: Assigned Doctor checks in an accepted appointment from the Dashboard
- **WHEN** the assigned Doctor checks in their own `ACCEPTED` appointment from the Doctor Dashboard at or after its scheduled time
- **THEN** the appointment changes to `CHECKED_IN` and becomes available to that Doctor for consultation

#### Scenario: Doctor cannot check in another Doctor's appointment
- **WHEN** a Doctor attempts to check in an `ACCEPTED` appointment assigned to another Doctor
- **THEN** the service rejects the request and preserves the accepted state

#### Scenario: Receptionist attempts check-in before the scheduled time
- **WHEN** a Receptionist attempts to check in an `ACCEPTED` appointment before its scheduled start
- **THEN** the service rejects the transition and preserves the `ACCEPTED` state

#### Scenario: Assigned Doctor attempts check-in before the scheduled time
- **WHEN** the assigned Doctor attempts to check in their own `ACCEPTED` appointment before its scheduled start
- **THEN** the service rejects the transition and preserves the `ACCEPTED` state

#### Scenario: Doctor completes a consultation
- **WHEN** the assigned Doctor records the consultation and marks a `CHECKED_IN` appointment completed
- **THEN** the appointment changes to `COMPLETED` and the clinical record is linked to that consultation

#### Scenario: Receptionist cancels an appointment before completion
- **WHEN** a Receptionist cancels a `PENDING`, `ACCEPTED`, or `DECLINED` appointment
- **THEN** the appointment changes to `CANCELLED` and it cannot be checked in or completed

#### Scenario: Assigned Doctor cancels an appointment before check-in
- **WHEN** the assigned Doctor cancels their own `PENDING`, `ACCEPTED`, or `DECLINED` appointment
- **THEN** the appointment changes to `CANCELLED` and it cannot be checked in or completed

#### Scenario: Cancellation after check-in is rejected
- **WHEN** a Receptionist or Doctor attempts to cancel an appointment in `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` state
- **THEN** the service rejects the request and preserves the existing appointment state

#### Scenario: Invalid transition is rejected
- **WHEN** a user attempts to check in a `PENDING`, `DECLINED`, `CANCELLED`, `COMPLETED`, or `CHECKED_OUT` appointment, to complete an appointment that is not checked in, or to check out an appointment that is not completed
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

