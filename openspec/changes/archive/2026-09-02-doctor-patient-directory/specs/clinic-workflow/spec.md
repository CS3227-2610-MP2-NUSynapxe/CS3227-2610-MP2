## MODIFIED Requirements

### Requirement: The system SHALL maintain patients with separate administrative and clinical information

The system SHALL maintain a patient identity with non-clinical administrative information separately from medical records, consultation notes, diagnoses, follow-up notes, and prescriptions. An authenticated Receptionist or Doctor SHALL be able to register a patient and create or update permitted administrative information. A Doctor SHALL be able to view and update clinical information for a patient linked to the Doctor's appointment. Administrative patient operations SHALL NOT read or write clinical fields, and a Receptionist SHALL not be granted access to clinical information.

#### Scenario: Receptionist registers a patient

- **WHEN** an authenticated Receptionist submits valid patient identity, demographic, contact, and address information
- **THEN** the patient is persisted with administrative information and no medical note is created

#### Scenario: Receptionist updates administrative information only

- **WHEN** a Receptionist changes a patient's permitted identity, demographic, contact, or address information
- **THEN** the administrative fields are updated while the Patient ID and existing medical notes, diagnoses, follow-up notes, prescriptions, and other history remain unchanged

#### Scenario: A Doctor registers or updates administrative information only

- **WHEN** an authenticated Doctor creates a patient or changes a patient's permitted identity, demographic, contact, or address information
- **THEN** the administrative fields are created or updated while the Patient ID and existing medical notes, diagnoses, follow-up notes, prescriptions, and other history remain unchanged

#### Scenario: Doctor records clinical information for an assigned patient

- **WHEN** an authenticated Doctor submits a diagnosis, consultation note, follow-up note, or prescription for a patient on that Doctor's appointment
- **THEN** the clinical information is persisted and associated with the patient and consultation

#### Scenario: Clinical data is not exposed to a Receptionist

- **WHEN** a Receptionist or Doctor requests a patient record through the administrative directory
- **THEN** the response contains only permitted administrative fields and excludes medical notes, diagnoses, follow-up notes, and prescriptions
