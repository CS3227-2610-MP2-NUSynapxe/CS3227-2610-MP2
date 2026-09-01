## Context

The repository is a Java 25 JavaFX desktop application whose current entry point opens `SqliteDatabase` and renders a placeholder `NUSynapxeView`. SQLite is local to the current machine, the existing schema contains only `app_metadata`, and the project already provides JUnit, Mockito, TestFX, JaCoCo, Checkstyle, PMD, SpotBugs, and a Docusaurus site. See `proposal.md` for the motivation and `specs/account-management/spec.md` and `specs/clinic-workflow/spec.md` for the externally visible contract.

The design assumes one desktop process using one local database at a time. It does not introduce a server, patient self-service, cross-clinic synchronization, or online payment integration.

## Goals / Non-Goals

**Goals:**

- Keep authentication, ownership, and confidentiality decisions in services that can be tested without JavaFX.
- Make the first launch usable without shipping a shared default password.
- Represent the book/check-in/consult/checkout workflow with explicit state and transactional persistence.
- Keep administrative patient fields separate from clinical fields in both storage and service return types.
- Preserve the current JavaFX, SQLite, and Gradle stack and the existing documentation publishing path.
- Make every major behavior verifiable through repository/service tests, JavaFX interaction tests, and the existing CI quality gate.

**Non-Goals:**

- Networked multi-user concurrency, remote backups, patient-facing accounts, appointment reminders, insurance claims, refunds, or external payment processing.
- A general-purpose permission editor or creation of additional roles beyond Doctor, Receptionist, and System Admin.
- Encryption of the SQLite file at rest; the application will protect credentials and in-app data access, while operating-system file permissions remain an environment responsibility.

## Decisions

### Use a layered service boundary over the existing SQLite adapter

Add domain values and immutable data-transfer records for roles, account data, patients, appointments, clinical records, prescriptions, payments, and workflow states. Add repositories under `nusynapxe.persistence` for SQL and transactions, and services above them for validation, authorization, and orchestration. JavaFX views call services and receive role-appropriate DTOs rather than querying repositories directly.

This keeps authorization testable and prevents a UI-only restriction from becoming the security boundary. It also fits the current `SqliteDatabase` abstraction. A direct view-to-SQL approach was considered and rejected because it would duplicate validation, expose clinical columns accidentally, and make TestFX tests responsible for business rules.

### Extend the existing SQLite metadata bootstrap with an idempotent schema initializer

On open, initialize the feature tables and indexes after enabling foreign keys. Store a schema version in `app_metadata` so future changes can be applied in ordered, repeatable migrations. The initial schema will include:

- `users`: unique username, display name, role, enabled flag, password salt, password verifier, and creation timestamp.
- `patients`: identity, contact, and billing fields only.
- `appointments`: patient, doctor, start/end time, status, and creation/update timestamps.
- `doctor_time_off`: doctor and unavailable start/end interval.
- `clinical_records`: one consultation record linked to an appointment, patient, and assigned doctor, with diagnosis, consultation notes, and follow-up notes.
- `prescriptions`: consultation link plus medication, dosage, frequency, duration, and instructions.
- `payments`: completed appointment link, amount in minor currency units, payment method, status, and local recorded timestamp.

Foreign keys, unique constraints, non-null constraints, and status checks will enforce structural integrity. Appointment conflict checks will cover both appointments and time-off intervals. A repository operation that changes an appointment slot will perform the conflict check and update in one transaction so a rejected reschedule cannot partially alter the schedule.

An integer minor-unit representation will be used for persisted money values and converted to/from a decimal presentation value at the service boundary. This avoids floating-point revenue totals. Payment writes and the checked-out appointment state will be committed together.

### Bootstrap the first administrator through an explicit setup screen

After database initialization, the application router will ask the account service whether any users exist. If none exist, it will show `System Admin Setup`; otherwise it will show `Login`. A successful setup creates exactly one enabled System Admin and routes to login. There will be no hard-coded credentials. The account service will repeat the empty-account check inside the create transaction so two setup attempts cannot create competing initial administrators.

The current `NUSynapxeApp.start` method will retain ownership of the database lifecycle and stage. Scene changes will be centralized in a small router so login, setup, role workspaces, and logout cannot leave an old authenticated view visible.

