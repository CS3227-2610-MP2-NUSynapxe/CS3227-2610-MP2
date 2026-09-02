## Purpose

Provides Receptionists with searchable, auditable checkout receipts that can be reviewed without duplicating payments or exposing clinical information.

## ADDED Requirements

### Requirement: Completed appointments SHALL be searchable for checkout

The Checkout view SHALL list completed appointments that are ready for payment and SHALL support filtering by Patient ID or name, Doctor, and appointment date. Search results SHALL show only administrative patient and appointment information.

#### Scenario: Receptionist searches completed appointments
- **WHEN** a Receptionist enters a patient query, selects a Doctor, or chooses an appointment date
- **THEN** the view shows only matching completed appointments ready for checkout

#### Scenario: Search excludes clinical information
- **WHEN** a Receptionist views checkout search results
- **THEN** diagnoses, consultation notes, follow-up notes, and prescriptions are not displayed

### Requirement: Successful checkout SHALL generate a receipt

After a valid payment is recorded for a completed appointment, the system SHALL generate a receipt containing a unique daily sequence number, Singapore-local receipt timestamp, patient identity, Doctor name, appointment interval, amount, payment method, payment status, and Receptionist identity. Receipt content SHALL not contain clinical information.

#### Scenario: Receipt is generated after payment
- **WHEN** a Receptionist completes checkout with a valid positive amount and payment method
- **THEN** the system persists one successful payment and one receipt and displays the receipt preview

#### Scenario: Receipt sequence is daily
- **WHEN** multiple receipts are generated on the same Singapore-local date
- **THEN** their sequence numbers are unique and increase in creation order for that date

### Requirement: Receipt history SHALL support review

The Receipts sub-tab SHALL provide receipt history for previously paid appointments, searchable by Patient ID or name, Doctor, and receipt date. Selecting a receipt SHALL show its persisted details and SHALL NOT create another payment, receipt, or checked-out transition.

#### Scenario: Receptionist reviews receipt history
- **WHEN** a Receptionist opens receipt history or applies its filters
- **THEN** previously paid receipts matching the filters are listed with receipt number, patient, Doctor, date, amount, and payment method

### Requirement: Receipt operations SHALL prevent duplicate charges

The service SHALL reject checkout for an appointment that already has a successful payment and SHALL preserve the existing payment, receipt, and checked-out state. Receipt retrieval and reprinting SHALL remain available after the duplicate attempt.

#### Scenario: Duplicate checkout is rejected
- **WHEN** a Receptionist submits checkout for an appointment that is already paid
- **THEN** the system reports that checkout is already complete and creates no additional payment or receipt
