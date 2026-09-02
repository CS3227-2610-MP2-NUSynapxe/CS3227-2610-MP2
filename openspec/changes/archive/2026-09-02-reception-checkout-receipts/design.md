## Context

The application stores checkout payments in SQLite and atomically changes a completed appointment to `CHECKED_OUT`. `Payment` currently contains amount, method, status, and recording timestamp, while the Receptionist Checkout tab lists appointments and records payments. See the proposal and specs for the receipt workflow contract.

## Goals / Non-Goals

**Goals:**

- Make completed appointments searchable before checkout.
- Persist receipt identity and contents independently from payment reprints.
- Allocate unique daily receipt sequence numbers safely under concurrent writes.
- Show receipt history and reprint an existing receipt without mutating payment state.
- Preserve administrative-only visibility and existing atomic checkout behavior.

**Non-Goals:**

- No tax, discounts, refunds, voids, or partial payments.
- No PDF library or external printing service; preview and plain-text export are sufficient for this change.
- No clinical content on receipts.
- No change to Doctor billing authorization unless separately specified.

## Decisions

Use a dedicated one-to-one `receipts` table linked to a successful payment rather than adding presentation fields directly to `payments`. This keeps payment history compatible and lets reprints read an immutable receipt snapshot. The table stores receipt ID, payment ID, Singapore-local receipt date, daily sequence, generated timestamp, and the administrative snapshot needed to reproduce the receipt.

Allocate the next sequence inside the same SQLite transaction as checkout. Compute the next number for the local receipt date and enforce a unique `(receipt_date, sequence_number)` constraint. If a concurrent allocation conflicts, retry the transaction. This is preferred over deriving a number in Java after payment because it cannot leave a paid payment without a receipt.

Expose completed-appointment search and receipt-history queries through Receptionist-authorized service methods. Search joins only patients' administrative projection and Doctor account display names. Reprint loads the persisted receipt snapshot and never calls checkout or updates appointment status.

Use the existing Checkout feature with two sub-tabs: `Ready for checkout` and `Receipt history`. Selecting a ready appointment opens a modal checkout window containing administrative details, amount/method controls, and the Complete checkout action; the main tab remains focused on searching and selecting appointments. A receipt preview appears after success. Selecting history shows receipt number, date, patient, Doctor, amount, method, and a reprint action. All list updates after filters and successful checkout without a manual refresh button.

For existing successful payments without receipt rows, the migration creates deterministic receipts ordered by Singapore-local recorded date and payment ID. This makes historical payments reprintable without changing their amounts or appointment states.

## Risks / Trade-offs

- [Risk] Receipt numbering can collide under concurrent checkout → unique database constraint and transactional allocation with retry.
- [Risk] Legacy payments may not have a receipt snapshot → migration backfills administrative fields from current projections; clinical fields are never copied.
- [Risk] Patient or Doctor names may later change → receipt snapshot preserves what was issued while search uses current administrative data.
- [Risk] Printing differs by platform → provide preview and plain-text export with a clear success message rather than assuming a printer is configured.

## Migration Plan

Add the receipts table, indexes, foreign key, and backfill migration in a new schema version. Existing payment and appointment rows remain valid. On rollback, remove only receipt rows/table created by this change; payment and appointment records remain intact.
