# Code-quality refactor design

## Context

The NuSynapxe clinic application currently has several maintainability risks that are
visible in the repository even though the existing functional test suite passes. The
largest risks are oversized JavaFX controllers, database work hidden in table cell
factories, repeated database queries in history and reporting paths, inconsistent
clinic-time handling, and synchronous database work on the JavaFX application thread.
The quality configuration also does not enforce the architecture and size constraints
that the codebase depends on.

This design turns those findings into separately reviewable changes. Each change will
be implemented and committed independently, with tests added before production code
for the behavior being changed.

## Goals

- Keep all existing clinic workflows, role boundaries, and privacy rules intact.
- Make appointment, clinical-history, billing, and checkout reads bounded by the
  requested data set rather than by the number of rows or dates displayed.
- Make all clinic-visible time calculations use the Singapore clinic timezone through
  an injectable clock.
- Keep blocking SQLite work off the JavaFX application thread while preserving the
  ordering required by the application's shared SQLite connection.
- Reduce the responsibilities and dependency surface of the largest UI classes.
- Make maintainability expectations executable through tests and build checks.
- Preserve deterministic, fast unit tests and provide focused UI regression coverage
  for the workflows affected by decomposition and asynchronous loading.
- Resolve every open review thread on the code-quality pull request with a verified
  code or test change.
- Keep every repository-owned Java source file at or below 500 physical lines.
- Make the Javadoc task warning-free rather than accepting a nonfatal warning budget.
- Increase the minimum JaCoCo branch-coverage requirement to 70 percent, supported by
  focused tests for asynchronous and filtering behavior.

## Non-goals and constraints

- This work does not change the clinic's domain rules, authorization model, or
  patient-data visibility rules.
- The existing SQLite database abstraction owns a shared connection. Database tasks
  therefore must not be changed to unconstrained parallel access; serialized database
  execution is the safe default.
- The clinic timezone remains `Asia/Singapore`. The change standardizes how it is
  supplied and tested; it does not introduce user-configurable timezones.
- Existing logs, teammate reflections, and unrelated documentation are outside the
  scope of this refactor.
- The active OpenSpec CLI is not available in this checkout and there is no active
  OpenSpec change to extend. The design and implementation commits will therefore be
  self-contained and will preserve the repository's existing OpenSpec artifacts.

## Architecture

### 1. Central clinic clock

Introduce one application clock abstraction backed by `java.time.Clock`, with the
Singapore zone as the production default. Services, repositories that write audit or
updated timestamps, and UI controllers that select today's date will receive the
clock through constructors. Existing constructors may remain as compatibility
overloads, but they must delegate to the production clinic clock rather than to the
host's default timezone.

Read-only operations that accept explicit dates should continue to use those dates.
Only implicit "now" behavior should consult the injected clock. Tests will use fixed
clocks, including a host-independent instant around Singapore midnight, to prove that
the displayed date and persisted timestamps do not vary with the machine timezone.

### 2. Appointment table read model

Add a repository/service-level appointment list projection containing the fields needed
by the appointment table, including patient and doctor display names. Its query will
join the relevant records and apply the existing filters in SQL. The UI will bind
columns to the projection and will not call patient or account services from cell
factories.

The projection is a read model, not a replacement for the domain appointment entity.
Mutations will continue to use the existing appointment services and authorization
checks. Missing display data will be represented deliberately by the read-model
mapping rather than by silently issuing a fallback query per cell.

### 3. Clinical-history prescription loading

Change clinical-history loading so that prescriptions for a page or result set are
loaded in one batch and grouped by clinical-record ID, or are explicitly loaded only
when a detail view requests them. The selected approach must preserve result order,
empty-prescription behavior, and the authorization boundary around clinical records.
The list query must not issue one prescription query for every returned record.

### 4. Revenue range query

Add a receipt repository query that accepts the report date range and payment-method
filter. Billing will call it once for the report instead of looping over every date
and loading that day's receipts. SQL will perform the date and method filtering, while
the service will retain the existing aggregation and report format.

### 5. Direct checkout receipt lookup

Make checkout return, or otherwise directly expose, the receipt created for the
appointment. The checkout screen will use that result for receipt display/export
instead of loading the complete receipt history and filtering it in memory. The
lookup will retain the current authorization and error behavior.

