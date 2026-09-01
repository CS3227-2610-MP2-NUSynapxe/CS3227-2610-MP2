## Context

See `proposal.md` for motivation and `specs/reception-patient-directory/spec.md` for the behavior contract. The application currently uses an immutable administrative `Patient` record, a `PatientRepository`, a Receptionist-authorized `PatientService`, programmatic JavaFX controls, and SQLite schema version 1. Patient relationships already use an autoincrement numeric ID, while existing databases lack flexible identity-document, sex, height, and weight fields.

The change crosses the domain, persistence, service, UI, tests, and documentation layers. It also introduces personally identifiable information and requires a backward-compatible schema migration.

## Goals / Non-Goals

**Goals:**

- Preserve numeric patient IDs and all existing foreign-key relationships.
- Expose the generated numeric ID as the immutable Patient ID while keeping it as the relational key.
- Support local and foreign identity documents without country-specific format assumptions.
- Make normalized document-identity uniqueness authoritative at the database boundary as well as user-friendly at the service boundary.
- Keep legacy databases readable without fabricating identity or measurement values.
- Keep search and editing within administrative projections that cannot contain clinical fields.
- Provide deterministic, testable search and atomic administrative updates.
- Separate registration from search/editing in the interface and keep patient deactivation in the search workflow.
- Remove patient billing information without changing appointment checkout or payment records.

**Non-Goals:**

- Replacing numeric patient IDs in appointments or other foreign keys with identity-document values.
- Verifying an identity against a government service or validating country-specific document length, syntax, or checksums.
- Encrypting the SQLite database or individual identity-document values at rest.
- Giving Receptionists any access to clinical records or prescriptions.
- Adding patient self-service, deletion, record merging, or bulk import.
- Implementing Doctor-facing patient CRUD/search, clinical encounter records, JSON import/export, appointment scheduling, or billing changes; each belongs to a separate feature branch.

## Decisions

### Use the generated numeric ID as the Patient primary key

`patients.id INTEGER PRIMARY KEY AUTOINCREMENT` remains the primary key and is the immutable Patient ID. The UI formats it for readability, for example numeric value `42` as `P000042`, without storing a second display ID. Appointments, clinical records, prescriptions, and payments continue to reference the numeric key.

Making NRIC, FIN, or passport number the primary key was rejected because foreign patients use different documents, sensitive text would spread into foreign keys, and correcting a document would become an identity rewrite. UUIDs were considered but add no useful benefit for this single local SQLite database and are less convenient for clinic staff to read.

### Store a flexible composite document identity

The patient stores `identity_type`, `identity_number`, and `issuing_country`. Identity type is restricted to `NRIC`, `FIN`, `PASSPORT`, or `OTHER`; document number and country are required non-blank values. The service trims and applies locale-independent uppercase before search or persistence but deliberately applies no country-specific format, minimum/maximum length, or checksum rule.

SQLite enforces uniqueness over the normalized `(identity_type, issuing_country, identity_number)` tuple. This allows identical passport numbers from different issuing countries and prevents case/whitespace variants from creating duplicates. The service performs a pre-check for user-friendly feedback, while the unique constraint closes concurrency races. Any uniqueness violation becomes `A patient with this identity document already exists`; errors and diagnostic output must not echo the complete number.

Relying only on a service pre-check was rejected because two concurrent registrations could both pass it. Relying only on the database exception was rejected because it produces poor user feedback and couples UI behavior to vendor-specific error text.

### Migrate legacy databases through nullable transition fields

Schema version 2 adds nullable `identity_type`, `identity_number`, `issuing_country`, `sex`, `height_cm`, and `weight_kg` fields plus a unique index over complete non-null document tuples. Existing patients remain intact and searchable by Patient ID and existing basic data without invented values. New registrations require complete document identity and sex through the service. Saving a legacy patient requires the Receptionist to complete required identity fields; optional height and weight may remain null.

The initializer must read the stored schema version and apply ordered migrations transactionally instead of only inserting a version marker. A failed migration rolls back and leaves the prior database usable.

Generating placeholder documents such as `LEGACY-<id>` was rejected because they could be mistaken for genuine identifiers. Blocking startup until every existing row is manually repaired was rejected because it would make migration operationally unsafe.

### Use an administrative projection for every directory query

The patient model and repository projection contain only Patient ID, document identity, demographics, measurements, contact, and address fields. Search SQL explicitly selects those columns and never joins clinical tables. Search uses parameterized statements, escaped wildcard input, case-insensitive matching for text fields, exact matching for a numeric ID, recognition of a displayed `P`-prefixed ID, and deterministic ordering by name followed by ID.

A single trimmed query is matched against identity type, number, issuing country, first name, last name, phone, and email; a numeric or valid displayed-ID query also checks exact Patient ID. A blank query returns the full administrative directory. This keeps the initial interface simple while satisfying all required lookup paths.

Returning a broad database row and removing clinical fields later was rejected because confidential values would already have crossed the persistence boundary.

### Make administrative updates atomic and selection-based

