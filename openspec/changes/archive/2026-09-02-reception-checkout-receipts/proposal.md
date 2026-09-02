## Why

The current Receptionist checkout records a payment and updates appointment status, but it does not provide a durable receipt workflow. Receptionists need to find completed visits quickly, review prior receipts, and reprint a receipt without accidentally charging a patient twice.

## What Changes

- Add a Receptionist checkout receipt workflow for completed appointments.
- Search completed appointments by Patient ID/name, Doctor, and appointment date.
- Generate a unique daily receipt sequence number when payment succeeds.
- Display receipt details in a preview after checkout.
- Provide receipt history for previously paid appointments.
- Allow viewing an existing receipt without creating another payment.
- Preserve atomic payment and appointment state updates and prevent duplicate checkout.
- Keep receipts and receipt history administrative-only with no clinical information.
- Add repository, service, integration, and TestFX tests plus guide and log updates.

## Capabilities

### New Capabilities

- `reception-checkout-receipts`: Completed-appointment search, receipt generation, receipt history, daily numbering, and receipt viewing.

### Modified Capabilities

- `clinic-workflow`: Extend Receptionist checkout behavior with receipt creation and retrieval while preserving payment validation and lifecycle rules.

## Impact

- Payment persistence gains receipt metadata and a daily sequence allocation strategy.
- Billing service APIs gain filtered completed-appointment and receipt-history operations plus receipt retrieval support.
- Receptionist checkout UI gains search filters, receipt history, and preview actions.
- Existing payments and checkout states must remain readable; migration/backfill behavior will be specified in the design.
- No external service or runtime dependency is required.
