## Purpose

Provide Receptionists with secure basic patient-data lookup and maintenance for local and foreign patients while preventing duplicate documented identities and protecting all clinical information.

## ADDED Requirements

### Requirement: Every patient has a generated Patient ID and documented identity

The system SHALL assign every new patient an immutable database-generated numeric Patient ID used by all internal relationships. Registration SHALL require an identity type of `NRIC`, `FIN`, `PASSPORT`, or `OTHER`, a non-blank identity number, and an issuing country. The system SHALL trim and uppercase identity values without applying country-specific format, length, or checksum validation, and SHALL reject a normalized identity type, issuing country, and identity number combination already assigned to another patient.

#### Scenario: Register a local patient with NRIC

- **WHEN** an authenticated Receptionist submits otherwise valid patient information with identity type `NRIC`, issuing country `SG`, and an unused non-blank identity number
- **THEN** the system creates one patient with the normalized identity information
- **AND** the database generates a separate immutable numeric Patient ID
- **AND** the interface displays that ID in a patient-friendly form such as `P000042`

#### Scenario: Register a foreign patient with a passport

- **WHEN** an authenticated Receptionist submits otherwise valid patient information with identity type `PASSPORT`, an issuing country, and an unused non-blank identity number
- **THEN** the system registers the patient without applying an NRIC-specific format or checksum rule

#### Scenario: Register a patient with another identity document

- **WHEN** an authenticated Receptionist selects identity type `FIN` or `OTHER` and supplies an issuing country and unused non-blank identity number
- **THEN** the system registers the patient without applying a country-specific document format or length rule

#### Scenario: Reject a duplicate identity document during registration

- **WHEN** an authenticated Receptionist submits an identity type, issuing country, and identity number whose normalized combination is already assigned to a patient
- **THEN** the system rejects the registration completely
- **AND** the system displays `A patient with this identity document already exists`
- **AND** no additional patient record is created

#### Scenario: Reject a case or whitespace duplicate

- **WHEN** an authenticated Receptionist submits an existing document combination with different letter casing or surrounding whitespace
- **THEN** the system treats it as the same normalized identity and rejects the registration

#### Scenario: Reject missing identity information

- **WHEN** an authenticated Receptionist omits identity type, identity number, or issuing country
- **THEN** the system rejects registration with a field-specific validation error
- **AND** no patient record is created

#### Scenario: Accept an unfamiliar document format

- **WHEN** an authenticated Receptionist supplies non-blank identity information whose document format is not recognized by the application
- **THEN** the system accepts the identity subject to normalized uniqueness and all other patient validation rules

#### Scenario: Concurrent duplicate registrations

- **WHEN** two registration attempts concurrently submit the same normalized identity-document combination
- **THEN** at most one patient record is committed
- **AND** every losing attempt receives the duplicate-identity error

### Requirement: Receptionists can search the patient directory

The system SHALL allow an authenticated Receptionist to search basic patient records by displayed Patient ID, identity type, identity number, issuing country, patient name, phone, or email. Text matching SHALL be case-insensitive, and surrounding whitespace in the search query SHALL be ignored.

#### Scenario: Search by Patient ID

- **WHEN** an authenticated Receptionist searches using an existing numeric Patient ID or its displayed form
- **THEN** the system returns that patient's administrative directory result

#### Scenario: Search by identity document

- **WHEN** an authenticated Receptionist searches using all or part of a patient's identity number, type, or issuing country with any letter casing
- **THEN** the system returns matching administrative directory results

#### Scenario: Search by patient name

- **WHEN** an authenticated Receptionist searches using all or part of a patient's first or last name
- **THEN** the system returns matching patients case-insensitively

#### Scenario: Search by phone or email

- **WHEN** an authenticated Receptionist searches using all or part of a patient's phone number or email address
- **THEN** the system returns matching administrative directory results

#### Scenario: Search has no matches

- **WHEN** an authenticated Receptionist submits a non-blank search query that matches no patient
- **THEN** the system displays an empty result set without treating it as an application error

#### Scenario: Blank search lists the directory

- **WHEN** an authenticated Receptionist clears the search query
- **THEN** the system lists all administrative patient records in a deterministic order

### Requirement: Receptionists can create, view, and edit basic patient data

The system SHALL allow an authenticated Receptionist to register, select, view, and edit a patient's identity type, identity number, issuing country, name, date of birth, sex, phone, email, address, billing information, height, and weight. An update SHALL either persist all validated changes or leave the existing patient unchanged. Patient removal SHALL deactivate rather than physically delete a patient with retained history.

