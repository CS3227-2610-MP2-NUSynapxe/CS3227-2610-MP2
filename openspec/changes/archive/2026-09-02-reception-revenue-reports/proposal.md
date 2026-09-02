## Why

Receptionists need a clear, auditable view of clinic income beyond individual
receipt records. A date-filtered revenue report will make daily reconciliation,
payment-method review, and doctor-level summaries quick and consistent with
the persisted checkout receipts.

## What Changes

- Add a Receptionist-only Revenue Reports dashboard.
- Support date/date-range, patient, doctor, and payment-method filters.
- Show total revenue, successful payment count, and receipt count.
- Show payment-method and doctor breakdowns.
- List matching receipt/payment details, including Singapore payment date/time.
- Keep cancelled appointments and failed payments out of all totals.
- Allow report results to be exported as CSV or JSON.
- Use persisted receipt and successful payment data as the reporting source.

## Capabilities

### New Capabilities

- `reception-revenue-reports`: Filtered revenue summaries, breakdowns, and
  receipt-backed report details for Receptionists.

### Modified Capabilities

- `clinic-workflow`: Extend Receptionist reporting behavior to aggregate only
  successful checkout payments and their receipts.

## Impact

- Add repository and service queries for filtered revenue aggregation.
- Extend the Receptionist workspace with a Revenue Reports tab and export
  actions.
- Reuse payment and receipt persistence without changing checkout semantics.
- Add repository, service, and TestFX coverage plus guide updates.
