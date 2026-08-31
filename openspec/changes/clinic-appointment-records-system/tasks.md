## 1. Persistence and domain foundation

- [ ] 1.1 Define the role, appointment-status, payment-status, and immutable domain/DTO types used by both capabilities; verify the production source compiles with `./gradlew.bat compileJava --no-daemon --console=plain`.
- [ ] 1.2 Extend `SqliteDatabase` with an idempotent, versioned schema initializer for users, patients, appointments, doctor time-off, clinical records, prescriptions, and payments; verify a temporary database opens, reopens, enables foreign keys, and contains the expected constraints and indexes.
- [ ] 1.3 Add transaction and query helpers that commit successful operations and roll back failed operations without leaving partial writes; verify rollback and connection-close behavior with JUnit tests.
- [ ] 1.4 Implement the account repository with unique usernames, enabled flags, role persistence, and explicit password salt/verifier columns; verify round-trip persistence and duplicate-username rejection.
- [ ] 1.5 Implement patient, appointment, time-off, clinical-record, prescription, and payment repositories with explicit administrative/clinical projections and foreign-key relationships; verify repository tests never return clinical columns through the administrative projection.

## 2. Account management and authorization

- [ ] 2.1 Implement the password policy and JDK-backed salted password verifier using constant-time comparison; verify correct-password, wrong-password, per-account-salt, and no-plaintext-storage tests.
- [ ] 2.2 Implement System Admin bootstrap and staff-account creation for Doctor and Receptionist roles, including field validation, unique usernames, and an atomic empty-account check; verify first-run, repeat-setup, invalid-input, duplicate, and successful account tests.
- [ ] 2.3 Implement the in-memory session and centralized authorization policies for role and Doctor ownership; verify permitted Doctor, Receptionist, and System Admin operations and denied clinical/cross-doctor operations at the service layer.
- [ ] 2.4 Implement authentication for enabled accounts with generic failure handling and session clearing; verify valid login, invalid/disabled login, logout, and application-restart-without-session tests.

## 3. Clinic workflow services

- [ ] 3.1 Implement patient registration and administrative updates for Receptionists plus Doctor clinical-record access for assigned appointments; verify contact/billing updates preserve clinical data and Receptionists receive no clinical fields.
- [ ] 3.2 Implement appointment booking, cancellation, and rescheduling for Receptionists and Doctor-owned schedule management; verify cross-doctor receptionist access, Doctor ownership restrictions, and unchanged state after rejected requests.
- [ ] 3.3 Implement interval conflict detection for appointments and Doctor time-off, including transactional rescheduling; verify overlapping, equal-boundary, adjacent, and time-off conflict cases.
- [ ] 3.4 Implement the pending → accepted → checked-in → completed → checked-out state policy and allowed cancellation path; verify valid transitions and rejection of invalid transitions without writes.
- [ ] 3.5 Implement Doctor consultation notes, diagnosis, follow-up notes, and prescriptions with required-field validation and assigned-Doctor ownership; verify assigned access, unassigned denial, invalid input, and preservation of existing clinical data.
- [ ] 3.6 Implement Receptionist checkout payments using integer minor currency units and selected-date revenue aggregation; verify valid checkout, invalid amounts, duplicate/invalid state handling, successful-payment totals, and exclusion of cancelled or unsuccessful records.

## 4. JavaFX application shell and role workspaces

- [ ] 4.1 Replace the placeholder startup scene with a router that initializes the database, selects first-run setup versus login, and switches scenes without retaining an old session; verify startup routing and database lifecycle tests.
- [ ] 4.2 Build the first-run System Admin Setup and Login views with non-sensitive validation feedback, password fields, submit actions, and stable semantic ids; verify TestFX setup-to-login, valid-login, invalid-login, and no-session-after-restart scenarios.
- [ ] 4.3 Build the System Admin workspace for creating Doctor and Receptionist accounts and refreshing after successful creation; verify TestFX account creation and duplicate/invalid feedback.
- [ ] 4.4 Build the Receptionist workspace for all-doctor scheduling, patient administrative data, check-in, checkout, and daily revenue; verify TestFX markers and a representative book/check-in/checkout interaction.
- [ ] 4.5 Build the Doctor workspace for the own schedule, acceptance/rescheduling, time-off, consultation, clinical records, prescriptions, and completion; verify TestFX markers and a representative assigned-consultation interaction.
- [ ] 4.6 Add shared logout, role-specific navigation, authorization-error presentation, and post-write refresh behavior; verify that each role sees only its permitted actions and logout returns to Login.

## 5. Documentation and developer workflow

- [ ] 5.1 Update `README.md` with the Clinic Appointment & Records System purpose, role responsibilities, book-to-checkout workflow, first-run setup, login behavior, local database location, and verification commands; verify the Docusaurus overview renders the new content.
- [ ] 5.2 Update `docs/user-guide.md` with first launch, System Admin account creation, login/logout, Doctor and Receptionist workflows, clinical confidentiality boundaries, checkout/revenue use, and local-data cautions; verify all referenced commands and screens match the implementation.
- [ ] 5.3 Update `docs/developer-guide.md` with package layout, service/repository boundaries, schema/migration approach, password/session handling, state transitions, testing strategy, and CI instructions; verify examples use the repository's Java 25/Gradle/Node 24 toolchain.
- [ ] 5.4 Build the Docusaurus site from `website` with `npm ci` and `npm run build`; verify the build completes with broken-link checking enabled.

## 6. Integrated verification and quality gates

- [ ] 6.1 Add service-level integration tests covering first-run setup, three-role login, patient registration, appointment scheduling, consultation, checkout, and revenue summary against a temporary SQLite database; verify the complete workflow and confidentiality assertions pass.
- [ ] 6.2 Add TestFX coverage for startup routing, role workspaces, semantic controls, validation feedback, and logout; verify the suite passes through the existing CI display path using `xvfb-run` on Linux.
- [ ] 6.3 Run `./gradlew.bat spotlessApply check javadoc --no-daemon --console=plain` and inspect the generated JaCoCo, Checkstyle, PMD, and SpotBugs reports; verify all configured Java quality gates pass.
- [ ] 6.4 Run `openspec validate clinic-appointment-records-system --type change --strict` and `git diff --check`; verify the change artifacts and final documentation have no structural or whitespace errors.
