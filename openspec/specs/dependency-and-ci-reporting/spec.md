# dependency-and-ci-reporting Specification

## Purpose

Provides repeatable dependency maintenance and actionable static-analysis and
test feedback for contributors without changing the clinic application's
runtime behavior or persisted data.

## Requirements

### Requirement: The repository SHALL check all supported dependency sources weekly

The repository SHALL configure weekly version-update checks for the root Gradle
build and wrapper, the documentation site's npm manifest and lockfile, and
GitHub Actions workflow references. Each update source SHALL be monitored from
the directory containing its manifest or workflow files.

#### Scenario: Root Java dependencies are monitored
- **WHEN** the weekly dependency-update schedule runs
- **THEN** the Gradle build dependencies and Gradle wrapper version are eligible for update proposals

#### Scenario: Documentation dependencies are monitored
- **WHEN** the weekly dependency-update schedule runs
- **THEN** dependencies declared by `website/package.json` and `website/package-lock.json` are eligible for update proposals

#### Scenario: Workflow actions are monitored
- **WHEN** the weekly dependency-update schedule runs
- **THEN** action references used by repository workflow files are eligible for update proposals

### Requirement: The Java quality gate SHALL analyze test sources with a dedicated relaxed PMD policy

The Java quality gate SHALL run PMD against test sources as well as production
sources. Test sources SHALL use a separate relaxed ruleset that preserves
meaningful error-prone and best-practice checks while explicitly accommodating
documented JUnit, Mockito, persistence-fixture, and TestFX harness patterns.
Test PMD violations not covered by an explicit rule or documented exception
SHALL fail the quality gate and SHALL be included in a machine-readable PMD
report.

#### Scenario: Test sources are analyzed during quality verification
- **WHEN** the Java quality gate runs
- **THEN** production and test sources are both analyzed and separate PMD reports are generated

#### Scenario: Test-harness patterns follow the relaxed policy
- **WHEN** a test uses an approved framework-specific lifecycle or fixture pattern
- **THEN** the test PMD ruleset permits that pattern without weakening the production PMD ruleset

#### Scenario: An unapproved test violation fails verification
- **WHEN** a test source violates a PMD rule that is not relaxed or explicitly excluded
- **THEN** the Java quality gate fails and identifies the violation in its PMD report

### Requirement: Pull-request CI SHALL retain machine-readable test and coverage reports

Pull-request verification SHALL generate JUnit XML test results and JaCoCo XML
coverage results whenever the relevant build stages produce them. Generated
reports SHALL be retained as workflow artifacts even when a later verification
step fails, and missing reports SHALL not be represented as a successful test
or coverage result.

#### Scenario: A successful verification produces report artifacts
- **WHEN** the Java tests and coverage report complete
- **THEN** the JUnit and JaCoCo XML files are available as CI artifacts

#### Scenario: A failing verification preserves available reports
- **WHEN** tests or a later quality step fails after one or more reports have been generated
- **THEN** the available report files are still uploaded and the workflow remains failed

### Requirement: Eligible pull requests SHALL receive one combined test and coverage comment

For a pull request whose head repository is the current repository and which
was not created by Dependabot, CI SHALL publish one sticky comment containing a
test-result summary and a coverage summary. The comment SHALL include the
available test totals and outcomes and the available overall and changed-code
coverage values. Re-running CI for the same pull request SHALL update the
existing report instead of creating another report comment.

#### Scenario: An eligible pull request receives the combined report
- **WHEN** an eligible pull request completes CI with JUnit and JaCoCo report data
- **THEN** exactly one PR comment is updated or created with both test and coverage summaries

#### Scenario: Re-running CI does not create comment noise
- **WHEN** CI is re-run for an eligible pull request
- **THEN** the existing combined report comment is updated rather than duplicated

#### Scenario: Fork and Dependabot pull requests do not receive comments
- **WHEN** CI runs for a pull request from a fork or for a Dependabot-created pull request
- **THEN** CI does not attempt a PR comment write, while available reports remain subject to normal artifact retention

#### Scenario: Non-pull-request CI does not create a PR comment
- **WHEN** CI runs for a push, manual dispatch, or another non-pull-request event
- **THEN** CI does not create a pull request comment
