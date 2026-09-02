## Why

The project has pinned Gradle, npm, and GitHub Actions dependencies but no
automated update schedule, so dependency maintenance can become stale or
manual. Production PMD analysis is enabled while test analysis is disabled,
and CI currently stores generated quality reports without presenting a
compact test and coverage result directly on a pull request.

## What Changes

- Add weekly Dependabot version updates for the root Gradle build, the
  `website` npm project, and GitHub Actions workflow references.
- Enable Gradle's `pmdTest` task with a separate, deliberately relaxed PMD
  ruleset for JUnit, Mockito, persistence, service, and TestFX test code.
- Keep the test PMD task part of the Java quality gate while allowing only
  explicitly documented test-harness exceptions.
- Extend pull-request CI to collect JUnit XML and JaCoCo XML reports and
  publish one sticky combined PR comment containing test results and coverage.
- Restrict PR comment creation to pull requests from this repository that are
  not created by Dependabot; fork and Dependabot runs continue to receive CI
  results and artifacts without comment writes.
- Document the dependency-update policy, test PMD ruleset, report locations,
  and PR-comment limitations.

## Capabilities

### New Capabilities

- `dependency-and-ci-reporting`: Automated dependency maintenance, test-source
  PMD quality analysis, and combined pull-request test and coverage reporting.

### Modified Capabilities

- None.

## Impact

- Repository automation: `.github/dependabot.yml` and
  `.github/workflows/ci.yml`.
- Gradle quality configuration: `build.gradle` and a new test-specific PMD
  ruleset under `config/pmd/`.
- Developer documentation describing the new quality-gate and reporting
  behavior.
- CI action permissions and report handling; no application runtime, database,
  API, or persisted-data behavior changes.
