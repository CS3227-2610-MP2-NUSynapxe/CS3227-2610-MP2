## Context

The current Receptionist workspace has daily revenue and receipt history, but
not a reusable filtered report. Receipts already persist the payment amount,
method, Singapore-local date, timestamp, patient, and Doctor relationships.

## Approach

- Add a report projection that joins successful payments to their receipts and
  administrative patient/Doctor projections.
- Support one-day and inclusive date-range queries with optional patient,
  Doctor, and payment-method filters.
- Keep aggregation in the service/repository layer so UI totals and exports
  use the same result set.
- Use the existing Receptionist authorization boundary and never select
  diagnoses, consultation notes, prescriptions, or follow-up notes.
- Present a separate `Revenue Reports` tab with filters, summary cards,
  breakdown tables, detail rows, and CSV/JSON export buttons.
- Use `Asia/Singapore` when grouping dates and formatting payment timestamps.

## UI Flow

```text
Filters -> Generate report -> Summary + breakdowns + receipt rows
                         \-> Export CSV/JSON
```

## Consistency and Failure Handling

- A successful checkout remains the only event that creates a receipt.
- Reports include only successful payments with linked receipts.
- Invalid ranges and malformed dates produce feedback without replacing the
  last valid report unexpectedly.
- Empty results show zero totals and an explicit empty state.
