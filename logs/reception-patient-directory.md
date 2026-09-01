# Receptionist patient directory interaction summary

## Scope

- Change: `reception-patient-directory`
- Working branch: `feat/reception-patient-directory`
- Goal: add Receptionist patient registration, directory search, permitted
  basic-data maintenance, duplicate blocking, and deactivation while preserving
  the clinical confidentiality boundary.

## Prompts and workflow skills

The feature began with a request to identify meaningful Receptionist work for
a clinic appointment and records system. The OpenSpec explore and proposal
workflows were used to clarify the role boundary and produce the proposal,
design, delta specification, and task list. The OpenSpec apply-change workflow
was then used to implement and verify those approved artifacts.

The user rebased the feature branch onto `origin/feat/account-management`
before implementation continued. OpenSpec workflow names beginning with `$`
are chat skills, not commands to enter in Windows Command Prompt or PowerShell.

## Human corrections and decisions

The initial NRIC-centred idea was corrected after considering foreign
patients. The accepted design uses an immutable database-generated numeric
Patient ID as the primary key and a separate identity tuple made from document
type, issuing country, and document number. Supported types are NRIC, FIN,
passport, and other. Complete normalized tuples are unique and duplicates are
hard failures.

The user and project partner also clarified that document values should not
have country-specific validation. Phone numbers accept only an optional
leading plus followed by digits, without a Singapore-specific minimum or
maximum length. Receptionists and Doctors may share basic patient data, but
Receptionists must not receive clinical notes or prescriptions.

After the initial implementation, the user requested separate registration
and search/manage tabs, removal of patient billing information, automatic
Singapore selection for NRIC and FIN, a complete country dropdown with
Singapore first, Female/Male-only sex choices, and deactivation beside patient
search. The accepted revision uses Java's ISO country dataset, stores country
codes, advances the schema to version 3, and leaves appointment payment and
checkout records intact.

Accepted suggestions included a formatted display ID such as `P000042`, a
transactional schema-version-2 migration, explicit administrative repository
projections, soft deactivation, positive optional height and weight values,
literal wildcard handling in search, and a fixed non-sensitive duplicate
message.

Rejected alternatives included using NRIC as the primary key, requiring every
patient to have an NRIC, inventing identifiers for migrated records, applying
country-specific document checks, hard-deleting patients, and exposing
clinical data through the Receptionist directory.

## Tests and verification

Focused automated coverage was added for fresh schema initialization,
version-1 migration and rollback, repository round trips and search,
uniqueness and concurrent creation, deactivation and referential integrity,
service validation and authorization, workflow-level clinical preservation,
and Receptionist TestFX interactions.

Focused persistence, migration, repository, service, authorization,
integration, and Receptionist TestFX suites passed. The revised final Gradle run
completed 54 tests and passed Spotless, Checkstyle, PMD, SpotBugs with
FindSecBugs, JaCoCo report generation, and Javadoc. Existing missing-tag
Javadoc warnings remain non-failing. Test-only text entry was moved onto the
JavaFX application thread to avoid intermittent Windows TestFX robot input
loss; production behavior was not changed by that stabilization.

Revision-focused tests cover version-1 and version-2 migration to version 3,
migration rollback, removal of the patient billing column, clearing unsupported
legacy sex values, country ordering and NRIC/FIN autofill, two sex choices,
tab state separation, registration, search, edit, appointment selection, and
search-tab deactivation.

`openspec validate reception-patient-directory --type change --strict`
passed again after the revision. The final diff contained only feature implementation, documentation,
tests, the OpenSpec task checklist, this summary, and narrow TestFX input
stabilization. A production-source scan confirmed that the Patient domain,
repository, service, and Receptionist view contain no diagnosis, consultation
note, follow-up note, or prescription fields.

No real identity-document number or patient record appears in this summary.
