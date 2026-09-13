## MODIFIED Requirements

### Requirement: The system SHALL support conflict-free appointment scheduling across the clinic

An authenticated Receptionist SHALL be able to book, cancel, and reschedule appointments for any Doctor. An authenticated Doctor SHALL be able to create, view, and manage only appointments assigned to that Doctor. A Doctor-created appointment SHALL start in the `ACCEPTED` state; a Receptionist-created appointment SHALL start in the `PENDING` state. An assigned Doctor SHALL be able to accept or decline their own pending or accepted appointments and reschedule their own pending or accepted appointments. A Receptionist SHALL be able to reschedule pending, accepted, or declined appointments for any Doctor. A Doctor SHALL NOT reschedule a declined appointment or change another Doctor's appointment. A Doctor's reschedule SHALL leave the appointment accepted, while a Receptionist's reschedule SHALL leave it pending. The system SHALL reject an appointment whose time interval overlaps another appointment for the same Doctor in `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` status, or overlaps blocked time for that Doctor, and SHALL leave the prior schedule unchanged when a booking or reschedule is rejected. `DECLINED` and `CANCELLED` appointments SHALL remain recorded but SHALL NOT reserve their former intervals.

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
- **THEN** the appointment changes to `DECLINED`, remains available for Receptionist coordination, and releases its former interval for another appointment or time off

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
- **WHEN** a Receptionist or assigned Doctor attempts to book or reschedule an appointment into a slot overlapping an appointment for the same Doctor in `PENDING`, `ACCEPTED`, `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` status
- **THEN** the service reports a scheduling conflict and the original appointment schedule remains unchanged

#### Scenario: Declined appointment does not block a replacement
- **WHEN** a Receptionist or assigned Doctor books an appointment in an interval occupied only by a `DECLINED` appointment
- **THEN** the new appointment is created and the declined appointment remains recorded with its existing details and state

#### Scenario: Doctor blocks time off
- **WHEN** a Doctor submits a valid time-off interval that does not overlap an availability-blocking appointment or existing time off
- **THEN** the interval is persisted as unavailable time and future bookings in that interval are rejected

#### Scenario: Doctor blocks time formerly occupied by a declined appointment
- **WHEN** a Doctor submits valid time off in an interval occupied only by a `DECLINED` appointment
- **THEN** the time off is persisted and the declined appointment remains recorded with its existing details and state

#### Scenario: Doctor cannot manage another Doctor's schedule
- **WHEN** a Doctor attempts to view or change an appointment or time-off interval belonging to another Doctor
- **THEN** the service rejects the request without returning or changing the other Doctor's schedule

## ADDED Requirements

### Requirement: Doctor time-off intervals SHALL be reversible by their owner

An authenticated Doctor SHALL be able to remove an existing time-off interval owned by that Doctor. Removing time off SHALL delete only that interval and SHALL NOT change appointment assignments, times, or lifecycle states. A different Doctor, a Receptionist, or another role SHALL NOT be able to remove the interval through the Doctor operation.

#### Scenario: Doctor removes their own time off
- **WHEN** an authenticated Doctor requests removal of an existing time-off interval owned by that Doctor
- **THEN** only that interval is deleted and its former time becomes available subject to all other appointment and conflict rules

#### Scenario: Doctor attempts to remove another Doctor's time off
- **WHEN** an authenticated Doctor requests removal of an interval owned by another Doctor
- **THEN** the operation is rejected without revealing or changing the other Doctor's interval

#### Scenario: Non-Doctor attempts to remove Doctor time off
- **WHEN** an authenticated user without the Doctor role invokes the Doctor time-off removal operation
- **THEN** the operation is rejected and the interval remains unchanged

#### Scenario: Doctor removes a missing interval
- **WHEN** a Doctor requests removal of a time-off identifier that does not exist for that Doctor
- **THEN** the operation reports a safe not-found outcome and changes no schedule data
