## Context

The application is a JavaFX desktop clinic system backed by one SQLite database. The current `DoctorView` is a schedule and consultation workspace, while `ReceptionistView` already contains the patient registration, search, edit, and activation/deactivation workflow. `PatientService` and `PatientRepository` expose an administrative patient projection that excludes clinical fields. `Authorization` currently restricts those administrative operations to Receptionists.

Patient IDs are database-generated numeric primary keys. Existing foreign keys connect patients to appointments, clinical records, payments, and receipts; prescriptions are connected through clinical records. SQLite is opened with foreign-key enforcement enabled, and the current schema has an `active` flag but no cascading delete rules. The earlier Receptionist patient-directory change deliberately excluded Doctor administration and hard deletion, so this change supersedes those two boundaries while retaining its validation, identity, projection, and status behavior.

## Goals / Non-Goals

Goals:

- Give Doctors and Receptionists one consistent, service-authorized administrative patient directory.
- Preserve the existing patient validation, generated Patient ID, identity uniqueness, administrative projection, and activation/deactivation behavior.
- Add a Doctor workspace rail with `Dashboard` for the existing schedule/consultation view and `Patients` for the shared administrative directory.
- Permit physical deletion only for an unused patient, with an atomic relationship check, no cascade, explicit confirmation, and a useful blocking modal.
- Keep the Doctor's clinical data access limited to the assigned-appointment workflow.
- Refresh search results and patient selectors after successful directory mutations.
- Cover the service, persistence, authorization, integration, JavaFX, and documentation boundaries.

Non-goals:

- Giving Doctors access to another Doctor's clinical records or exposing clinical fields in the patient directory.
- Deleting, anonymizing, merging, or repairing patients that have related clinic history.
- Changing appointment, clinical-record, prescription, payment, receipt, or revenue workflows beyond the references needed for deletion checks and inactive-patient booking protection.
- Giving System Admins patient-directory access, adding bulk patient actions, or adding patient self-service.
- Adding a schema migration or a new runtime dependency.

## Decisions

### Use one administrative authorization policy for Doctors and Receptionists

Add a central authorization helper for patient administration that accepts only an authenticated `DOCTOR` or `RECEPTIONIST` session. Apply it in every `PatientService` administrative method: search, list, retrieve, register, update, activation, deactivation, deletion preflight, and deletion. Keep the existing Doctor-ownership check on clinical operations unchanged. UI visibility is a convenience; service authorization remains the enforcement boundary for direct calls and future clients.

This avoids copying role checks into two views and prevents the Doctor's new directory screen from becoming an accidental bypass of clinical privacy. System Admin and missing-session requests continue to fail without returning patient data.

### Extract and reuse the existing administrative patient workflow

Move the patient form, administrative list, selected-patient details window, validation feedback, status action, and refresh callbacks currently owned by `ReceptionistView` into a shared patient-directory UI component. Supply the current session, services, workspace-specific control-ID prefix, and refresh hooks to the component. Preserve existing Receptionist IDs and labels where they are already used by TestFX; give the Doctor surface stable `doctor-*` IDs.

The shared component will continue to keep registration separate from search/manage state. Selecting a result opens the owned details window containing only administrative fields. The details window adds a Delete action for both roles and keeps the existing Activate/Deactivate action separate from deletion, so deactivation is not presented as a disguised delete.

### Make Doctor Dashboard the existing view and Patients a sibling destination

Retain the current schedule, assigned appointment selection, consultation editor, prescriptions, and completion controls as the Doctor `Dashboard` content. Wrap that content and the shared patient directory in a Doctor workspace shell with a left navigation rail or equivalent clearly labeled navigation. Selecting `Patients` swaps the content area and refreshes its directory; selecting `Dashboard` returns to the existing appointment/clinical content. The active destination is styled and exposed through stable semantic IDs.

The patient directory does not receive a clinical service or appointment ownership context. A Doctor can administer basic data for any directory patient, but clinical records remain reachable only from that Doctor's assigned appointment workflow.

### Represent deletion blockers as structured, non-sensitive data

Introduce a small deletion-check result containing the patient ID and blocking categories/counts, plus a service-level blocked-deletion error or result that the JavaFX layer can handle without parsing SQL text. The categories cover the current relationship graph: appointments, clinical records, prescriptions reached through those clinical records, payments, receipts, and any other patient-reference category maintained by the schema. The result contains counts and safe category labels, never clinical contents or complete identity numbers.

