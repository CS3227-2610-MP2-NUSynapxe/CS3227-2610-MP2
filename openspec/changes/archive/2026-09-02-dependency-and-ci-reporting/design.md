## Context

The project has one Gradle Java application with a Docusaurus npm project under
`website/`. `build.gradle` already applies PMD, Checkstyle, SpotBugs, and
JaCoCo, but disables test PMD and SpotBugs tasks. The Java CI job runs the
quality gate and documentation build, has read-only contents permission, and
uploads `build/reports/` only after the preceding steps succeed. JUnit XML is
generated below `build/test-results/test/`, while JaCoCo XML is generated at
`build/reports/jacoco/test/jacocoTestReport.xml`.

## Goals / Non-Goals

**Goals:**

- Monitor the Gradle build, documentation npm project, and workflow actions on
  a weekly Dependabot schedule.
- Analyze test sources with a dedicated PMD policy without weakening the
  production ruleset.
- Preserve available JUnit and JaCoCo reports when verification fails.
- Publish one updateable combined test and coverage comment for eligible
  pull requests.
- Keep report-writing permissions away from the job that executes the build.

**Non-Goals:**

- No application, database, API, or persisted-data changes.
- No automatic dependency merging or new coverage threshold policy.
- No PR comments for fork or Dependabot pull requests.
- No `pull_request_target`, personal access token, or privileged workflow that
  checks out and executes pull-request code.

## Decisions

### Weekly dependency sources

Add one `dependabot.yml` configuration with three independent weekly update
entries:

1. `gradle` at `/` for `build.gradle` and the Gradle wrapper.
2. `npm` at `/website` for `package.json` and `package-lock.json`.
3. `github-actions` at `/` for action references in workflow files.

The entries remain independent so a Java, documentation, or workflow upgrade
can be reviewed and reverted without coupling unrelated failures. The
configuration covers version-update proposals; repository-level Dependabot
alert/security-update settings remain GitHub configuration rather than being
assumed to change through this file.

### Separate PMD rulesets

Keep `config/pmd/ruleset.xml` as the production policy. Add a test-specific
ruleset that starts with the error-prone checks and a selected subset of
best-practice checks that are meaningful for tests. Framework-specific
TestFX lifecycle methods and test-fixture patterns are relaxed only through
named, documented rule exclusions in that test ruleset.

Configure the production and test PMD tasks to use their respective rulesets,
enable the test task, and retain XML and HTML reports for both source sets.
The test task remains a blocking part of the Gradle quality gate. The first
implementation run will classify its findings; only findings demonstrated to
be framework-specific may be added to the test ruleset's explicit exceptions.
Broad failure suppression and changes to the production ruleset are not part
of this design.

### Two-job CI reporting

Retain a `verify` job with `contents: read` that checks out the repository,
runs the existing Java and documentation verification, and uploads a report
input artifact with `if: always()`. The artifact contains any available JUnit
XML and JaCoCo XML files; artifact upload warnings for files that could not be
generated must not turn a failed build into a successful one.

Add a separate reporting job that depends on `verify` and is guarded to run
only for a pull request whose head repository is the current repository and
whose author is not Dependabot. The job downloads only the report artifact and
does not check out or execute pull-request source code. Its permissions are
limited to `contents: read`, `checks: write`, and `pull-requests: write`.

The reporting sequence is:

1. Run `madrapps/jacoco-report@v1.8.0` against the JaCoCo XML with
   `comment-type: summary`, so it calculates coverage without creating a PR
   comment and exposes the overall and changed-line values.
2. Run `mikepenz/action-junit-report@v6` against
   `build/test-results/test/TEST-*.xml` with a detailed summary and comment
   updates enabled. Pass the JaCoCo values into its summary text, making this
   the only PR comment.

Both report steps are conditional on their input files being available and
must run after a failed verification when usable reports exist. Missing
coverage is reported as unavailable rather than being presented as a passing
or zero-percent result. The existing Gradle quality gate remains the source
of pass/fail status; the comment actions are informational and do not add a
new coverage threshold.

The action documentation requires check-write permission for the JUnit check,
pull-request write permission for its comment, and pull-request write
permission for the JaCoCo PR integration:

- https://github.com/marketplace/actions/junit-report-action
- https://github.com/Madrapps/jacoco-report

### Documentation

Update the Developer Guide to describe the test PMD command and policy, the
JUnit and JaCoCo report paths, the weekly dependency sources, the single
comment behavior, and the deliberate fork/Dependabot limitation. Keep the
README's high-level quality-gate description accurate without duplicating the
workflow implementation.

## Risks / Trade-offs

- **Test PMD produces framework noise** -> Start with a selective test ruleset,
  inspect the first report, and record only narrow TestFX-specific exceptions.
- **A failed build may not produce every report** -> Run artifact and reporter
  handling with `always()`, check file availability, and label absent coverage
  as unavailable.
- **Two actions must share one comment's data** -> Run JaCoCo summary first,
  pass only its numeric outputs to the JUnit comment, and keep JaCoCo out of
  PR-comment mode.
- **Write permissions could expose the repository** -> Isolate them to the
  reporting job, restrict that job to same-repository non-Dependabot PRs, and
  never execute downloaded pull-request code there.
- **Weekly updates can introduce incompatible major versions** -> Keep
  ecosystem entries independent, require the existing CI checks, and review
  major updates manually.

## Migration Plan

This is an additive repository-automation change and requires no data or
runtime migration. After merging, verify one same-repository pull request to
confirm that the combined comment is created and updated on a rerun, and
confirm that fork and Dependabot runs skip comment writes while retaining
available artifacts. Reverting the repository changes disables the schedule,
test PMD task, and report comment without affecting application data.
