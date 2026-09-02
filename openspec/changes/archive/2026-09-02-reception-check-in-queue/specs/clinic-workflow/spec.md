## MODIFIED Requirements

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