### 6. UI decomposition

Treat `ReceptionistView` and `DoctorView` as composition shells. Extract cohesive
feature components/services with narrow responsibilities:

- receptionist navigation and screen composition;
- appointment booking, rescheduling, and check-in;
- checkout and receipt presentation;
- revenue/report presentation;
- patient-directory composition;
- doctor consultation and patient-form composition; and
- shared CSV/JSON export behavior.

The exact class names may follow the existing package conventions, but each extracted
unit must have a single primary reason to change and must receive only the services it
uses. The shell remains responsible for wiring, lifecycle, and shared session state.
The decomposition must not duplicate authorization checks or move them into the UI.

### 7. Serialized background task runner

Introduce a small clinic task-runner abstraction that executes database-bound work on
a dedicated serialized executor and applies results back to the JavaFX thread. UI
controllers will inject the runner so unit tests can use a deterministic immediate
implementation. Production controllers will handle loading, success, failure, and
stale-result states explicitly; a later refresh must not be overwritten by an older
task.

The runner must be closed before the shared database connection is closed. It must not
allow UI callbacks to mutate controls from the worker thread. Existing `Platform.runLater`
calls that only defer database work to the JavaFX thread will be replaced or moved so
that they are used for UI application, not for making blocking work asynchronous.

### 8. Enforced quality gates

Strengthen the build in proportion to the repository's existing conventions:

- add meaningful Checkstyle/PMD rules for unused complexity and maintainability
  regressions without introducing a formatting-only rewrite;
- reassess test-source SpotBugs coverage and enable it where the build can support it;
- add JaCoCo verification thresholds based on the current measured baseline, with a
  small explicit margin for incremental improvement;
- add architecture tests or equivalent checks for UI/database boundaries, controller
  size, and forbidden database calls from table cell factories; and
- enforce a 500-physical-line maximum for every Java file under `src/main/java` and
  `src/test/java`, with a build failure that names each violating file;
- make the Javadoc task emit zero warnings, including missing parameter, return,
  type-parameter, record-component, and member documentation warnings; and
- raise the JaCoCo branch-coverage minimum from 65 percent to 70 percent after adding
  focused tests for the new asynchronous and report-state branches; and
- keep the checks runnable through the normal Gradle verification task.

The 500-line rule counts physical lines, including comments and Javadocs, so the
constraint cannot be satisfied by deleting documentation or compressing code. The
branch threshold is an explicit 0.70 `BRANCH` `COVEREDRATIO` limit. A failing gate
must identify the violated rule rather than relying on a reviewer to infer it from a
report. Javadoc warnings must be fixed at their source; blanket warning suppression
is out of scope.

### 9. Review-thread remediation

The eight unresolved pull-request threads will be handled as behavior-preserving
changes with one regression test or executable assertion for each distinct failure
mode:

- Doctor appointment actions capture the selected appointment ID and selection
  generation on the JavaFX thread. Actions are cleared or disabled while a new
  selection is loading, and stale completions cannot refresh another selection.
- Doctor consultation and prescription reloads apply only when their captured
  selection generation remains current.
- Doctor and Receptionist calendar range tasks receive immutable date snapshots
  captured before database work is submitted.
- Receptionist Doctor-selector loads use per-selector freshness state, and callers
  explicitly choose whether a selector may select its first result. Filter selectors
  retain the empty “All Doctors” state after loading.
- Appointment dialogs receive an owning workspace lifecycle guard. Logging out
  invalidates delayed loads and prevents an editor retaining the old session from
  being rendered.
- Revenue range SQL preserves ascending receipt-date order and descending sequence
  order within a date.
- Revenue export controls are disabled and the previous report is discarded while a
  new asynchronous report is pending.

The current consultation-generation guard will be tested before being changed; if
the existing implementation already satisfies that thread, the implementation
commit will add the missing regression evidence and the thread reply will identify
the existing guard rather than introduce duplicate logic.

### 10. Remaining god-file decomposition

The strict size rule applies to both production and test Java sources. The current
violations are split by responsibility, not by arbitrary line count:

