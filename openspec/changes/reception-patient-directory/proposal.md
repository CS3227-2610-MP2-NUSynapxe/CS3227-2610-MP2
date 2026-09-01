## Why

Receptionists need a reliable way to find and maintain basic patient records without creating duplicate identities or gaining access to confidential clinical information. An NRIC-only identifier excludes foreign patients, while the current registration-focused workflow does not provide directory search or complete basic-data maintenance.

## What Changes

- Retain an immutable, database-generated numeric Patient ID as the primary key and display it in a patient-friendly form such as `P000042`.
- Replace the proposed NRIC-only field with identity-document type (`NRIC`, `FIN`, `PASSPORT`, or `OTHER`), identity number, and issuing country so local and foreign patients can be registered.
- Normalize document identity values by trimming whitespace and uppercasing letters, but do not impose country-specific format, length, or checksum validation.
- Completely reject a duplicate normalized combination of identity type, issuing country, and identity number, and show a clear duplicate-identity error rather than a warning.
- Accept international phone numbers containing an optional leading `+` followed by digits, without Singapore-specific length validation.
- Add Receptionist patient search by generated Patient ID, identity document, name, phone, and email.
- Add separate Receptionist tabs for new-patient registration and patient search/editing, with deactivation located alongside search results.
- Use an ISO country dropdown with Singapore first, automatically select Singapore for NRIC and FIN, and restrict sex to Male or Female.
- Add a Receptionist basic patient-data view and editing workflow for identity, name, date of birth, sex, contact details, address, height, and weight; remove patient billing information while retaining appointment payment/checkout records.
- Enforce Receptionist authorization and the clinical-confidentiality boundary in the service and persistence layers, not only in the JavaFX interface.
- Add repository, service, authorization, and TestFX coverage for search, editing, duplicate rejection, validation, and clinical-data isolation.
- Update the User Guide and Developer Guide to describe the patient-directory workflow, generated Patient IDs, flexible identity documents, phone handling, privacy boundary, schema change, and verification approach.
- Keep Doctor-facing basic-data CRUD/search, appointment clinical records, JSON import/export, scheduling, and billing workflows outside this change so they can be delivered in their own feature branches.

## Capabilities

### New Capabilities

- `reception-patient-directory`: Receptionist-only lookup and maintenance of basic patient data using a generated Patient ID and flexible unique identity documents, with strict exclusion of clinical information.

### Modified Capabilities

None. There are no archived main OpenSpec capabilities in this repository to modify.

## Impact

- `src/main/java/nusynapxe/domain/Patient.java`: represent generated Patient ID, flexible identity-document details, binary sex, height, and weight in the basic patient model without patient billing information.
- `src/main/java/nusynapxe/persistence/SchemaInitializer.java` and `PatientRepository.java`: migrate and enforce composite identity-document uniqueness and provide basic-data search/update operations.
- `src/main/java/nusynapxe/service/PatientService.java`: normalize document identity, validate international phone syntax and measurements, reject duplicate identities, authorize directory operations, and preserve the confidentiality boundary.
- `src/main/java/nusynapxe/ui/ReceptionistView.java`: provide directory search, patient selection, detail viewing, editing, and user-facing errors.
- `src/test/java/nusynapxe/`: expand persistence, service, authorization, integration, and TestFX coverage.
- `docs/UserGuide.md` and `docs/DeveloperGuide.md`: document behavior, identity flexibility, privacy constraints, schema evolution, and tests.
- Existing databases require versioned migrations for the identity fields and later removal of patient billing information and unsupported sex values; the migration and rollout policy is resolved in the design.