#### Scenario: View an administrative patient record

- **WHEN** an authenticated Receptionist selects a patient from the directory
- **THEN** the system displays that patient's generated Patient ID and permitted identity, demographic, measurement, contact, address, and billing information

#### Scenario: Save valid administrative changes

- **WHEN** an authenticated Receptionist submits valid changes to a selected patient's administrative information
- **THEN** the system persists the changes and refreshes the directory and detail view

#### Scenario: Reject changing to another patient's identity document

- **WHEN** an authenticated Receptionist changes a patient's document details to a normalized identity combination assigned to another patient
- **THEN** the system rejects the complete update with the duplicate-identity error
- **AND** the selected patient's existing information remains unchanged

#### Scenario: Reject an invalid administrative update

- **WHEN** an authenticated Receptionist submits missing or invalid required administrative information
- **THEN** the system shows a field-appropriate validation error
- **AND** the selected patient's existing information remains unchanged

#### Scenario: Save optional measurements

- **WHEN** an authenticated Receptionist supplies positive height or weight values, or leaves either optional measurement blank
- **THEN** the system saves the patient using the supplied measurement values or null for omitted values

#### Scenario: Reject invalid measurements

- **WHEN** an authenticated Receptionist submits a zero, negative, or non-numeric height or weight value
- **THEN** the system rejects the update without changing the patient

#### Scenario: Save an international phone number

- **WHEN** an authenticated Receptionist submits a phone number containing an optional leading `+` followed by one or more digits
- **THEN** the system accepts the phone number without imposing a country-specific length

#### Scenario: Reject unsupported phone characters

- **WHEN** an authenticated Receptionist submits a blank phone number, a `+` without digits, a `+` outside the first position, or characters other than `+` and digits
- **THEN** the system rejects the registration or update with a phone validation error

#### Scenario: Deactivate a patient

- **WHEN** an authenticated Receptionist removes a patient who has retained clinic history
- **THEN** the system marks the patient inactive instead of physically deleting the patient or related records

### Requirement: Patient-directory access is authorized below the user interface

The system SHALL require an active Receptionist session for patient-directory search, administrative viewing, registration, and editing, regardless of whether controls are visible in the user interface.

#### Scenario: Receptionist accesses the directory

- **WHEN** an authenticated Receptionist requests a patient-directory operation
- **THEN** the system permits the operation subject to its validation rules

#### Scenario: Doctor attempts an administrative directory operation

- **WHEN** an authenticated Doctor requests patient-directory search, registration, or editing
- **THEN** the system rejects the request without returning or changing patient administrative data

#### Scenario: Missing session attempts a directory operation

- **WHEN** a request without an active session attempts a patient-directory operation
- **THEN** the system rejects the request without returning or changing patient data

### Requirement: The patient directory excludes clinical information

Patient-directory queries and results SHALL exclude diagnoses, consultation notes, follow-up notes, prescriptions, and every other clinical field. Patient administrative changes SHALL preserve all existing clinical records unchanged.

#### Scenario: Receptionist searches or views a patient

- **WHEN** an authenticated Receptionist searches for or views a patient
- **THEN** the returned data contains only permitted identity, contact, address, and billing information
- **AND** no clinical information is queried or returned

#### Scenario: Receptionist updates administrative information

- **WHEN** an authenticated Receptionist updates a patient's permitted administrative information
- **THEN** all clinical records and prescriptions associated with the patient remain unchanged

#### Scenario: Duplicate error protects the submitted document

- **WHEN** the system rejects a duplicate identity-document registration or update
- **THEN** the error identifies the duplicate condition without echoing the full submitted identity number into logs or diagnostic output

### Requirement: Existing patient data remains usable after schema migration

The system SHALL migrate an existing supported database without deleting patients, appointments, payments, or clinical information. A legacy patient without document details SHALL remain searchable by generated Patient ID and existing basic fields, but SHALL require a complete unique identity type, identity number, and issuing country when the Receptionist next saves that patient's basic record. Newly introduced sex, height, and weight values SHALL remain unset until supplied rather than being fabricated during migration.

#### Scenario: Open a database containing legacy patients

- **WHEN** the application opens a supported database created before flexible identity-document storage existed
- **THEN** the migration completes without inventing identity, sex, height, or weight values or deleting existing records
- **AND** legacy patients remain visible in the Receptionist directory

#### Scenario: Update a legacy patient

- **WHEN** an authenticated Receptionist saves basic-data changes for a migrated patient that has no identity-document details
- **THEN** the system requires a complete unique document identity before committing the update
