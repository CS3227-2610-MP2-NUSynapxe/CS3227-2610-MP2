## 1. Revenue data and authorization

- [x] 1.1 Add revenue report projection/domain types for summary, breakdowns, and receipt detail rows.
- [x] 1.2 Add repository queries for inclusive date ranges and patient, Doctor, and payment-method filters.
- [x] 1.3 Add Receptionist-authorized BillingService report APIs using successful payments and linked receipts only.

## 2. Report UI

- [x] 2.1 Add a separate Revenue Reports workspace tab with date/date-range and optional filters.
- [x] 2.2 Display totals, payment-method and Doctor breakdowns, and receipt-backed detail rows.
- [x] 2.3 Add CSV and JSON export actions for the current filtered report.
- [x] 2.4 Use Singapore date/time formatting and clear validation/empty-state feedback.

## 3. Verification

- [x] 3.1 Add repository tests for date ranges, filters, aggregation, and exclusion of failed/cancelled activity.
- [x] 3.2 Add service tests for Receptionist authorization and report/receipt reconciliation.
- [x] 3.3 Add TestFX coverage for filters, summaries, empty results, detail rows, and exports.
- [x] 3.4 Update User Guide, Developer Guide, and implementation log.
- [x] 3.5 Run Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Javadoc, tests, and strict OpenSpec validation.
