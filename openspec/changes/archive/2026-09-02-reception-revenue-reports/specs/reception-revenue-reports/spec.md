## Purpose

Provides Receptionists with accurate, filterable revenue reports backed by
successful checkout payments and their persisted receipts.

## ADDED Requirements

### Requirement: Revenue reports SHALL support filtering

The Revenue Reports view SHALL support a date or date-range filter and optional
patient ID/name, Doctor, and payment-method filters.

#### Scenario: Receptionist generates a filtered report
- **WHEN** a Receptionist supplies valid filters and generates a report
- **THEN** the report lists only matching successful payments and receipts

#### Scenario: Invalid date range is rejected
- **WHEN** the end date is earlier than the start date or a date is malformed
- **THEN** the system reports a validation error and does not show stale totals as current

### Requirement: Revenue summaries SHALL reconcile to receipts

The report SHALL show successful payment count, receipt count, total revenue,
payment-method breakdown, and Doctor breakdown. All dates and timestamps SHALL
use the Singapore clinic timezone.

#### Scenario: Summary includes successful checkouts
- **WHEN** a report is generated for a date containing successful checkouts
- **THEN** totals and breakdowns equal the matching persisted receipt/payment records

#### Scenario: Failed and cancelled activity is excluded
- **WHEN** the selected period contains failed payments or cancelled appointments
- **THEN** those records do not contribute to counts, totals, or breakdowns

### Requirement: Revenue reports SHALL show receipt details

The report SHALL list receipt number, payment date/time, Patient ID and name,
Doctor name, amount, and payment method without exposing clinical information.

#### Scenario: Receipt-backed detail rows are displayed
- **WHEN** a report contains matching successful payments
- **THEN** each row displays its persisted receipt and administrative details

### Requirement: Revenue reports SHALL support export

The Revenue Reports view SHALL allow the currently filtered results to be
exported as CSV or JSON, preserving the displayed totals and detail rows.

#### Scenario: Receptionist exports a report
- **WHEN** a Receptionist chooses CSV or JSON export for a generated report
- **THEN** the system creates a file containing the filtered summary and receipt details
