---
id: overview
title: NUSynapxe
slug: /
sidebar_label: Overview
---

# NUSynapxe Clinic Appointment & Records System

NUSynapxe is a Java 25 JavaFX desktop application for coordinating clinic
appointments, patient administration, consultations, prescriptions, and
checkout. It uses a local SQLite database and has three controlled staff
roles: Doctor, Receptionist, and System Admin.

The shared workflow is:

```text
book -> accept -> check in -> consult -> complete -> checkout
```

Receptionists coordinate the administrative and scheduling steps across the
clinic. Doctors manage their own schedules and the clinical information for
their assigned appointments. System Admins create the Doctor and Receptionist
accounts used to access the application.

## Roles and confidentiality

| Role | Responsibilities |
| --- | --- |
| System Admin | Create enabled Doctor and Receptionist accounts. |
| Receptionist | Register patients, maintain administrative contact information, book/cancel/reschedule appointments for any Doctor, check patients in, record checkout payments, and view daily revenue. |
| Doctor | View and manage the Doctor's own schedule, accept or reschedule appointments, block time off, record diagnoses/consultation/follow-up notes and prescriptions, and complete consultations. |

Patient administration and clinical information are separate by design.
Receptionists can access administrative patient information but never receive or
modify medical notes, diagnoses, follow-up notes, or prescriptions. The same
boundary is enforced in the service layer, not only by hiding JavaFX controls.

## First launch and login

Run the desktop application from the repository root:

```powershell
.\gradlew.bat run
```

If the local database contains no accounts, the first window is the one-time
System Admin setup form. Create a username and a password with at least eight
non-blank characters. The application then routes to Login. Log in as the
System Admin and create the Doctor and Receptionist accounts from the admin
workspace. Subsequent launches show Login directly; no session is persisted
when the application closes, and Log out returns to Login immediately.

## Local database

The default database is stored per user at:

```text
Windows: %USERPROFILE%\.nusynapxe\nusynapxe.db
macOS/Linux: ~/.nusynapxe/nusynapxe.db
```

For an isolated development database, set the `nusynapxe.database` Java
system property to another path. The application creates the parent directory
and initializes the versioned schema when it opens the database. The database
contains account credentials as salted PBKDF2 verifiers, not plaintext
passwords. Do not commit the database file or copy it into an issue or log.

## Demo database

The repository includes PowerShell scripts for preparing the local-development
database used by `.\gradlew.bat run`. They default to the normal per-user
database at `%USERPROFILE%\.nusynapxe\nusynapxe.db`:

```powershell
.\scripts\reset-demo-database.ps1 -Force
.\scripts\seed-demo-data.ps1
```

To recreate the demo database in one step, use
`.\scripts\seed-demo-data.ps1 -Reset`. The standalone reset script requires
`-Force` for an existing database; seeding an existing database without
`-Reset` fails safely. The seed contains two Doctors, a System Admin, a
Receptionist, six patients, calendar settings with a lunch break, and enough
future appointments to exercise the Schedule view's lazy loading.

After seeding, launch the application normally:

```powershell
.\gradlew.bat run
```

The script prints the demo credentials after a successful seed. These accounts
and passwords are for local demonstrations only and must not be used in a
production database.

The complete operating instructions are in the [User Guide](docs/UserGuide.md).
The package, persistence, security, testing, and CI details are in the
[Developer Guide](docs/DeveloperGuide.md).

## Toolchain

Versions are pinned for reproducible builds as of 31 August 2026.

| Area | Version |
| --- | --- |
| Java | 25 (toolchain) |
| Gradle Wrapper | 9.7.1 |
| JavaFX | 25.0.4 |
| SQLite JDBC | 3.53.2.1 |
| JUnit Jupiter | 6.1.3 |
| TestFX | 4.0.18 |
| Mockito | 5.23.0 |
| Spotless | 8.10.1 |
| Google Java Format | 1.36.1 |
| Checkstyle | 14.1.0 |
| PMD | 7.26.0 |
| SpotBugs Gradle plugin | 6.5.11 |
| SpotBugs engine | 4.10.3 |
| FindSecBugs | 1.14.0 |
| JaCoCo | 0.8.14 |
| Docusaurus | 3.10.2 |

## Verify the project

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat check javadoc --no-daemon --console=plain
```

`check` runs JUnit (including TestFX), Checkstyle, PMD, SpotBugs with
FindSecBugs, and JaCoCo. Reports are written below `build/reports/`, including
the HTML coverage report at `build/reports/jacoco/test/html/index.html`.

## Build the documentation site

```powershell
Set-Location website
npm ci
npm run start
```

Use `npm run build` for a production documentation build with broken links
treated as errors. The Docusaurus site uses this README as its overview page
and exposes the developer and user guides as separate navigation entries.
