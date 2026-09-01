## Purpose

Provide Receptionists with secure basic patient-data lookup and maintenance for local and foreign patients while preventing duplicate documented identities and protecting all clinical information.

## ADDED Requirements

### Requirement: Every patient has a generated Patient ID and documented identity

The system SHALL assign every new patient an immutable database-generated numeric Patient ID used by all internal relationships. Registration SHALL require an identity type of `NRIC`, `FIN`, `PASSPORT`, or `OTHER`, a non-blank identity number, and an issuing country. The system SHALL trim and uppercase identity values. NRIC SHALL use `S` or `T`, seven digits, and one letter; FIN SHALL use `F`, `G`, or `M`, seven digits, and one letter; and both SHALL require issuing country `SG`. Passport numbers SHALL contain 5 to 20 ASCII letters or digits; OTHER SHALL remain non-blank. The system SHALL reject a normalized identity type, issuing country, and identity number combination already assigned to another patient.

#### Scenario: Register a local patient with NRIC

- **WHEN** an authenticated Receptionist submits otherwise valid patient information with identity type `NRIC`, issuing country `SG`, and an unused non-blank identity number
- **THEN** the system creates one patient with the normalized identity information
- **AND** the database generates a separate immutable numeric Patient ID
- **AND** the interface displays that ID in a patient-friendly form such as `P000042`

#### Scenario: Register a foreign patient with a passport

- **WHEN** an authenticated Receptionist submits otherwise valid patient information with identity type `PASSPORT`, an issuing country, and an unused non-blank identity number
- **THEN** the system registers the patient when the normalized passport contains 5 to 20 letters or digits

#### Scenario: Register a patient with another identity document

- **WHEN** an authenticated Receptionist selects identity type `FIN` or `OTHER` and supplies an unused non-blank identity number
- **THEN** the system applies Singapore FIN syntax and issuing-country rules to FIN and a non-blank rule to OTHER

#### Scenario: Reject a non-Singapore issuing country for NRIC or FIN

- **WHEN** registration or editing submits identity type `NRIC` or `FIN` with an issuing country other than `SG`
- **THEN** the system rejects the complete operation with `Issuing country must be Singapore for NRIC and FIN`
- **AND** no patient data is created or changed

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

#### Scenario: Reject an invalid document format

- **WHEN** an authenticated Receptionist supplies an NRIC, FIN, or passport that does not match its applicable syntax
- **THEN** the system rejects the registration with a field-specific identity-number error

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

The system SHALL allow an authenticated Receptionist to register, select, view, and edit a patient's identity type, identity number, issuing country, name, date of birth, sex, phone country code, phone number, email, address, height, and weight. All fields except height and weight SHALL be required and marked with `*`. Sex SHALL be either `FEMALE` or `MALE`. Patient billing information SHALL not be collected or stored; appointment payment and checkout records remain separate. An update SHALL either persist all validated changes or leave the existing patient unchanged. Patient removal SHALL deactivate rather than physically delete a patient with retained history.

#### Scenario: View an administrative patient record

- **WHEN** an authenticated Receptionist selects a patient from the directory
- **THEN** the system displays that patient's generated Patient ID and permitted identity, demographic, measurement, contact, and address information

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

#### Scenario: Select date of birth and calculate age

- **WHEN** a Receptionist selects a date of birth using the calendar control
- **THEN** the system displays the patient's age calculated against the current date in the `Asia/Singapore` time zone
- **AND** age is read-only and is not persisted as a second source of truth
- **AND** the age control has no placeholder text when no date is selected

#### Scenario: Navigate date of birth by month and year

- **WHEN** a Receptionist chooses a month or year from the date-of-birth dropdowns
- **THEN** the calendar moves directly to that month and year without repeated previous/next navigation
- **AND** the selected day is preserved when valid or clamped to the last valid day of the selected month

#### Scenario: Save optional measurements

- **WHEN** an authenticated Receptionist supplies a positive whole-number height in centimetres or a positive weight with at most one decimal place, or leaves either optional measurement blank
- **THEN** the system saves the patient using the supplied measurement values or null for omitted values

#### Scenario: Reject invalid measurements

- **WHEN** an authenticated Receptionist submits a zero, negative, non-numeric, fractional height, or weight with more than one decimal place
- **THEN** the system rejects the update without changing the patient

#### Scenario: Populate and edit a phone country code

- **WHEN** a Receptionist selects an issuing country
- **THEN** the phone country-code field is populated with digits from international calling-code metadata, such as `65` for Singapore
- **AND** the Receptionist may edit the populated country code
- **AND** a fixed non-editable `+` is displayed immediately before the country-code control

#### Scenario: Reject unsupported phone characters

- **WHEN** an authenticated Receptionist submits a blank country code, a country code containing a plus sign or non-digit, a country code longer than 3 digits, or a blank/non-digits phone number
- **THEN** the system rejects the registration or update with a phone validation error

#### Scenario: Reject missing required data or invalid email

- **WHEN** any required patient field is missing or the email lacks a non-empty local part, `@`, and non-empty domain part
- **THEN** the system rejects the registration or update with a field-specific validation error

