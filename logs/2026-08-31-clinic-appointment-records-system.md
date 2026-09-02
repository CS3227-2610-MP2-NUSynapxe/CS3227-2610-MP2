# AI Development History: Clinic Appointment and Records System

## Scope

This log summarizes the prompts, decisions, OpenSpec workflow, implementation,
verification, documentation correction, and archive activity recorded in the
current AI conversation for the NUSynapxe Clinic Appointment and Records
System.

The conversation began on 2026-08-31 and continued through 2026-09-02. No
real patient data, credentials, clinical notes, or payment information was
used. Only one AI agent, Codex, participated; no separate specialized agent
instance or external service was invoked.

Existing files in `logs/` were preserved. The attached
`src/main/java/nusynapxe/domain/AppointmentStatus.java` file was inspected as
context during the conversation and was not modified.

## Prompt and interaction history

### 1. Define the clinic product — User

The user asked for a Clinic Appointment and Records System with Doctor and
Receptionist roles, plus a System Admin role for creating staff accounts. The
requested responsibilities were:

- Doctors manage their own schedules, accept and reschedule appointments,
  block time off, create and edit medical records, write consultation,
  diagnosis, and follow-up notes, issue prescriptions, and complete
  appointments.
- Receptionists book, cancel, and reschedule appointments for all Doctors,
  register patients, maintain contact and billing information, check patients
  out, handle payments, and generate daily revenue summaries.
- System Admins add Doctor and Receptionist accounts.

The user explained that the feature has a shared Patient, Appointment, and
Doctor core, a real medical-confidentiality boundary, and a substantial
book-to-checkout workflow suitable for automated tests and CI.

The user also asked for the product to be documented in the README, User
Guide, and Developer Guide, and asked for account management and a login page
to appear when the application opens. The repository `README.md` was supplied
as context.

### 2. Start the OpenSpec change — User and Codex

The user invoked `$openspec-new-change` for the clinic system. The request was
interpreted as a feature change rather than a small UI-only edit because it
required persistence, authorization, workflows, JavaFX screens, tests, CI,
and documentation.

The change was organized as `clinic-appointment-records-system` with the
following capability areas:

- `account-management`: first-run System Admin setup, staff accounts,
  authentication, logout, sessions, and role/ownership authorization.
- `clinic-workflow`: patients, appointments, doctor schedules and time off,
  clinical consultations, prescriptions, checkout, and revenue reporting.

The OpenSpec proposal established a local JavaFX and SQLite application with
no network service, patient self-service, online payment integration, or
additional roles.

### 3. Fast-forward the planning artifacts — User and Codex

The user invoked `$openspec-ff-change continue`. The change artifacts were
prepared under
`openspec/changes/clinic-appointment-records-system/`:

- `proposal.md`
- `design.md`
- `specs/account-management/spec.md`
- `specs/clinic-workflow/spec.md`
- `tasks.md`

The task list contained 29 implementation and verification tasks covering the
domain model, SQLite schema, repositories, services, password handling,
authorization, JavaFX routing and workspaces, documentation, integration
tests, TestFX, quality gates, and OpenSpec validation.

### 4. Implement the approved system — User and Codex

The user invoked `$openspec-apply-change carry on with the implementation` and
provided the proposal as context. Codex followed the OpenSpec implementation
workflow and completed all 29 tasks.

The implementation established the following behavior and boundaries:

- Role, appointment-status, payment-status, account, patient, clinical,
  prescription, and payment domain/DTO types were added.
- SQLite initialization became versioned and idempotent, with tables and
  indexes for users, patients, appointments, doctor time off, clinical
  records, prescriptions, and payments.
- Repositories used explicit administrative and clinical projections so
  receptionist-facing patient data did not include medical notes, diagnoses,
  follow-up notes, or prescriptions.
- Account creation stores a per-account salt and password verifier rather than
  plaintext or reversible credentials.
- The first launch checks for an empty account store and presents a one-time
  System Admin setup form. Subsequent launches present Login.
- Successful authentication creates an in-memory role-aware session. Logout
  clears it, and restarting the application does not reuse it.
- Services enforce role permissions and Doctor ownership independently of UI
  visibility. Doctors cannot access another Doctor's schedule or clinical
  records, while Receptionists cannot read or write clinical information.
- Appointment scheduling checks overlapping appointments and Doctor time off.
  Rejected bookings and reschedules preserve the previous schedule.
- Appointment states follow the controlled workflow
  `PENDING -> ACCEPTED -> CHECKED_IN -> COMPLETED -> CHECKED_OUT`, with
  cancellation allowed before completion and invalid transitions rejected.
- Checkout stores integer minor currency units, commits payment and checked-out
  state together, and aggregates successful payments by local clinic date.
- The placeholder startup scene was replaced by routing for first-run setup,
  Login, System Admin, Receptionist, and Doctor workspaces.
- Shared logout, role-specific navigation, validation feedback, and refresh
  behavior were implemented.
- Unit, service-level integration, and JavaFX/TestFX coverage was added for
  authentication, confidentiality, scheduling, workflow transitions,
  consultation, checkout, revenue, routing, role workspaces, and logout.
