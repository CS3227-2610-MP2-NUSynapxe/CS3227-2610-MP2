## 1. Revenue data and authorization

- [ ] 1.1 Add revenue report projection/domain types for summary, breakdowns, and receipt detail rows.
- [ ] 1.2 Add repository queries for inclusive date ranges and patient, Doctor, and payment-method filters.
- [ ] 1.3 Add Receptionist-authorized BillingService report APIs using successful payments and linked receipts only.

## 2. Report UI

- [ ] 2.1 Add a separate Revenue Reports workspace tab with date/date-range and optional filters.
- [ ] 2.2 Display totals, payment-method and Doctor breakdowns, and receipt-backed detail rows.
- [ ] 2.3 Add CSV and JSON export actions for the current filtered report.
- [ ] 2.4 Use Singapore date/time formatting and clear validation/empty-state feedback.

## 3. Verification

- [ ] 3.1 Add repository tests for date ranges, filters, aggregation, and exclusion of failed/cancelled activity.
- [ ] 3.2 Add service tests for Receptionist authorization and report/receipt reconciliation.
- [ ] 3.3 Add TestFX coverage for filters, summaries, empty results, detail rows, and exports.
- [ ] 3.4 Update User Guide, Developer Guide, and implementation log.
- [ ] 3.5 Run Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Javadoc, tests, and strict OpenSpec validation.
