# Developer Guide

## Toolchain and commands

The project uses Java 25, Gradle Wrapper 9.7.1, JavaFX 25.0.4, SQLite JDBC
3.53.2.1, and Node.js 24 for the Docusaurus site. JavaFX is resolved by the
Gradle plugin; a separate JavaFX SDK is not required.

Run the desktop application or the Java quality gate from the repository root:

```powershell
.\gradlew.bat run
.\gradlew.bat spotlessApply check javadoc --no-daemon --console=plain
```

Useful focused commands are:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.service.AppointmentServiceTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.persistence.SchemaMigrationTest --tests nusynapxe.persistence.PatientDirectoryRepositoryTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.service.PatientServiceTest --tests nusynapxe.ui.ReceptionistViewTest --no-daemon --console=plain
.\gradlew.bat checkstyleMain checkstyleTest --no-daemon --console=plain
.\gradlew.bat pmdMain --no-daemon --console=plain
.\gradlew.bat spotbugsMain --no-daemon --console=plain
.\gradlew.bat jacocoTestReport --no-daemon --console=plain
```

`spotlessApply` formats Java source. `spotlessCheck` is the read-only CI
equivalent. `check` runs JUnit, Checkstyle, PMD, SpotBugs with FindSecBugs, and
JaCoCo. Production quality gates fail the build on violations; PMD and
SpotBugs test tasks are disabled because their framework-specific analysis is
not useful for the TestFX harness.

## Package layout and boundaries

```text
src/main/java/nusynapxe/             Application entry point and database paths
src/main/java/nusynapxe/domain/      Immutable records and workflow enums
src/main/java/nusynapxe/persistence/SQLite connection, schema, repositories
src/main/java/nusynapxe/service/     Authorization and business-use-case rules
src/main/java/nusynapxe/ui/          Programmatic JavaFX views and scene router
src/test/java/nusynapxe/             JUnit, Mockito, persistence, service, TestFX tests
config/checkstyle/                   Checkstyle configuration
config/pmd/                          PMD ruleset
config/spotbugs/                     SpotBugs exclusions
website/                             Docusaurus configuration and lockfile
```

The UI calls `ClinicServices`; it does not write SQL. Services validate the
actor, role, ownership, state, and input before calling repositories.
Repositories contain explicit projections and transaction boundaries. This
keeps the confidentiality boundary testable even if a future UI accidentally
renders an unauthorized control.

## Persistence and schema

`SqliteDatabase.open()` creates the parent directory, enables SQLite foreign
keys, and delegates to the idempotent `SchemaInitializer`. The initializer
stores the schema version in `app_metadata` and creates:

```text
users                 account identity, role, enabled flag, salt/verifier
patients              Patient ID, documented identity, basic data, active flag
appointments          patient/Doctor interval and lifecycle status
doctor_time_off       blocked Doctor availability intervals
clinical_records      diagnosis and consultation/follow-up notes
prescriptions         medication and usage instructions
payments              checkout amount in integer minor units and method
```

Schema version 2 adds nullable identity type/number/country, sex, height,
weight, and active columns for backward compatibility. Schema version 3
removes `patients.billing_information` and clears legacy sex values other than
`FEMALE` and `MALE`; it does not alter the separate `payments` table. Schema
version 4 renames legacy `phone` to `phone_number` and adds nullable
`phone_country_code`. Existing telephone text is retained verbatim and a
migrated row requires a country code on its next save. Version-1
databases apply all migrations in order within one transaction. Existing rows keep their generated numeric
Patient IDs and related records; no identity or measurement values are
invented. New registrations require complete identity and sex values, and a
legacy row requires complete identity fields on its next basic-data save.
Migration advances `app_metadata.schema_version` only after every statement
succeeds, so failure rolls back both schema changes and the version marker.

`patients.id INTEGER PRIMARY KEY AUTOINCREMENT` is the immutable relational
Patient ID. The UI formats value `42` as `P000042` without storing another
identifier. NRIC, FIN, passport, and other documents are business identifiers,
not primary keys. SQLite enforces uniqueness over the normalized
`(identity_type, issuing_country, identity_number)` tuple. Repository binding
trims and uppercases document values, while the service performs a friendly
pre-check and maps uniqueness races to a non-sensitive duplicate message.

NRIC syntax is `[ST][0-9]{7}[A-Z]`, FIN syntax is
`[FGM][0-9]{7}[A-Z]`, and both require normalized issuing country `SG` at the
service boundary. Passport syntax is `[A-Z0-9]{5,20}`; these rules do
not perform government checksum validation. `phone_country_code` follows
`^[1-9][0-9]{0,2}$` and `phone_number` contains digits only. The plus sign is
added only when formatting the combined telephone number. Calling-code
suggestions use Google libphonenumber metadata and remain editable. Email has
non-empty text around `@`. Height is an optional positive whole number of
centimetres; weight is optional, positive, and limited to one decimal place.
Patient sex is limited to `FEMALE` or `MALE`. Patient-level billing information
is not stored; appointment payments remain in `payments`.
Patient deactivation sets `active = 0`, and reactivation restores `active = 1`;
neither transition reuses the Patient ID or deletes appointment, payment, or
clinical history.

Directory search accepts an exact numeric or `P`-formatted Patient ID and
case-insensitive partial document, country, name, phone, or email text. SQL
wildcards supplied by a user are escaped and treated literally. Blank search
lists the directory, and results use deterministic name-then-ID ordering.

New schema changes should remain ordered, versioned, and transactional. Keep
basic administrative and clinical columns in separate repository projections. All
appointment and time-off interval writes use transactions and reject overlap;
the overlap rule is `existing_start < new_end` and `existing_end > new_start`,
so adjacent intervals are valid.

## Account, session, and authorization design

The first account must be a System Admin. `AccountService` uses the
`PasswordHasher` PBKDF2WithHmacSHA256 implementation with a random per-account
salt and stores only the salt/verifier byte arrays. `AuthenticationService`
creates an in-memory `Session` after verifying an enabled account and clears
the submitted password array. Sessions are never serialized to SQLite.

`Authorization.requireRole` and `requireDoctorOwnership` are called by every
protected service operation. Receptionist registration, search, detail,
update, deactivation, and activation require `Role.RECEPTIONIST`. `PatientRepository`
selects an explicit basic-data projection and never joins clinical tables;
the `Patient` record cannot contain diagnoses, notes, or prescriptions.
`ClinicalService` requires the assigned Doctor and a checked-in or later
appointment. System Admin is limited to account administration.

Treat document numbers as private data: do not include complete values in
exceptions, logs, screenshots, fixtures, or generated reports. Duplicate
failures use one fixed message and never echo the submitted identity tuple.

## Workflow rules

The Receptionist scheduling dashboard uses `AppointmentRepository.search` and
`AppointmentService.searchAppointments` for optional date, Doctor, patient, and
status filters. It derives summary counts from the same filtered result set.
The repository joins only the administrative patient projection, and the UI
formats rows with Patient ID/name, Doctor ID, interval, and status. Booking
rejects inactive patients at the service boundary. Date/time parsing accepts
calendar date plus `HH:mm` as well as the legacy complete timestamp form, and
all conflict and lifecycle checks remain transactional service/repository
rules.

The lifecycle policy is:

```text
PENDING -> ACCEPTED -> CHECKED_IN -> COMPLETED -> CHECKED_OUT
    \          /
     \-> CANCELLED