### Use JDK password hashing and ephemeral sessions

Password creation will generate a cryptographically random per-account salt and store only a salted PBKDF2 verifier using the JDK security APIs. Verification will use the stored salt and a constant-time comparison; the original password will not be persisted. The hashing parameters will be kept in one policy component so they can be tested and upgraded deliberately.

Successful login will create an in-memory session containing the account identifier and role. The session is passed to protected services, is cleared on logout, and is never serialized to SQLite. Login failures will use one generic response for unknown, disabled, and wrong-password cases. A password-hashing library was considered but rejected because the current application is offline and the JDK provides the required primitives without adding a dependency.

### Centralize role and ownership checks in services

Use separate services for authentication/accounts, patients, appointments, clinical records, and billing. Each service will validate the session before repository access. Receptionist patient queries will return administrative projections; clinical services will require the assigned Doctor identity; account services will require System Admin for staff creation. The UI may hide unavailable menus, but service methods will reject unauthorized calls independently.

Appointment states will be represented by an enum and validated through a transition policy. Receptionist booking starts at `PENDING`; the assigned Doctor changes it to `ACCEPTED`; Receptionist check-in changes it to `CHECKED_IN`; the assigned Doctor records consultation data and changes it to `COMPLETED`; Receptionist checkout records payment and changes it to `CHECKED_OUT`. Cancellation is allowed before completion and changes the appointment to `CANCELLED`. Invalid transitions are rejected without repository writes.

### Build role-specific JavaFX workspaces from shared controls

Replace the placeholder view with programmatic JavaFX views, following the existing code style. The login and setup views will expose stable semantic ids for TestFX. After login, the router will display a Doctor, Receptionist, or System Admin workspace with only the actions relevant to that role:

- Doctor: own schedule, accept/reschedule, time-off, assigned consultation, clinical record, prescription, and completion actions.
- Receptionist: all-doctor appointment and patient administration, check-in, checkout, and daily revenue summary.
- System Admin: Doctor and Receptionist account creation.

Forms will validate required fields before calling services and show non-sensitive validation or authorization feedback. Table and form data will be refreshed from service results after successful writes rather than mutating an unrelated view model.

### Treat documentation as part of the feature contract

Update the root README with the product purpose, roles, workflow, launch commands, and the first-run login expectation. Update the User Guide with setup, login/logout, role workflows, privacy boundaries, and local-data handling. Update the Developer Guide with package structure, schema and service boundaries, migration/bootstrap behavior, testing strategy, and CI commands. Keep the existing Docusaurus configuration and verify that all three published Markdown entry points build successfully.

## Risks / Trade-offs

- [Risk] A local SQLite file can be copied by an operating-system user with file access. → [Mitigation] Never store plaintext passwords, keep clinical access behind services, document the local-storage boundary, and leave full-disk/file encryption as an environment responsibility.
- [Risk] A schema initializer can fail halfway through a first launch. → [Mitigation] Apply each migration transactionally where SQLite permits, record the schema version only after success, and add reopen/upgrade tests using temporary databases.
- [Risk] Appointment conflict logic can drift between booking and rescheduling. → [Mitigation] Put both operations through the same repository/service path and cover equal-boundary, overlapping, adjacent, and time-off cases.
- [Risk] JavaFX tests can be sensitive to display availability and virtualized controls. → [Mitigation] Keep business rules in unit tests, use stable node ids and semantic selectors for TestFX, and retain the CI `xvfb-run` execution path.
- [Risk] Clinical fields could leak through a broad patient DTO or table query. → [Mitigation] Define separate administrative and clinical projections, select columns explicitly, and add negative authorization tests that inspect both returned data and persisted state.
- [Risk] A single local database limits simultaneous staff use. → [Mitigation] State the single-workstation constraint in the guides and keep repositories/service interfaces sufficiently isolated for a future server-backed implementation.

## Migration Plan

1. On application startup, create the new tables and indexes through the versioned schema initializer while preserving the existing `app_metadata` table.
2. If the database has no users, show first-run System Admin Setup; otherwise preserve all existing data and show Login.
3. Run the application and documentation verification suites before packaging or distributing the desktop app.
4. For rollback during development, close the application and restore the pre-change SQLite file or remove the local development database; no destructive automatic rollback will run against a user's database.
