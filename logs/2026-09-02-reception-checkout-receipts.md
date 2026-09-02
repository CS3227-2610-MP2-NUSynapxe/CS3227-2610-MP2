# Reception Checkout Receipts

## Scope

Checkout now supports searching completed appointments by patient, Doctor, and
date, then recording a payment and displaying a receipt preview. Receipt history
supports reviewing and reprinting prior receipts without creating another payment.

## Receipt rules

- Receipt sequences start at one for each Singapore-local date.
- A successful checkout creates one receipt atomically with the payment and
  checked-out appointment state.
- Duplicate checkout attempts do not create another payment or receipt.
- Receipts contain administrative appointment and payment details only.

No real patient data is recorded in this log.
