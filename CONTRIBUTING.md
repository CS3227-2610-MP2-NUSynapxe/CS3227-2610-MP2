# Contributing to NUSynapxe

Thank you for contributing to NUSynapxe, a Java 25 JavaFX desktop application
with local SQLite persistence, role-aware workflows, automated tests, quality
gates, and a Docusaurus documentation site. This guide explains the expected
development workflow for code, tests, documentation, OpenSpec artifacts, and
pull requests.

The project handles appointment and clinical information, so a contribution
must be reviewed for authorization, ownership, confidentiality, stored-data
compatibility, and test coverage in addition to ordinary compilation and
style requirements.

## Before starting

Read the following project documentation first:

- [`README.md`](README.md) for product scope, local execution, demo data, and
  the pinned toolchain.
- [`docs/DeveloperGuide.md`](docs/DeveloperGuide.md) for architecture,
  persistence, migrations, tests, and quality commands.
- [`docs/UserGuide.md`](docs/UserGuide.md) when changing user-visible
  workflows.
- [`AGENTS.md`](AGENTS.md) for repository-specific agent and contribution
  boundaries.
- The relevant files under [`openspec/`](openspec/) for behavioural or
  architectural changes.

For a new bug or feature, use the repository's GitHub issue templates. Keep a
change focused and explain the intended outcome before proposing a solution.

## Development prerequisites

The repository uses the checked-in Gradle Wrapper and expects:

- Java 25;
- Gradle Wrapper 9.7.1, invoked through `gradlew` or `gradlew.bat`;
- JavaFX 25.0.4, resolved by Gradle;
- Node.js 24 and npm for the documentation site; and
- a display-capable environment for JavaFX/TestFX checks.

On Windows, use PowerShell and `gradlew.bat`. On macOS or Linux, use
`./gradlew`. Do not require contributors to install a separate Gradle version
when the wrapper can perform the task.

## Getting started locally

From the repository root, compile and run the application with:

```powershell
.\gradlew.bat run
```

The application uses a per-user SQLite database by default:

```text
Windows: %USERPROFILE%\.nusynapxe\nusynapxe.db
macOS/Linux: ~/.nusynapxe/nusynapxe.db
```

For isolated work, provide a separate database path through the
`nusynapxe.database` Java system property. Do not commit a database file or
copy credentials, patient information, clinical notes, or generated records
into an issue, pull request, log, screenshot, or documentation example.

### Demo data

Demo-data scripts are intended for local development only:

```powershell
.\scripts\reset-demo-database.ps1 -Force
.\scripts\seed-demo-data.ps1
```

To reset and seed in one explicit operation:

```powershell
.\scripts\seed-demo-data.ps1 -Reset
```

Reset is destructive. The standalone reset script requires `-Force`, and
seeding an existing database without `-Reset` fails safely. Keep the generated
demo accounts and records local; they are not production credentials or
fixtures for external services.

## Choose the right workflow

### Small or bounded changes

For a focused bug fix or documentation-only change:

1. Inspect the existing flow and tests.
2. State the intended behaviour and non-goals.
3. Add or update a focused regression test when behaviour changes.
4. Make the smallest coherent change.
5. Run the relevant checks and record the exact commands and results.

### Behavioural or architectural changes

Use the repository-local OpenSpec workflow before implementation when a change
crosses domain, persistence, authorization, UI, testing, or documentation
boundaries. The workflow is intended to keep requirements and implementation
aligned:

```text
$openspec-explore
$openspec-propose
$openspec-apply-change
$openspec-archive-change
```

The `$` forms above are agent/chat skills, not PowerShell commands. When using
the OpenSpec CLI directly, validate one change with:

```powershell
openspec validate "<change-name>" --type change --strict --no-interactive
```

Validate the synchronized main specifications with:

```powershell
openspec validate --specs --strict --no-interactive
```

Update the relevant proposal, design, specification, and task artifacts when
requirements change. Sync the completed delta specifications before archiving,
and confirm that the archived change contains its metadata and task history.
Do not use `openspec validate --change`; this repository's supported form
requires the change name and `--type change`.

