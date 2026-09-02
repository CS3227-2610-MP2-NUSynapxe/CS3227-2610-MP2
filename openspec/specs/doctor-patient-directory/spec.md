# doctor-patient-directory Specification

## Purpose

Provide Doctors and Receptionists with an authorized, non-clinical patient directory for finding, creating, editing, and safely removing patient records while preserving clinic history and clinical confidentiality.

## Requirements

### Requirement: Authorized staff SHALL access patient administration without clinical data

The system SHALL allow an authenticated Doctor or Receptionist to open the patient directory, search all patient records, and view the permitted administrative fields for a selected patient. The directory SHALL include active and inactive patients. It SHALL not expose clinical records, consultation notes, diagnoses, follow-up notes, or prescriptions. Unauthenticated users and roles without patient-administration permission SHALL be denied.

#### Scenario: A Doctor opens the patient directory

- **WHEN** an authenticated Doctor selects Patients from the Doctor workspace
- **THEN** the system shows the patient directory with search and patient-administration actions for active and inactive patients

#### Scenario: A Receptionist retains patient-directory access

- **WHEN** an authenticated Receptionist opens the patient directory
- **THEN** the Receptionist can use the same administrative patient search and maintenance actions

#### Scenario: A user without patient-administration permission is denied

- **WHEN** an unauthenticated user or a role without patient-administration permission requests the patient directory or its administrative operations
- **THEN** the system rejects the request and does not disclose patient data

#### Scenario: The directory keeps clinical information private

- **WHEN** a Doctor or Receptionist views a patient through the administrative directory
- **THEN** the response and displayed patient details contain only permitted administrative fields and exclude clinical records, consultation notes, diagnoses, follow-up notes, and prescriptions

### Requirement: Authorized staff SHALL create and edit administrative patient information

The system SHALL allow an authenticated Doctor or Receptionist to create a patient and edit the permitted non-clinical patient fields. Creation SHALL apply the existing patient validation rules, enforce identity uniqueness, and generate the Patient ID. Editing SHALL preserve the Patient ID and existing clinical and historical data. Invalid or duplicate submissions SHALL be rejected without partially changing the patient.

#### Scenario: A Doctor creates a patient

- **WHEN** an authenticated Doctor submits valid administrative identity, demographic, contact, and address information for a new patient
- **THEN** the system generates a Patient ID, persists the administrative information, and creates no clinical record

#### Scenario: A Doctor edits a patient

- **WHEN** an authenticated Doctor submits valid changes to a selected patient's permitted administrative fields
- **THEN** the system updates those fields, preserves the Patient ID, and leaves the patient's clinical and historical data unchanged

#### Scenario: An invalid or duplicate patient submission is rejected

- **WHEN** a Doctor or Receptionist submits invalid data or an identity value already belonging to another patient
- **THEN** the system explains the validation or uniqueness error and leaves the existing patient data unchanged

### Requirement: Patient search SHALL support the directory's supported identifiers and contact fields

The system SHALL allow a Doctor or Receptionist to search patients by Patient ID, identity document, name, phone number, or email address. Text searches SHALL be case-insensitive and support partial matches where the existing directory supports them. An empty search SHALL list all patients, including inactive patients, and a search with no matches SHALL show an explicit empty result.

#### Scenario: A Doctor searches by identity or contact information

- **WHEN** a Doctor enters a supported Patient ID, identity document, name, phone number, or email query
- **THEN** the system shows the matching administrative patient records without showing clinical information

#### Scenario: A directory search has no matches

- **WHEN** a Doctor or Receptionist submits a query that matches no patient
- **THEN** the system shows an explicit no-results state and does not display unrelated patients

#### Scenario: An empty query lists the complete directory

- **WHEN** a Doctor or Receptionist clears the search query
- **THEN** the system shows all patients available to the directory, including inactive patients

### Requirement: Physical patient deletion SHALL be allowed only when no related data exists