The repository owns the relationship query because it already owns the SQLite connection and administrative patient projection. It will keep one canonical inventory of current patient-reference paths and add tests whenever a new patient relationship is introduced. The final foreign-key constraint remains a last-line safeguard and is translated into the same safe blocked-deletion feedback if a concurrent or newly introduced reference appears.

### Perform the preflight and delete as an atomic, non-cascading operation

The UI may request a deletion check to decide whether to open confirmation, but the final delete operation repeats the check and executes `DELETE FROM patients WHERE id = ?` in one write transaction. If any blocker exists, the transaction does not delete. If the final delete encounters a foreign-key violation, it rolls back, refreshes the blocker information, and reports the deletion as blocked. No repository code will delete child rows, and no `ON DELETE CASCADE` rule will be added.

The current relationship checks count appointments, clinical records, payments, receipts, and prescriptions joined through clinical records. The database's existing `PRAGMA foreign_keys = ON` and default restrictive references protect against orphaning even if a caller bypasses the JavaFX preflight. A patient not found error remains distinct from a related-data block.

### Use an owned modal for both confirmation and blocked deletion feedback

When the deletion check reports no blockers, the patient-details window opens an explicit confirmation dialog identifying the Patient ID and warning that the operation is permanent. On confirmation, the service performs the authoritative atomic operation. If deletion is blocked, the UI opens an owned modal listing every reported category and count, explaining that the patient and history were preserved, and identifying deactivation as the safe alternative. The modal has a close/cancel path and does not silently deactivate the patient.

The UI reloads the selected patient and directory after failures caused by stale state. This prevents a previously selected unused patient from being treated as unused after another workflow creates a relationship.

### Keep activation/deactivation as the reversible history-preserving operation

Retain the `active` column and the existing status methods. Extend their authorization to Doctors while keeping the behavior unchanged: deactivation leaves the patient and all history intact, keeps the patient searchable, and causes appointment booking to reject the patient through the existing active-patient check; activation restores booking eligibility subject to normal scheduling rules. Status changes are not prerequisites for deletion and never remove related data.

After a status change, the shared directory refreshes its result list and any appointment patient selectors. Inactive patients remain available for administrative search and for viewing retained history, but are excluded from new-booking selectors as they are today.

### Keep schema and dependency scope unchanged

No schema version is introduced. The existing `active` column, patient foreign keys, restrictive default delete behavior, and foreign-key pragma supply the required structure. No external service or library is needed. Persistence tests will assert the current schema assumptions so a future change cannot accidentally enable cascading deletion or disable foreign-key enforcement.

## Risks / Trade-offs

- Physical deletion is irreversible, so it is limited to patients with no recognized related rows, requires confirmation, and retains database foreign-key protection as a final guard.
- A preflight can become stale between display and confirmation. Repeating it inside the final transaction and handling a foreign-key failure closes that gap without allowing partial deletion.
- Doctors gain access to more personally identifiable administrative information. Central service authorization, administrative-only projections, and explicit clinical-workflow separation limit the expanded access.
- Extracting the current Receptionist controls may temporarily increase UI wiring complexity. Shared form state and prefix-based IDs reduce long-term duplication while preserving existing Receptionist behavior.
- Large directories may make a blank search expensive. The existing indexed identity/appointment paths and deterministic patient ordering remain in place; performance tests or pagination are outside this change unless the current UI proves unusable.
- Blocker counts can expose the existence of a patient's history to an already authorized staff member. This is intentional for safe administration, while the modal omits clinical content and sensitive identity values.

## Migration Plan

No database migration is required. Deploy the service and UI changes against the current schema version. Existing patients with appointments, clinical records, prescriptions, payments, receipts, or other references remain intact and will receive a blocked-deletion explanation. Existing inactive patients remain inactive and searchable. Existing Receptionist patient actions retain their behavior, with Doctor authorization and the guarded Delete action added.

Before implementation is considered complete, run the relationship fixture tests against a fresh database and an existing database opened with foreign keys enabled, then run the focused service/UI tests, the project quality gate, and strict OpenSpec validation.

## Open Questions

None. Activation/deactivation remains the safe history-preserving alternative, and physical deletion is intentionally limited to immediately correcting an unused patient record.
