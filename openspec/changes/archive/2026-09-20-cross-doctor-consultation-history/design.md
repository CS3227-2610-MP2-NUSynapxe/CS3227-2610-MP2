## Context

The existing clinical service uses the assigned Doctor ownership check for
both reading and writing a consultation. The Doctor Dashboard loads one
selected appointment at a time, while the Doctor Patients destination embeds
the shared administrative patient directory. Completed and checked-out
appointments already render clinical fields read-only in the Dashboard.

The change is defined by `proposal.md` and the deltas in
`specs/clinic-workflow/spec.md` and
`specs/doctor-patient-directory/spec.md`. Receptionist projections and
checkout services remain administrative and are outside this design.

## Goals / Non-Goals

**Goals:**

- Give any authenticated Doctor read-only access to completed and checked-out
  consultation records for any patient.
- Keep in-progress consultations private to their assigned Doctor.
- Keep all clinical writes restricted to the assigned Doctor.
- Provide a patient-centred history view in the Doctor Patients destination and
  a Dashboard shortcut into the same view.
- Keep clinical history out of the shared administrative patient directory and
  preserve the existing Receptionist boundary.

**Non-Goals:**

- Receptionist access to consultations or prescriptions.
- Cross-Doctor editing, prescription changes, appointment lifecycle changes, or
  clinical corrections.
- Viewing in-progress consultations owned by another Doctor.
- Audit logging, billing changes, receipt changes, or revenue-report changes.
- A new top-level navigation destination or a second calendar/history renderer.

## Decisions

### 1. Split clinical read authorization from clinical write authorization

The service layer will use two distinct policies:

- A Doctor-role check plus a terminal-status check for reading a completed or
  checked-out record.
- The existing Doctor-ownership check for saving consultation notes, adding
  prescriptions, and every other clinical mutation.

This is preferred over removing ownership checks from the current methods,
because the current methods serve the editable Dashboard workflow. The read
path must reject another Doctor's `CHECKED_IN` record before returning either
the record or its prescriptions. Receptionists must continue to fail the
Doctor-role check.

### 2. Query history as a patient-centred clinical projection

Add a repository/service read operation that returns a patient's clinical
history joined to its appointment context. The result should provide the
appointment date/time, lifecycle status, assigned Doctor display name,
clinical record, and prescriptions. The query will include only
`COMPLETED` and `CHECKED_OUT` appointments, order newest first, and use a
stable appointment identifier as a tie-breaker.

The existing `ClinicalRecord` and `Prescription` records remain the clinical
write/read projections. A separate history DTO or equivalent view projection
will carry appointment and display metadata instead of adding UI concerns to
those domain records.

### 3. Keep administrative Patients and clinical history visibly separate

The Doctor Patients destination will retain the existing directory for search,
registration, and administrative editing. A separate Doctor-only history panel
or detail state will be opened after a patient is selected. It will have its
own read-only clinical detail presentation and will not add clinical columns or
controls to the shared directory table.

The Dashboard selected-appointment detail will provide a shortcut to this
patient history. It will reuse the same history component or state rather than
creating a second clinical-history implementation.

This is preferred over putting history in Calendar or Agenda, because those
views are scheduling projections and intentionally exclude clinical data. It
is also preferred over a new top-level History destination because the patient
context is clearer from Patients and the Dashboard shortcut remains available
at the point of care.

### 4. Treat completed history as read-only for every Doctor

History detail fields and prescription rows will not expose save, add, edit,
or completion controls. The assigned Doctor's existing `CHECKED_IN` Dashboard
workflow remains the only editing path. Selecting a historical record from
another Doctor will never switch the current user into an editable state.

### 5. Preserve data and authorization boundaries

The history query will take a patient identifier and authenticated Doctor
session, validate the session at the service boundary, and return only
completed/checked-out clinical records associated with that patient. It will
not reuse administrative patient projections or add clinical fields to
Receptionist-facing repository methods. No audit rows or schema migration are
needed for this change.

## Risks / Trade-offs

- **[Risk]** Any Doctor can see highly sensitive patient information.
  **Mitigation:** limit access to authenticated Doctors, expose only terminal
  records, keep the view read-only, and preserve service-layer checks rather
  than relying on hidden UI controls.
- **[Risk]** A new history panel could accidentally leak clinical fields into
  the shared directory. **Mitigation:** keep the history projection and UI
  separate from administrative patient DTOs and table columns; add a
  Receptionist regression test.
- **[Risk]** History queries may return records in an unstable or confusing
  order. **Mitigation:** sort by Singapore-local appointment start time
  descending and appointment ID descending, and display the Doctor and status
  for every entry.
- **[Risk]** Existing Dashboard loading currently assumes the selected Doctor
  owns the appointment. **Mitigation:** retain the current assigned-Doctor
  loader for editable Dashboard consultations and use a separate read path for
  history; do not broaden mutation methods.
- **[Risk]** A large patient's history may make the JavaFX detail view slow or
  difficult to scan. **Mitigation:** use a compact summary list with a
  selected-record detail pane, bounded initial loading, and explicit empty and
  loading states. Keep pagination or incremental loading as an implementation
  task if the existing data volume requires it.

## Migration Plan

No database migration is expected. Existing clinical records already contain
patient, appointment, and Doctor relationships. Implementation will add the
read projection, service authorization path, Doctor UI states, tests, and
documentation. If the feature must be rolled back, remove the history entry
point and read-only query path; existing clinical records and the assigned
Doctor editing workflow remain intact.

## Open Questions

None. The scope decisions that affect authorization, status visibility,
editing, audit logging, and Receptionist checkout were resolved before this
design.
