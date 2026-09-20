## Why

Doctors currently can read clinical consultation records only when they are the
Doctor assigned to the appointment. This makes it difficult for Doctors to
understand a patient's prior consultations when care is shared across the
clinic, even though the records are already retained and the clinical history
is patient-centred.

## What Changes

- Add a Doctor-facing patient clinical-history workflow reachable from the
  Patients destination, with a shortcut from the selected Dashboard
  appointment.
- Allow any authenticated Doctor to view completed or checked-out
  consultations for any patient in read-only mode.
- Keep consultation editing, prescription creation, and other clinical writes
  restricted to the Doctor assigned to the appointment.
- Keep in-progress clinical records restricted to the assigned Doctor.
- Preserve the existing administrative patient directory as a clinical-data
  boundary; the new history view is a separate Doctor-only clinical view.
- Do not add an audit trail for cross-Doctor viewing.
- Leave Receptionist checkout, receipts, revenue reports, and their clinical
  confidentiality boundary unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `clinic-workflow`: change clinical-record read authorization so any Doctor
  can view completed or checked-out consultations while only the assigned
  Doctor can create or edit them.
- `doctor-patient-directory`: add a Doctor-only patient clinical-history view
  without mixing clinical controls into Receptionist administration or
  changing existing patient-directory operations.

## Impact

- Doctor clinical authorization and clinical-record query APIs will need a
  read path separate from assigned-Doctor write authorization.
- The Doctor Patients workspace will need a history list and read-only record
  detail view, plus a Dashboard shortcut for the selected patient.
- Clinical-history queries will need patient, appointment, Doctor, and status
  data sufficient to present chronological completed consultations.
- Existing Receptionist services, checkout UI, receipts, revenue reports, and
  administrative patient projections remain unchanged.
- Tests and documentation must cover cross-Doctor read access, assigned-Doctor
  write access, protection of in-progress records, and continued Receptionist
  denial of clinical data.