- `README.md`, `docs/UserGuide.md`, and `docs/DeveloperGuide.md` were updated
  with product, operation, architecture, data, testing, and CI information.

### 5. Diagnose the Docusaurus CI failure — User and Codex

The user supplied a GitHub Actions log for `npm run build` and attached
`website/sidebars.js`. Webpack compiled both the client and server bundles,
but Docusaurus then failed its broken-link check with links to:

```text
/CS3227-2610-MP2/docs/developer-guide
/CS3227-2610-MP2/docs/user-guide
```

The failure appeared on the root page, the 404 page, and both guide pages.
This showed that the JavaScript compilation was successful and that the
failure occurred during final route validation.

Codex inspected the tracked filenames and found that Git records the guides
as:

```text
docs/DeveloperGuide.md
docs/UserGuide.md
```

The attached sidebar already used the matching path-qualified document IDs:

```javascript
guides: ['overview', 'docs/DeveloperGuide', 'docs/UserGuide']
```

The mismatch was in the footer links in `website/docusaurus.config.js`, which
used lowercase kebab-case routes. The fix changed those two links to
`/docs/DeveloperGuide` and `/docs/UserGuide`. Broken-link checking remained
configured as an error; it was not disabled or suppressed.

The exact production command was rerun from `website`:

```powershell
npm run build
```

It exited successfully and generated both
`build/docs/DeveloperGuide/index.html` and
`build/docs/UserGuide/index.html`. `git diff --check` also found no
whitespace errors.

### 6. Sync and archive the completed change — User and Codex

The user invoked `$openspec-archive-change` and explicitly requested that the
change be synced and archived. The attached `website/sidebars.js` was again
context only and was not changed during the archive operation.

Codex loaded the archive instructions and checked the change status:

- Schema: `spec-driven`
- Artifacts: proposal, specs, design, and tasks all complete
- Tasks: 29/29 complete, with no unchecked tasks
- Delta specs: `account-management` and `clinic-workflow`

The main `openspec/specs/` directory existed but did not yet contain those two
capability specifications. The sync plan was therefore to create two new
main specs, preserving each delta Purpose and moving the requirements under a
canonical `## Requirements` section.

Before writing, Codex retrieved the required specs instructions and read the
delta specifications. The following main specs were created:

- `openspec/specs/account-management/spec.md`
- `openspec/specs/clinic-workflow/spec.md`

The merge preserved all requirements and scenarios from the deltas without
leaving delta-only operation headers in the main specs. Verification found 20
matching requirement/scenario headers for account management and 25 matching
headers for clinic workflow. `openspec validate --specs` passed both specs.

The requested archive target was checked and found absent. The complete change
directory, including `.openspec.yaml`, was moved to:

```text
openspec/changes/archive/2026-09-01-clinic-appointment-records-system/
```

The source change directory no longer remained under the active changes
directory. A final `openspec list --json` returned an empty active-change
list.

### 7. Add this conversation summary — User and Codex

On 2026-09-02, the user requested a new log file containing the history of
prompts and AI-agent interactions in this conversation and explicitly required
that existing files in `logs/` not be modified. The user attached
`AppointmentStatus.java` as context.

Codex inspected the existing log filenames and formats, checked that the
target summary file did not already exist, and reviewed the attached enum.
This new file was then added without editing any existing log file or the
attached Java source file.

## Decisions and rationale

- The application uses three controlled staff roles: Doctor, Receptionist,
  and System Admin. Doctor and Receptionist have comparable workflow depth,
  while System Admin is limited to account administration.
- Authorization and clinical confidentiality are service-layer rules. UI
  controls may be role-specific, but hiding a control is not treated as
  authorization.
- Administrative patient information is kept separate from clinical records
  in storage, repository projections, DTOs, and services.
- Passwords are represented by salted, non-recoverable verifiers. Sessions are
  in memory and are cleared on logout or application restart.
- The application remains a single-workstation JavaFX/SQLite desktop system;
  encryption at rest, network synchronization, patient accounts, reminders,
  insurance processing, refunds, and external payment processing are outside
  this change.
- Appointment conflicts use interval checks for both appointments and Doctor
  time off. Payment totals use integer minor units to avoid floating-point
  rounding.
- Docusaurus route links match the case of the tracked guide filenames, and
  broken links continue to fail the build so future documentation mistakes are
  visible in CI.
- The completed delta specs were synced before the change was archived, as
  explicitly requested by the user.

## Verification record

The conversation recorded the following successful checks:

```text
npm run build
openspec validate --specs
openspec validate clinic-appointment-records-system --type change --strict
git diff --check
openspec list --json
```

The Docusaurus build generated static files with no broken-link errors. The
main capability specs validated successfully. Strict change validation and
whitespace checking passed, and the final OpenSpec active-change list was
empty after archiving.

No real patient identity, clinical record, password, or payment value appears
in this log. The only current change made by this request is the addition of
this new summary file; existing files in `logs/` were left untouched.

## Final status

The Clinic Appointment and Records System implementation, documentation, and
OpenSpec artifacts were completed. The Docusaurus guide-route mismatch was
corrected, the two capability deltas were synced into main specifications, and
the completed change was archived. This file now records the corresponding
prompt and AI-agent history for the conversation.
