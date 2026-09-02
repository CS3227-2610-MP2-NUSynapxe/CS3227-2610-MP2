## MODIFIED Requirements

### Requirement: A Receptionist SHALL be able to record checkout payments and generate a daily revenue summary

After an appointment is completed, a Receptionist SHALL be able to record a valid checkout charge and payment method for the patient. The service SHALL reject negative, zero, malformed, or otherwise invalid payment amounts and SHALL reject a second successful checkout for the same appointment. A successful checkout SHALL create a receipt with a unique Singapore-local daily sequence number and persisted receipt timestamp. Receptionists SHALL be able to retrieve and view receipts without creating another payment. Revenue reports SHALL aggregate only successful checkout payments and their linked receipts, excluding cancelled appointments and unsuccessful payment attempts.

#### Scenario: Receptionist completes checkout
- **WHEN** a Receptionist records a valid positive payment for a completed appointment
- **THEN** the payment is persisted, a receipt is generated, the payment is associated with the appointment and patient, and the appointment is marked checked out

#### Scenario: Invalid payment amount is rejected
- **WHEN** a Receptionist submits a zero, negative, malformed, or missing payment amount
- **THEN** checkout is rejected and no payment, receipt, or checked-out state is persisted

#### Scenario: Daily revenue includes successful payments for the selected date
- **WHEN** a Receptionist requests the revenue summary for a local clinic date
- **THEN** the summary reports the count and total of successful payments recorded on that date, grouped from persisted checkout records

#### Scenario: Revenue report reconciles to checkout records
- **WHEN** a Receptionist requests a report for a selected date or date range
- **THEN** the report totals and detail rows are derived from successful persisted payments and linked receipts

#### Scenario: Revenue excludes cancelled or unsuccessful transactions
- **WHEN** the selected period contains cancelled appointments or unsuccessful payment attempts
- **THEN** those transactions are excluded from the report count, totals, and breakdowns