The system SHALL physically delete a patient only when no stored record refers to that patient. The deletion guard SHALL cover appointments, clinical records, prescriptions or other clinical data, payments, receipts, and any other direct or transitive patient-related data represented by the system. Deletion SHALL be atomic and SHALL never cascade, orphan, or partially remove related data.

#### Scenario: An unused patient can be deleted

- **WHEN** an authenticated Doctor or Receptionist requests deletion of a patient with no related records and confirms the operation
- **THEN** the system permanently removes the patient record and reports success

#### Scenario: A patient with appointments cannot be deleted

- **WHEN** a Doctor or Receptionist requests deletion of a patient with one or more appointments
- **THEN** the system refuses the deletion, keeps the patient and appointments unchanged, and shows a modal explaining that appointments prevent deletion

#### Scenario: A patient with clinical or financial history cannot be deleted

- **WHEN** a Doctor or Receptionist requests deletion of a patient with clinical records, prescriptions, payments, receipts, or another patient-related record
- **THEN** the system refuses the deletion and shows a modal listing each blocking related-data category and its count, with deactivation presented as the safe alternative

#### Scenario: A confirmed deletion does not cascade

- **WHEN** the deletion check and the final deletion occur
- **THEN** the operation either removes only an eligible patient or makes no change, and it never deletes or modifies another patient-related record

### Requirement: Patient activation and deactivation SHALL preserve history and control booking eligibility

The system SHALL provide authenticated Doctors and Receptionists with reversible activation and deactivation actions. Deactivation SHALL mark a patient inactive without deleting the patient, appointments, clinical records, prescriptions, payments, receipts, or other history, and SHALL prevent new appointments from being booked for that patient. Activation SHALL mark the patient active again and restore eligibility for new appointment booking. Existing appointments and historical records SHALL remain available to their authorized workflows.

#### Scenario: A patient is deactivated

- **WHEN** an authenticated Doctor or Receptionist deactivates an active patient
- **THEN** the patient remains searchable with an inactive status, existing history remains unchanged, and new appointment booking for the patient is rejected

#### Scenario: A patient is reactivated

- **WHEN** an authenticated Doctor or Receptionist activates an inactive patient
- **THEN** the patient status changes to active and new appointment booking becomes permitted again, subject to the normal scheduling rules

### Requirement: The Doctor workspace SHALL provide Dashboard and Patients destinations

The Doctor workspace SHALL provide clearly labeled `Dashboard` and `Patients` destinations. `Dashboard` SHALL retain the current Doctor appointment and consultation workspace. `Patients` SHALL open the administrative patient directory and SHALL not mix directory controls with the Doctor's clinical-record workflow.

#### Scenario: A Doctor switches between workspace destinations

- **WHEN** a Doctor selects Dashboard or Patients
- **THEN** the selected destination is visibly identified and its corresponding content and actions are shown without exposing controls from the other destination as if they belonged to it

#### Scenario: A Doctor uses patient administration without changing clinical scope

- **WHEN** a Doctor creates, edits, searches, activates, deactivates, or attempts to delete a patient from Patients
- **THEN** the system performs only the authorized administrative operation and does not grant access to clinical records outside the Doctor's assigned appointment workflow

### Requirement: Patient-directory operations SHALL provide confirmation, error feedback, and refreshed results

The patient directory SHALL require confirmation before permanently deleting an eligible patient. After a successful create, edit, activation, deactivation, or deletion, the directory SHALL refresh its results and dependent patient selectors so that the current patient state is visible. Failed operations SHALL leave the visible patient state consistent with persisted data and SHALL provide an actionable explanation.

#### Scenario: An eligible deletion requires confirmation

- **WHEN** a Doctor or Receptionist starts deletion of an eligible unused patient
- **THEN** the system asks for explicit confirmation before permanently removing the patient

#### Scenario: A successful directory mutation refreshes the view

- **WHEN** a create, edit, activation, deactivation, or deletion succeeds
- **THEN** the directory and affected patient selectors show the persisted result without requiring the user to leave and reopen the workspace
