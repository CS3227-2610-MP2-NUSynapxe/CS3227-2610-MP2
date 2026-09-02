## Why

Doctors currently reach patient context through assigned appointments, while the patient directory is restricted to Receptionists. This makes it difficult for Doctors to find or correct basic patient information independently and leaves no safe way to remove a newly created patient record that has not yet acquired clinic history.

## What Changes

- Add a Doctor workspace navigation rail with `Dashboard` and `Patients` destinations; keep the current appointment and consultation workspace as `Dashboard`.
- Add a Doctor `Patients` page for searching all patients by Patient ID, identity document, name, phone, or email.
- Allow authenticated Doctors to create and edit the existing non-clinical patient fields using the same validation, generated Patient ID, identity uniqueness, and administrative projection rules as Receptionists.
- Allow authenticated Doctors and Receptionists to physically delete a patient only when no appointments, clinical records, prescriptions, payments, receipts, or other patient-referencing data exists.
- Make deletion transactional and guarded by the database's foreign-key protection. Require confirmation for an eligible delete and show an owned modal explaining the related record categories and counts when deletion is blocked.
- Preserve activation and deactivation as separate reversible status operations. Deactivation keeps patient history and prevents new appointment booking; activation restores eligibility for new booking.
- Refresh patient results and related selectors after successful creation, editing, deletion, activation, or deactivation.
- Keep clinical records and prescriptions outside the Patients page; Doctor clinical access remains restricted to the assigned Doctor's appointment workflow.
- Add service, persistence, authorization, integration, and TestFX coverage, and update the User Guide, Developer Guide, and OpenSpec documentation.

## Capabilities

### New Capabilities

- `doctor-patient-directory`: Doctor-facing search and maintenance of the administrative patient directory, including guarded deletion and Dashboard/Patients navigation.

### Modified Capabilities

- `clinic-workflow`: Permit Doctors to create, view, and update non-clinical patient information while preserving the separate assigned-Doctor clinical-record boundary.

## Impact

- `PatientService`, `PatientRepository`, and `Authorization`: extend the administrative patient authorization boundary and add transactional deletion preflight and delete behavior.
- `DoctorView` and shared patient-directory UI code: add the navigation rail, Patients page, patient forms, deletion confirmation, and blocked-deletion modal.
- `ReceptionistView`: expose the same guarded delete behavior and retain activation/deactivation for both staff roles.
- `src/test/java/nusynapxe/`: add relationship-blocking, successful-delete, role-authorization, history-preservation, and Doctor TestFX coverage.
- `docs/` and OpenSpec specifications: document Doctor access, deletion rules, status semantics, and the privacy boundary.
- No schema migration or new runtime dependency is expected; existing patient foreign keys and the `active` column provide the required structural safeguards.