The service accepts the selected Patient ID plus a complete validated basic patient value. The repository updates only permitted columns in one statement and reports a missing patient separately. Identity normalization, international phone validation, and measurement validation occur before persistence. A duplicate or invalid update changes no columns.

The generated Patient ID identifies the record being edited even when its identity-document details change. This avoids ambiguous update targeting and keeps foreign keys stable.

### Validate phone and measurements without local-only assumptions

Phone input follows `^\+?[0-9]+$`: an optional `+` may appear only first and at least one digit is required, but no fixed minimum or maximum length is imposed. Height and weight are optional positive decimal values stored in centimetres and kilograms. Zero, negative, and non-numeric measurements are rejected. This provides basic data quality without assuming Singapore phone lengths or making clinical plausibility claims.

Allowing `+` anywhere was rejected because it does not represent a valid international prefix. Accepting only eight digits was rejected because it excludes foreign telephone numbers.

### Deactivate rather than hard-delete patients

Receptionist “delete” behavior sets an active flag rather than physically removing the patient. Patient IDs are never reused, and appointments, billing, and clinical history remain referentially intact. Physical deletion was rejected because it would destroy audit and medical history and conflict with existing foreign keys.

### Use constrained country and sex choices

Issuing country is selected from the Java runtime's ISO 3166 country list. The interface displays English country names, stores normalized two-letter codes, and orders Singapore first followed by all other countries alphabetically. Selecting NRIC or FIN automatically selects Singapore; Passport and Other allow any listed country. This avoids free-text country variants without adding a runtime dependency.

Sex is limited to `FEMALE` or `MALE` in the domain, service, and interface. When a version-2 database contains `OTHER` or `UNDISCLOSED`, the version-3 migration clears that value so staff must choose one of the supported options on the next save.

### Remove patient billing information in schema version 3

Patient-level billing information is removed from the domain, repository, service, interface, and fresh schema. The version-3 migration drops `patients.billing_information`; it does not change the separate `payments` table, checkout workflow, or revenue reporting. A version-1 database applies version 2 and then version 3 in order within the same transaction.

### Split the Receptionist patient workflow into tabs

The Receptionist view uses a `Register new patient` tab with its own blank form and register action, and a `Search and manage patients` tab with search controls, results, selected-patient edit form, and deactivation action. Each tab has distinct stable semantic IDs for TestFX. Clearing search shows the directory; selecting a result populates only the edit form. Successful edits and deactivation refresh the result list while retaining the affected selection when possible. Registration stays independent of the search/edit form.

Duplicate feedback states that a patient with the identity document already exists but does not display its full number. UI visibility remains only a convenience; services enforce authorization independently.

### Test each enforcement boundary

Persistence tests cover migration through version 3, billing-column removal, composite normalized uniqueness, concurrent duplicate resistance, parameterized search, deterministic order, atomic updates, deactivation, and administrative projections. Service tests cover document normalization without format validation, international phone syntax, measurements, binary sex, authorization, duplicate error mapping, legacy identity completion, and clinical preservation. TestFX covers separate registration/search tabs, all-country selection, Singapore autofill for NRIC/FIN, local and foreign registration, search, selection, editing, duplicate feedback, deactivation placement, refresh behavior, and stable control IDs. Integration tests confirm that basic-data edits do not change clinical records or payment history.

No new runtime dependency is required.

## Risks / Trade-offs

- [Risk] Identity-document numbers are sensitive personal data stored in an unencrypted local database. -> Mitigation: restrict them to authorized administrative projections, avoid logging or echoing them in errors, document OS-level storage protection, and keep database files excluded from Git.
- [Risk] Deliberately permissive document validation can accept a mistyped or meaningless identifier. -> Mitigation: require complete non-blank document fields and normalized uniqueness, clearly document that staff must verify the source document, and keep authoritative government verification outside this change.
- [Risk] Partial document search exposes identifiers to authorized Receptionists and may be slow on a large table. -> Mitigation: preserve the Receptionist-only boundary, return administrative data only, and keep an index for exact uniqueness; revisit masking or specialized indexing with measured requirements.
- [Risk] A migration defect could prevent an existing database from opening. -> Mitigation: run migrations in a transaction, advance the version only after success, test version-1 fixtures, and document backup/rollback procedures.
- [Risk] Nullable document and demographic values temporarily weaken database-wide required-field rules for migrated rows. -> Mitigation: allow null only for legacy compatibility and require complete unique document identity on the next basic-data save; all new registrations require it immediately.

## Migration Plan

1. Back up or copy representative schema-version-1 databases for migration tests.
2. In one transaction, apply version 2 by adding nullable document identity, sex, height, weight, and active columns plus the unique document index, then apply version 3 by clearing unsupported sex values and dropping patient billing information.
3. Preserve every existing patient and related foreign-key row unchanged; do not synthesize identifiers.
4. Require a complete normalized unique document identity and other required basic fields for every new registration and whenever a legacy patient is saved.
5. Verify fresh-database initialization, version-1 and version-2 migration, repeated startup, and rollback after an injected migration failure.
6. Roll back a failed deployment by restoring the pre-migration database backup and the prior application release; version-3 databases are not expected to be opened by an older release.