- `PatientDirectoryView` becomes a composition shell over directory loading,
  patient editing, and result-table concerns.
- `DoctorWorkspace` delegates appointment-selection/action coordination while
  retaining workspace composition and lifecycle ownership.
- `AppointmentDialog` separates asynchronous editor loading from editor rendering
  and action wiring while preserving its compatibility facade.
- `AppointmentRepository` and `PatientRepository` delegate read/query-heavy work to
  focused repository collaborators while preserving service-facing APIs.
- `DemoDataSeeder` delegates account, patient, schedule, and clinical-history seed
  data to focused builders.
- `CalendarTimeGrid` delegates geometry/layout and interaction-node construction to
  focused helpers.
- `ReceptionistCheckoutPanel` separates checkout queue behavior from receipt-history
  presentation.
- `DoctorCalendarView` extracts toolbar/control composition where needed to keep the
  calendar controller below the limit.
- Oversized UI and service tests are split by workflow scenario, retaining shared
  fixtures in test helpers rather than duplicating setup in each class.

Each extracted unit has one primary reason to change, and the original public or
package-facing contracts remain stable unless a narrower dependency is required for
the lifecycle or testability fix.

## Data-flow and error handling

Database access remains behind repositories and services. Read projections and batch
queries are read-only and must not bypass session or role checks. Any background task
failure is converted to the existing user-facing error mechanism on the JavaFX thread,
with the underlying exception retained for logging. Empty results are valid states and
must not be treated as failures.

For asynchronous views, each load has a logical generation or equivalent guard. The
view applies only the current generation's result, and closing a view prevents further
UI callbacks. Writes refresh the relevant projection after successful completion so
the user sees committed state rather than an optimistic copy that can diverge.

## Testing strategy

Each implementation commit follows red-green-refactor:

1. Add a focused failing test or executable quality assertion for the behavior.
2. Make the smallest production change that satisfies it.
3. Refactor only after the test is green, then run the affected checks again.

Coverage will include:

- fixed-clock repository and UI tests around Singapore midnight and a non-Singapore
  host default timezone;
- repository query-count or SQL-shape tests for appointment, history, and revenue
  reads;
- direct checkout receipt behavior, including no-receipt and failure cases;
- deterministic task-runner tests for serialization, FX-thread callback application,
  failure handling, and stale-result suppression;
- regression tests for captured Doctor actions, calendar date snapshots, per-selector
  Doctor loading, logout-invalidated dialogs, receipt ordering, and pending report
  exports;
- focused TestFX regression tests for the decomposed receptionist and doctor flows;
- unit tests for extracted repository, seed-data, calendar-layout, and test-helper
  components where their behavior is not already covered by integration tests;
- architecture/size checks that fail when database calls return to cell factories or
  any Java source file grows beyond 500 physical lines; and
- a warning-free Javadoc run and a branch-coverage report that remains above the
  explicit 70 percent minimum; and
- the full Gradle quality gate before the pull request is created.

## Commit and issue boundaries

The implementation will use separate, reviewable commits for each item below. The
existing PR-linked issues will be referenced where they cover the work; creating new
issues is not required unless a genuinely separate scope is discovered.

1. one commit per unresolved review-thread behavior group, with its regression test;
2. one commit per remaining production god-file decomposition group;
3. one or more commits for splitting oversized test classes and extracting shared
   fixtures;
4. one commit for the 500-line source-size gate, Javadoc warning cleanup, added
   documentation tests/checks, and the 70 percent branch-coverage threshold.

Documentation changes that describe the completed work will be kept separate from
production refactors where practical. No commit will combine unrelated cleanup with a
behavioral change.

## Rollout order and risks

The safe order is clock and read-model foundations, then query and checkout changes,
then UI decomposition, then task-runner integration, and finally enforcement of the
quality gates. The task runner should be introduced before moving existing loads so
that asynchronous lifecycle behavior can be tested independently.

The main risks are accidental authorization changes during extraction, shared SQLite
connection misuse, stale asynchronous UI updates, and a quality threshold that blocks
the baseline without improving it. Each risk is addressed by preserving service
boundaries, serializing database work, injecting deterministic runners, adding
regression tests, and measuring the baseline before setting thresholds.
