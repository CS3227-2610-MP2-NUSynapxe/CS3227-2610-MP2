## MODIFIED Requirements

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

#### Scenario: Revenue excludes cancelled or unsuccessful transactions
- **WHEN** the selected date contains a cancelled appointment or an unsuccessful payment attempt
- **THEN** those records contribute zero to the successful payment count and total