The upstream OpenSpec project is available at
[Fission-AI/OpenSpec](https://github.com/Fission-AI/OpenSpec).

### Agent-assisted contributions

Codex and Superpowers can help explore code, draft plans, implement changes,
write tests, and interpret quality reports. They do not replace contributor
responsibility for requirements, security, privacy, review, or verification.
Useful Superpowers principles for this repository are:

```text
clarify intent -> design -> approve -> implement -> verify
```

Use the upstream [obra/superpowers](https://github.com/obra/superpowers)
repository for the reusable skills and workflow guidance. When an agent is
used, record meaningful decisions and verify its claims independently. Do not
include private prompts, credentials, database contents, or sensitive logs in
the repository.

## Code and architecture expectations

Keep the existing separation of responsibilities:

- `domain` contains immutable records and workflow types;
- `persistence` owns SQLite access, schema changes, repositories, projections,
  and transaction boundaries;
- `service` owns authorization, ownership checks, validation, and business
  use cases;
- `ui` contains programmatic JavaFX views and routing; and
- `tools` contains local reset and demo-data utilities.

The UI should call service APIs rather than write SQL directly. Service-layer
authorization must remain effective even if a UI control is bypassed. Keep
clinical and administrative projections intentionally narrow, and do not load
or expose sensitive fields merely because a view could hide them.

For schema changes:

- increment the schema version in order;
- make migrations transactional and idempotent;
- preserve existing records unless the approved requirement says otherwise;
- test fresh initialization and upgrades from relevant older versions;
- test rollback or failure behaviour; and
- check foreign keys, indexes, projections, and related service behaviour.

For JavaFX changes:

- preserve semantic control IDs and accessible labels;
- keep lifecycle and authorization decisions in services;
- add TestFX coverage for important interactions;
- use screenshots or a temporary visual smoke test for layout defects when
  semantic assertions cannot observe the problem; and
- remove temporary probes unless they are intentional regression tests.

## Testing and quality checks

### Full Java quality gate

Before opening a pull request, run the repository quality gate from the root:

```powershell
.\gradlew.bat spotlessApply check javadoc --no-daemon --console=plain
```

This covers formatting, JUnit tests, TestFX, Checkstyle, PMD, SpotBugs with
FindSecBugs, JaCoCo report generation, and Javadoc. `spotlessApply` changes
formatting; use `spotlessCheck` when you need a read-only formatting check.

### Focused checks

Run a focused test while developing, then run the broader gate before making a
completion claim. Examples include:

```powershell
.\gradlew.bat test --tests nusynapxe.service.AppointmentServiceTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.ui.DoctorViewTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.architecture.ArchitectureTest --no-daemon --console=plain
.\gradlew.bat checkstyleMain checkstyleTest --no-daemon --console=plain
.\gradlew.bat jacocoTestReport --no-daemon --console=plain
```

If a check cannot run because of missing dependencies, display support,
authentication, or another environment limitation, record it as unverified
with the reason. Do not report a missing check as a pass and do not weaken a
quality gate to hide an environmental failure.

### Documentation site

If README or documentation-site content changes, run the Docusaurus checks:

```powershell
Set-Location website
npm ci
npm run build
```

The production build treats broken links as errors. Return to the repository
root after running the site commands before running root-level Gradle commands.

### Diff hygiene

Before committing, inspect the scope and check whitespace:

```powershell
git status --short
git diff --check
git diff --stat
```

Do not include `build/`, `website/build/`, local databases, IDE metadata,
credentials, or unrelated generated files in a contribution.

## Pull requests

Create a focused branch with a descriptive name such as `feature/<topic>`,
`fix/<topic>`, or `docs/<topic>`. Keep commits logically grouped and use
descriptive messages. Do not mix unrelated refactors with a feature or bug
fix.

Use the repository pull-request template at
[`.github/pull_request_template.md`](.github/pull_request_template.md). A
complete pull request should include:

- a concise summary and motivation;
- a linked issue, using a closing keyword where appropriate;
- the main code, test, documentation, and configuration changes;
- exact verification commands and outcomes;
- sanitized screenshots for visible UI changes;
- authorization, privacy, credential, migration, and stored-data impact;
- links to relevant OpenSpec artifacts and documentation; and
- known risks, limitations, and unverified checks.

The author checklist should be completed before requesting review. Reviewers
should be able to distinguish what was verified locally from what still needs
manual or remote confirmation.

## Security and privacy requirements

Never commit:

- real patient identity, clinical, payment, or account data;
- passwords, password verifiers, salts, API keys, or tokens;
- local SQLite files or database journals;
- unsanitized screenshots or logs; or
- credentials copied from demo-data output into production configuration.

When changing authentication, authorization, clinical projections, or stored
data, add tests for both the allowed and rejected paths. Treat hiding a button
as a usability feature, not as an authorization mechanism. The service layer
must enforce the relevant actor, role, ownership, state, and input rules.

## Contributor checklist

Before submitting a pull request, confirm:

- [ ] The change has a focused scope and a linked issue when applicable.
- [ ] Requirements, non-goals, and affected workflows are clear.
- [ ] Relevant OpenSpec artifacts are updated for behavioural or architectural
      changes.
- [ ] Focused tests cover the changed behaviour.
- [ ] The full Java quality gate has been run, or the limitation is recorded.
- [ ] Documentation and the website build are updated when needed.
- [ ] Authorization, privacy, schema, migration, and compatibility effects
      were reviewed.
- [ ] No real data, credentials, databases, or generated build outputs are
      included.
- [ ] The diff contains no unrelated changes and passes `git diff --check`.
- [ ] The pull-request template contains accurate verification evidence,
      risks, screenshots, and follow-up work.
