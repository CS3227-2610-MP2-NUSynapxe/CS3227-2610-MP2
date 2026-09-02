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

## Validated form and feature-tab revision

The user subsequently requested separate editable phone country-code and
digits-only number controls, automatic calling-code suggestions, a calendar
date-of-birth control with Singapore-time age, required markers and stronger
validation, separate top-level feature tabs, automatic refresh, top-right
logout, and no Patient ID on registration. This correction supersedes the
earlier permissive NRIC/FIN decision: NRIC and FIN now receive Singapore syntax
checks, passports use a broad international alphanumeric rule, and no checksum
or external government verification is claimed.

The accepted implementation advances the schema to version 4 without losing
the old phone text, derives age rather than storing it, uses maintained Google
libphonenumber calling-code metadata, and keeps the suggested country code
editable. Patient data, appointments, checkout, and daily revenue are separate
top-level tabs. No manual Receptionist refresh button remains; successful
actions and feature navigation reload affected data.

Revision tests cover direct version-3 phone migration, required fields,
email, NRIC/FIN/passport formats, future dates, measurement precision,
calling-code autofill, calendar age, absence of a registration Patient ID,
four feature tabs, automatic refresh, and the existing confidentiality and
workflow boundaries. The final full run completed 55 tests and passed
Spotless, Checkstyle, PMD, SpotBugs with FindSecBugs, JaCoCo report generation,
Javadoc, and strict OpenSpec validation. Only synthetic identifiers are used
in tests.

The user then clarified that NRIC and FIN must strictly use Singapore as the
issuing country and that the calling-code field itself must contain only
numbers. The OpenSpec proposal, specification, design, and tasks were corrected
before implementation. The UI now locks Singapore for NRIC/FIN, the service
rejects non-Singapore values if the UI is bypassed, and calling codes are stored
as digits such as `65`; a leading plus is added only for combined display and
search. This correction was kept in local commits and was not pushed.

## Date navigation and patient-details revision

The user next requested direct month and year navigation for date of birth,
Male first in the sex selector, a fixed telephone plus sign, no automatic-age
placeholder, patient details in a separate window, and reversible patient
activation. The OpenSpec proposal, specification, design, and tasks were
updated before implementation.

The implementation keeps the calendar day selector and synchronizes it with
month/year dropdowns, opens an owned modal details stage only after a search
result is selected, and exposes one status button whose text follows the
patient's current state. Repository and Receptionist service operations now
support both status transitions while retaining the same Patient ID and all
history. Focused repository, service, and TestFX tests cover these behaviors.
Only synthetic patient data is used in tests, and no changes were pushed.

The scheduling UI was refined with editable searchable patient and Doctor
selectors, half-hour time dropdowns, and a patient-context rescheduling popup.
The popup displays only administrative patient details and provides separate
reschedule and cancellation actions. Focused TestFX, service, and PMD checks
passed after the refinement.

## Receptionist scheduling dashboard

The next approved feature is the Receptionist all-Doctor scheduling dashboard.
The implementation adds optional date, Doctor, patient, and status filters,
summary counts, calendar-based booking dates, inactive-patient protection, and
an administrative appointment list. Existing conflict detection and lifecycle
transitions remain service-layer rules. Focused repository, service, and
Receptionist TestFX tests were extended; no real patient data is recorded here.
