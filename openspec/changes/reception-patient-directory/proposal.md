## Why

Receptionists need a reliable way to find and maintain basic patient records without creating duplicate identities or gaining access to confidential clinical information. An NRIC-only identifier excludes foreign patients, while the current registration-focused workflow does not provide directory search or complete basic-data maintenance.

## What Changes

- Retain an immutable, database-generated numeric Patient ID as the primary key and display it in a patient-friendly form such as `P000042`.
- Replace the proposed NRIC-only field with identity-document type (`NRIC`, `FIN`, `PASSPORT`, or `OTHER`), identity number, and issuing country so local and foreign patients can be registered.
- Normalize document identity values by trimming whitespace and uppercasing letters. Validate NRIC and FIN using Singapore syntax rules and require Singapore as their issuing country. Validate passports using a deliberately broad international letters-and-digits rule because no single worldwide passport format exists.
- Completely reject a duplicate normalized combination of identity type, issuing country, and identity number, and show a clear duplicate-identity error rather than a warning.
- Store the digits-only international calling code separately from the digits-only subscriber number. Automatically suggest the calling code for the selected issuing country while allowing staff to edit it.
- Add Receptionist patient search by generated Patient ID, identity document, name, phone, and email.
- Add separate Receptionist tabs for new-patient registration and patient search/editing, with deactivation located alongside search results.
- Organize patient data, appointments, checkout, and daily revenue into separate top-level feature tabs; remove manual refresh and refresh relevant data automatically after actions and when a feature tab is opened.
- Use an ISO country dropdown with Singapore first, lock the issuing country to Singapore for NRIC and FIN, and restrict sex to Male or Female.
- Add a Receptionist basic patient-data view and editing workflow for identity, name, date of birth, sex, contact details, address, height, and weight; remove patient billing information while retaining appointment payment/checkout records.
- Use a calendar date selector, calculate a read-only age using the current date in Singapore, hide generated Patient ID from registration, and mark every required field except optional height and weight with `*`.
- Validate required fields, email syntax, digits-only phone numbers, positive whole-centimetre height, and positive weight with at most one decimal place using field-specific errors.
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
