## MODIFIED Requirements

### Requirement: The system SHALL maintain patients with separate administrative and clinical information

The system SHALL maintain a patient identity with non-clinical administrative
information separately from medical records, consultation notes, diagnoses,
follow-up notes, and prescriptions. An authenticated Receptionist or Doctor
SHALL be able to register a patient and create or update permitted
administrative information. Any authenticated Doctor SHALL be able to view
completed or checked-out clinical information for any patient. Only the Doctor
assigned to an appointment SHALL be able to create or update its clinical
information, and in-progress clinical information SHALL remain visible only to
that assigned Doctor. Administrative patient operations SHALL NOT read or write
clinical fields, and a Receptionist SHALL not be granted access to clinical
information.

#### Scenario: Receptionist registers a patient

- **WHEN** an authenticated Receptionist submits valid patient identity,
  demographic, contact, and address information
- **THEN** the patient is persisted with administrative information and no
  medical note is created

#### Scenario: Receptionist updates administrative information only

- **WHEN** a Receptionist changes a patient's permitted identity, demographic,
  contact, or address information
- **THEN** the administrative fields are updated while the Patient ID and
  existing medical notes, diagnoses, follow-up notes, prescriptions, and other
  history remain unchanged

#### Scenario: A Doctor registers or updates administrative information only

- **WHEN** an authenticated Doctor creates a patient or changes a patient's
  permitted identity, demographic, contact, or address information
- **THEN** the administrative fields are created or updated while the Patient
  ID and existing medical notes, diagnoses, follow-up notes, prescriptions,
  and other history remain unchanged

#### Scenario: Doctor records clinical information for an assigned patient

- **WHEN** an authenticated Doctor submits a diagnosis, consultation note,
  follow-up note, or prescription for a patient on that Doctor's appointment
- **THEN** the clinical information is persisted and associated with the
  patient and consultation

#### Scenario: Any Doctor views a completed consultation

- **WHEN** an authenticated Doctor requests a patient's completed or checked-
  out consultation history
- **THEN** the system displays the patient's retained clinical records in
  read-only form, including the associated consultation details and
  prescriptions

#### Scenario: In-progress clinical information remains assigned-Doctor-only

- **WHEN** a Doctor who is not assigned to an in-progress consultation
  requests its clinical record
- **THEN** the service rejects the request without exposing the in-progress
  clinical information

#### Scenario: Clinical data is not exposed to a Receptionist

- **WHEN** a Receptionist or Doctor requests a patient record through the
  administrative directory
- **THEN** the response contains only permitted administrative fields and
  excludes medical notes, diagnoses, follow-up notes, and prescriptions

### Requirement: The system SHALL protect clinical records and prescriptions by doctor ownership

Only the Doctor assigned to a consultation SHALL be able to create or edit its
diagnosis, consultation notes, follow-up notes, and prescriptions. Any
authenticated Doctor SHALL be able to read the clinical record and
prescriptions for a completed or checked-out consultation in read-only mode.
An unassigned Doctor SHALL not be able to read an in-progress clinical record.
A prescription SHALL include enough information to identify the medication,
dosage, frequency, duration, and instructions, and invalid clinical submissions
SHALL be rejected without replacing valid existing data.

#### Scenario: Assigned Doctor adds a prescription

- **WHEN** the assigned Doctor submits a prescription with all required
  medication and usage details
- **THEN** the prescription is persisted for the consultation and is visible
  in the permitted Doctor clinical view

#### Scenario: Any Doctor views a completed clinical record

- **WHEN** a Doctor who is not assigned to a completed or checked-out
  consultation requests its clinical record
- **THEN** the service returns the record and its prescriptions without
  allowing any clinical field or prescription to be changed

#### Scenario: Unassigned Doctor cannot edit clinical data

- **WHEN** a Doctor who is not assigned to an in-progress consultation attempts
  to read or edit its clinical record, or attempts to edit any consultation's
  clinical data
- **THEN** the service rejects the request without exposing or changing the
  record

#### Scenario: Invalid prescription is rejected

- **WHEN** a Doctor submits a prescription missing a required medication or
  usage detail
- **THEN** validation feedback is returned and any previously saved clinical
  information remains unchanged
