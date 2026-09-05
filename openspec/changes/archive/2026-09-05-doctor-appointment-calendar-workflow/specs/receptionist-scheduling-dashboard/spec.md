## MODIFIED Requirements

### Requirement: The dashboard SHALL support Receptionist appointment coordination

A Receptionist SHALL be able to choose a patient and any Doctor, enter a valid date and interval, and book an appointment. Booking SHALL be presented separately from appointment search and management controls. For pending, accepted, or declined appointments, the Receptionist SHALL be able to reschedule or cancel before check-in; rescheduling any of these states SHALL result in `PENDING`. For accepted appointments at or after their start time, the Receptionist SHALL be able to check in. Existing service-layer lifecycle and Doctor-conflict rules SHALL remain authoritative.

#### Scenario: Receptionist books an available appointment
- **WHEN** valid patient, Doctor, date, start, and end values identify an available interval
- **THEN** a `PENDING` appointment is created and the dashboard refreshes to show it

#### Scenario: Receptionist attempts an unavailable interval
- **WHEN** booking or rescheduling overlaps a non-cancelled appointment or Doctor time-off
- **THEN** a clear scheduling-conflict message is shown and the previous schedule remains unchanged

#### Scenario: Receptionist books an inactive patient
- **WHEN** an inactive patient is selected for a new appointment
- **THEN** the booking is rejected with a validation message and no appointment is created

#### Scenario: Receptionist reschedules a declined appointment
- **WHEN** a `DECLINED` appointment is selected and the Receptionist submits a valid available interval
- **THEN** the appointment is rescheduled, changes to `PENDING`, and remains visible in the dashboard after refresh

#### Scenario: Receptionist cannot reschedule after check-in
- **WHEN** the Receptionist selects a `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` appointment for rescheduling
- **THEN** the reschedule action is unavailable or rejected and the appointment remains unchanged

### Requirement: Rescheduling SHALL use a patient-context popup

Selecting reschedule for an eligible `PENDING`, `ACCEPTED`, or `DECLINED` appointment SHALL open a separate popup containing the selected patient's permitted administrative details, new date/start/end controls, a reschedule action, and a cancel-appointment action. Successful actions SHALL close the popup and refresh the dashboard. The popup SHALL not permit a Receptionist to change the assigned Doctor through rescheduling.

#### Scenario: Receptionist opens rescheduling popup
- **WHEN** a `PENDING`, `ACCEPTED`, or `DECLINED` appointment is selected and reschedule is chosen
- **THEN** a popup shows the appointment patient, assigned Doctor, current status, and permitted administrative details

#### Scenario: Receptionist reschedules from popup
- **WHEN** the Receptionist submits a valid available interval in the popup
- **THEN** the appointment moves to the new interval with `PENDING` status, the popup closes, and the dashboard refreshes

#### Scenario: Receptionist cancels from popup
- **WHEN** cancel appointment is selected for a `PENDING`, `ACCEPTED`, or `DECLINED` appointment
- **THEN** the appointment becomes `CANCELLED`, the popup closes, and the dashboard refreshes

### Requirement: Dashboard updates SHALL be automatic and non-clinical

After a successful booking, reschedule, cancellation, check-in, or filter change, the dashboard SHALL reload affected data automatically. Receptionist results SHALL contain only administrative patient information and appointment coordination fields, including declined appointments that require rescheduling; diagnoses, consultation notes, follow-up notes, and prescriptions SHALL never be returned or displayed.

#### Scenario: Dashboard refreshes after a write
- **WHEN** a Receptionist successfully changes an appointment
- **THEN** the appointment list and summary reflect the new state without a manual refresh action

#### Scenario: Declined appointment remains available to Receptionist
- **WHEN** an appointment is in the `DECLINED` state
- **THEN** the Receptionist dashboard can display and select it for rescheduling while the appointment remains hidden from the assigned Doctor's Calendar and Schedule

#### Scenario: Receptionist cannot access clinical information
- **WHEN** a Receptionist uses the dashboard or its services
- **THEN** clinical records remain unavailable and unchanged