```

Receptionists book for any Doctor, check in at or after the start time, and
check out a completed appointment. Doctors accept assigned appointments,
reschedule their own pending/accepted appointments, block time off, save one
clinical record per consultation, add prescriptions, and complete checked-in
appointments. Invalid transitions leave persistence unchanged. Billing
stores integer minor units and aggregates successful payments by local date.

## UI and TestFX conventions

`ApplicationRouter` opens Login or first-run Setup and routes an authenticated
session to one of the role workspaces. Views are built programmatically so
semantic ids remain easy to assert. The patient area has independent
`Register new patient` and `Search and manage patients` tabs. Country options
come from `Locale.getISOCountries()`, use English display names, persist ISO
two-letter codes, and order Singapore first. NRIC and FIN selection chooses
and locks Singapore automatically; service validation independently enforces
the same rule. Patient forms use `DatePicker` plus synchronized month and year
selectors; impossible day/month combinations clamp to the month's final day.
Age is derived with the `Asia/Singapore` date, has no placeholder, and is never
persisted. Male precedes Female in the sex selector. The telephone `+` is a
fixed label outside the editable, digits-only country-code field. The search
tab contains only search controls and results; selecting a result opens an
owned modal details stage for editing and reversible active-status changes. The top-level
`reception-workspace-tabs` separates patient data, appointments, checkout, and
revenue. Actions and tab selection refresh affected data, so Receptionist view
has no manual refresh control. Important ids include `login-submit`, `setup-submit`,
`admin-account-submit`, `reception-patient-tabs`,
`reception-register-identity-type`, `reception-register-issuing-country`,
`reception-register-phone-country-code`, `reception-register-phone-number`,
`reception-register-phone-plus`, `reception-register-date-of-birth`,
`reception-register-date-of-birth-month`, `reception-register-date-of-birth-year`,
`reception-register-age`, `reception-patient-details-window`, `reception-patient-id`,
`reception-patient-identity-type`, `reception-patient-identity-number`,
`reception-patient-issuing-country`, `reception-patient-search`,
`reception-patient-search-submit`, `reception-patient-update`,
`reception-patient-deactivate`, `reception-book`, `reception-checkout`,
`doctor-consultation-save`, and `logout-button`.

TestFX tests require a display. The CI workflow runs the Java suite through
`xvfb-run --auto-servernum` on Ubuntu. For controls inside a scroll pane,
tests should wait for the view marker and use semantic JavaFX-thread actions
when a control is not physically visible. This avoids coupling tests to
layout coordinates while still exercising each control's event handler.

JaCoCo reports are generated at:

```text
build/reports/jacoco/test/html/index.html
build/reports/jacoco/test/jacocoTestReport.xml
```

## Documentation site and CI

From the repository root, build or serve the Docusaurus site with:

```powershell
Set-Location website
npm ci
npm run build
npm run start
```

`website/docusaurus.config.js` reads `README.md` and the two files in `docs/`
as documentation pages. Broken site links fail the production build. GitHub
Actions uses JDK 25, Node.js 24, `xvfb-run`, `./gradlew check javadoc`, and
`npm ci && npm run build`; it publishes the generated `build/reports/` files
as a quality-report artifact.
