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
patients              contact and billing administration only
appointments          patient/Doctor interval and lifecycle status
doctor_time_off       blocked Doctor availability intervals
clinical_records      diagnosis and consultation/follow-up notes
prescriptions         medication and usage instructions
payments              checkout amount in integer minor units and method
```

New schema changes should be versioned and applied in one transaction. Keep
administrative and clinical columns in separate repository projections. All
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
protected service operation. Receptionists receive `Patient` administrative
records only. `ClinicalService` requires the assigned Doctor and a checked-in
or later appointment. System Admin is limited to account administration.

## Workflow rules

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
semantic ids remain easy to assert. Important ids include
`login-submit`, `setup-submit`, `admin-account-submit`,
`reception-book`, `reception-checkout`, `doctor-consultation-save`, and
`logout-button`.

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
