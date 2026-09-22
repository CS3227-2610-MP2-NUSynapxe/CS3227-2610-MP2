## MODIFIED Requirements

### Requirement: The Doctor workspace SHALL provide Dashboard and Patients destinations

The Doctor workspace SHALL provide clearly labeled `Dashboard` and `Patients`
destinations. `Dashboard` SHALL retain the current Doctor appointment and
consultation workspace. `Patients` SHALL open the administrative patient
directory and a separate Doctor-only clinical-history workflow. The clinical
history workflow SHALL not mix administrative directory controls with clinical
record editing controls. Clinical history shown there SHALL be read-only for
completed or checked-out consultations, while clinical editing SHALL remain in
the assigned Doctor's consultation workflow.

#### Scenario: A Doctor switches between workspace destinations

- **WHEN** a Doctor selects Dashboard or Patients
- **THEN** the selected destination is visibly identified and its
  corresponding content and actions are shown without exposing controls from
  the other destination as if they belonged to it

#### Scenario: A Doctor uses patient administration without changing clinical scope

- **WHEN** a Doctor creates, edits, searches, activates, deactivates, or
  attempts to delete a patient from the administrative directory within
  Patients
- **THEN** the system performs only the authorized administrative operation
  and does not grant clinical editing access through that operation

#### Scenario: A Doctor views a patient's clinical history

- **WHEN** a Doctor selects a patient and opens the clinical-history workflow
  from Patients
- **THEN** the system lists that patient's completed or checked-out
  consultations and allows the Doctor to open their clinical records and
  prescriptions in read-only form

#### Scenario: Dashboard links to patient clinical history

- **WHEN** a Doctor selects an appointment from Dashboard and chooses the
  patient-history action
- **THEN** the system opens the same clinical-history workflow with that
  patient selected without granting clinical editing controls for records
  owned by another Doctor
