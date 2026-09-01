## Why

NUSynapxe currently opens a placeholder JavaFX window and only bootstraps an empty SQLite metadata table, so clinic staff have no way to authenticate or coordinate the appointment-to-checkout workflow. This change establishes the first useful product slice: role-based access for Doctors, Receptionists, and System Admins, a shared clinic operations model, and documentation that makes the application usable and maintainable.

## What Changes

- Replace the placeholder startup view with a first-run account setup flow and a login page shown whenever the application opens.
- Add persistent account management for the Doctor, Receptionist, and System Admin roles, including password verification and service-layer authorization.
- Add the shared clinic workflow: patient registration, appointment booking and state transitions, doctor schedule management, consultation records, prescriptions, checkout payments, and daily revenue summaries.
- Enforce the confidentiality boundary in application services: Receptionists manage patient contact and billing data but cannot read or write medical notes; Doctors manage clinical information for their assigned appointments; System Admins manage staff accounts.
- Expand the README, User Guide, and Developer Guide with the product purpose, role responsibilities, workflow, setup/login behavior, data model, extension points, and verification instructions.
- Add automated unit and JavaFX interaction tests for authentication, authorization, appointment conflicts and transitions, clinical-data protection, billing totals, account setup, and login routing.

## Capabilities

### New Capabilities

- `account-management`: First-run System Admin setup, login/logout, persistent staff accounts, password handling, role-aware sessions, and service-layer authorization for Doctor, Receptionist, and System Admin actions.
- `clinic-workflow`: Patient and appointment management across the book/check-in/consult/checkout flow, doctor scheduling and time-off, protected medical records, prescriptions and follow-up notes, receptionist billing, and daily revenue reporting.

### Modified Capabilities

None. The repository is an initial application scaffold and has no existing feature-level requirements to modify.

## Impact

- `src/main/java/nusynapxe`: add domain models, repositories, services, authentication/session handling, and JavaFX views while retaining the existing application entry point and SQLite database boundary.
- SQLite schema: add tables and indexes for users, patients, appointments, clinical records, prescriptions, payments, and account/bootstrap state.
- `src/test/java/nusynapxe`: add service, persistence, authorization, and TestFX coverage for the new flows.
- `README.md`, `../../../docs/UserGuide.md`, and `../../../docs/DeveloperGuide.md`: replace scaffold-only descriptions with product and development documentation; the existing Docusaurus configuration will continue to publish these files.
- `build.gradle` and CI may need only test/configuration changes; implementation should prefer the existing Java 25, JavaFX, SQLite, JUnit, Mockito, and TestFX stack and avoid introducing a network service.