#### Scenario: Deactivate a patient

- **WHEN** an authenticated Receptionist removes a patient who has retained clinic history
- **THEN** the system marks the patient inactive instead of physically deleting the patient or related records

#### Scenario: Reactivate an inactive patient

- **WHEN** an authenticated Receptionist activates an inactive patient from the patient-details window
- **THEN** the system marks the patient active again without changing the Patient ID or retained history
- **AND** the status action changes back to `Deactivate patient`

#### Scenario: Restrict sex choices

- **WHEN** a Receptionist registers or edits a patient
- **THEN** the interface offers only Male and Female
- **AND** Male is the first option
- **AND** the service rejects any other or missing sex value

### Requirement: Registration and patient management use separate tabs

The Receptionist workspace SHALL place new-patient registration in a `Register new patient` tab and only search controls and patient results in a separate `Search and manage patients` tab. Selecting a patient result SHALL open a separate patient-details window containing permitted administrative details, editing, and activate/deactivate actions. The registration and details-window forms SHALL use independent controls and state.

#### Scenario: Open the registration tab

- **WHEN** a Receptionist opens `Register new patient`
- **THEN** the system displays a blank registration form and register action without Patient ID, search results, save-changes, or deactivation controls

#### Scenario: Open the search and manage tab

- **WHEN** a Receptionist opens `Search and manage patients`
- **THEN** the system displays search controls and patient results without the patient edit form or status actions
- **AND** no new-patient register action appears in that tab

#### Scenario: Select a search result

- **WHEN** a Receptionist selects a patient in the search results
- **THEN** a separate patient-details window opens with the permitted administrative fields, generated Patient ID, save action, and an active-status action
- **AND** the active-status action reads `Deactivate patient` for an active patient and `Activate patient` for an inactive patient

### Requirement: Issuing country uses a complete constrained selector

The Receptionist registration and edit forms SHALL use an issuing-country dropdown containing the Java runtime's complete ISO 3166 country list, displaying English country names and storing normalized two-letter country codes. Singapore SHALL appear first and all remaining countries SHALL follow alphabetically.

#### Scenario: Select NRIC or FIN

- **WHEN** a Receptionist selects identity type `NRIC` or `FIN`
- **THEN** the issuing-country selector automatically selects Singapore and cannot be changed while that identity type remains selected
- **AND** service-layer validation rejects a non-Singapore issuing country even if the interface is bypassed

#### Scenario: Select a foreign country

- **WHEN** a Receptionist selects `PASSPORT` or `OTHER`
- **THEN** the Receptionist can select any country in the country dropdown
- **AND** the selected normalized country code is used for identity uniqueness

### Requirement: Receptionist features use separate automatically refreshed tabs

The workspace SHALL provide separate top-level tabs for `Patient directory and basic data`, `Appointments across all Doctors`, `Checkout`, and `Daily revenue`. The Log out button SHALL be positioned at the top right. The workspace SHALL not expose a manual Refresh button and SHALL refresh affected data after successful writes, searches, and relevant feature-tab selection.

#### Scenario: Navigate between Receptionist features

- **WHEN** a Receptionist selects a top-level feature tab
- **THEN** only that feature's controls are presented as the primary page content
- **AND** the feature reloads data that may have changed since it was last viewed

#### Scenario: Complete a write or search

- **WHEN** a Receptionist registers, edits, deactivates, searches, books, reschedules, cancels, checks in, or checks out
- **THEN** the affected list and selection refresh automatically without a Refresh button

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
- **THEN** the returned data contains only permitted identity, contact, and address information
- **AND** no clinical information is queried or returned

#### Scenario: Receptionist updates administrative information

- **WHEN** an authenticated Receptionist updates a patient's permitted administrative information
- **THEN** all clinical records and prescriptions associated with the patient remain unchanged

#### Scenario: Duplicate error protects the submitted document

- **WHEN** the system rejects a duplicate identity-document registration or update
- **THEN** the error identifies the duplicate condition without echoing the full submitted identity number into logs or diagnostic output

### Requirement: Existing patient data remains usable after schema migration

The system SHALL migrate an existing supported database without deleting patients, appointments, payments, or clinical information. Patient billing information SHALL be removed during schema-version-3 migration without altering payment history. A legacy patient without document details SHALL remain searchable by generated Patient ID and existing basic fields, but SHALL require a complete unique identity type, identity number, and issuing country when the Receptionist next saves that patient's basic record. Newly introduced sex, height, and weight values SHALL remain unset until supplied rather than being fabricated during migration; unsupported legacy sex values SHALL also become unset.

#### Scenario: Open a database containing legacy patients

- **WHEN** the application opens a supported database created before flexible identity-document storage existed
- **THEN** the migration completes without inventing identity, sex, height, or weight values or deleting existing records
- **AND** legacy patients remain visible in the Receptionist directory

#### Scenario: Update a legacy patient

- **WHEN** an authenticated Receptionist saves basic-data changes for a migrated patient that has no identity-document details
- **THEN** the system requires a complete unique document identity before committing the update
