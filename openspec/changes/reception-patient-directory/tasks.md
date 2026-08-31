## 1. Patient identity and schema migration

- [ ] 1.1 Extend the basic `Patient` domain model with generated Patient ID display, nullable legacy identity type/number/country and sex, optional height/weight, and active status; update affected fixtures and compilation call sites, and verify production and test sources compile.
- [ ] 1.2 Replace schema-version marker initialization with ordered transactional migration handling and add schema version 2 with nullable document identity, sex, height, weight, and active columns plus complete non-null composite uniqueness enforcement; verify fresh version-2 initialization and idempotent reopen tests.
- [ ] 1.3 Add version-1 database fixture tests proving migration preserves Patient IDs, patients, appointments, payments, clinical records, and foreign keys without inventing identity, sex, height, or weight values; verify an injected migration failure rolls back without advancing the stored version.

## 2. Patient repository directory operations

- [ ] 2.1 Extend explicit patient administrative projections and insert/update mappings with document identity, sex, height, weight, and active status while keeping all clinical columns absent; verify local, foreign, and legacy repository round trips plus projection-boundary tests.
- [ ] 2.2 Add parameterized patient-directory search for exact numeric/formatted Patient ID and case-insensitive partial identity type/number/country, name, phone, and email, including escaped wildcard input, blank-query listing, and deterministic name/ID order; verify matching and no-match repository tests.
- [ ] 2.3 Enforce normalized uniqueness of complete identity type/country/number tuples for new rows and atomic basic-data updates, map concurrent uniqueness failures predictably, and verify duplicate insert/update operations leave persistence unchanged.
- [ ] 2.4 Add patient deactivation that preserves the generated Patient ID and every related appointment, payment, and clinical record; verify deactivated-patient persistence and referential-integrity tests.

## 3. Receptionist service rules and confidentiality

- [ ] 3.1 Add focused document identity normalization for required trimmed uppercase type/number/country values without country-specific syntax, length, or checksum validation; verify NRIC, FIN, passport, other, unfamiliar-format, blank, case-variant, and whitespace-variant inputs.
- [ ] 3.2 Add international phone validation using an optional leading `+` followed by one or more digits with no fixed length, plus optional positive decimal height/weight validation; verify accepted international values and rejected blank, misplaced-plus, unsupported-character, zero, negative, and non-numeric inputs.
- [ ] 3.3 Extend Receptionist registration and basic-data editing with generated Patient ID, flexible identity documents, sex, measurements, hard duplicate rejection, deactivation, and non-sensitive errors; verify duplicate, invalid, missing-patient, legacy-patient, deactivation, and successful update service tests.
- [ ] 3.4 Add Receptionist-authorized directory search and detail retrieval while rejecting Doctor and missing-session access in this role-specific change; verify authorization tests demonstrate no data is returned or changed on denial.
- [ ] 3.5 Add integration coverage proving Receptionist search results contain only basic non-clinical fields and that registration, editing, or deactivation never reads, replaces, or deletes diagnoses, consultation notes, follow-up notes, or prescriptions.

## 4. Receptionist patient-directory interface

- [ ] 4.1 Add stable semantic JavaFX controls for read-only formatted Patient ID, identity type/number/country, sex, height/weight, directory search/clear, basic-data results, and distinct register, save-update, and deactivate actions; verify the workspace exposes the documented TestFX IDs.
- [ ] 4.2 Populate permitted basic fields when a result is selected, refresh and retain the affected selection after successful writes, and show an empty result set without an error; verify local and foreign registration plus representative search, selection, edit, deactivation, and refresh TestFX flows.
- [ ] 4.3 Present field-specific identity, phone, and measurement validation plus the hard duplicate message `A patient with this identity document already exists` without echoing the complete document number; verify failed registration and update leave both displayed and persisted patient data unchanged.

## 5. Documentation and development evidence

- [ ] 5.1 Update `docs/UserGuide.md` with generated Patient IDs, local/foreign identity documents, international phone syntax, basic-data fields, patient search, registration, selection, editing, deactivation, duplicate errors, legacy completion, and the clinical-confidentiality boundary; verify every label and interaction matches the JavaFX workspace.
- [ ] 5.2 Update `docs/DeveloperGuide.md` with the generated-ID/composite-document distinction, identity privacy handling, phone/measurement rules, schema-version-2 migration, administrative projection, search behavior, authorization boundary, and relevant test commands.
- [ ] 5.3 Create and verify the assignment interaction summary for this feature under `logs/`, recording prompts, OpenSpec/agent skills used, human corrections, accepted and rejected suggestions, tests, and final verification results without including real identity documents or patient data.

## 6. Integrated verification and quality gates

- [ ] 6.1 Run focused domain, migration, repository, service, authorization, integration, and Receptionist TestFX tests; verify every scenario in `specs/reception-patient-directory/spec.md` is covered by an automated test or an explicitly documented manual check.
- [ ] 6.2 Run `gradlew.bat spotlessApply` followed by `gradlew.bat check javadoc --no-daemon --console=plain`; inspect generated Checkstyle, PMD, SpotBugs, TestFX, and JaCoCo results and resolve all failures attributable to this change.
- [ ] 6.3 Run `openspec validate reception-patient-directory --type change --strict`, inspect the feature diff for accidental clinical-data exposure or unrelated files, and verify the implementation and documentation match all approved artifacts before requesting review.
