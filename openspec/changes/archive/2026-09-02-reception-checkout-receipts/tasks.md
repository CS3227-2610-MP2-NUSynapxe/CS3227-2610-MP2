## 1. Receipt persistence and numbering

- [x] 1.1 Add receipt domain and repository models with immutable receipt details, payment linkage, local receipt date, and daily sequence number.
- [x] 1.2 Add the receipts table, unique daily sequence constraint, indexes, and a migration/backfill for existing successful payments.
- [x] 1.3 Make successful checkout allocate and persist exactly one receipt atomically with the payment and checked-out appointment state.
- [x] 1.4 Add repository queries for completed appointments and receipt history with Patient ID/name, Doctor, date, and receipt-number filters.

## 2. Billing service and authorization

- [x] 2.1 Add Receptionist-authorized completed-appointment search and receipt-history retrieval APIs using administrative projections only.
- [x] 2.2 Add receipt retrieval APIs that do not create payments or mutate appointment state.
- [x] 2.3 Reject duplicate checkout while preserving the existing payment, receipt, and checked-out state.

## 3. Receptionist checkout UI

- [x] 3.1 Split Checkout into `Ready for checkout` and `Receipt history` tabs with stable semantic IDs.
- [x] 3.2 Add completed-appointment filters for Patient ID/name, Doctor, and appointment date, with automatic result refresh.
- [x] 3.3 Add receipt preview after successful checkout showing administrative fields, payment details, receipt number, and Singapore timestamp.
- [x] 3.4 Add receipt history filtering and viewing without creating another payment; refresh lists after checkout.
- [x] 3.5 Keep diagnoses, consultation notes, follow-up notes, prescriptions, and other clinical fields out of search results and receipts.

## 4. Automated verification

- [x] 4.1 Add repository tests for completed-appointment filters, receipt persistence, history filters, sequence numbering, uniqueness, and legacy backfill.
- [x] 4.2 Add service/integration tests for authorization, atomic checkout receipt creation, duplicate checkout, reprint immutability, and confidentiality.
- [x] 4.3 Add TestFX coverage for checkout tabs, filters, receipt preview, history, reprint, validation feedback, and automatic refresh.

## 5. Documentation and quality

- [x] 5.1 Update User Guide and Developer Guide with checkout search, receipt preview, history, sequence numbers, and reprint behavior.
- [x] 5.2 Add `logs/reception-checkout-receipts.md` without real patient data.
- [x] 5.3 Run Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Javadoc, the full test suite, and strict OpenSpec validation.
- [x] 5.4 Commit implementation, tests, and documentation separately; do not push unless explicitly requested.
