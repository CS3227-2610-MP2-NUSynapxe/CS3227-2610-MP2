# Reflections on AI-assisted Software Engineering

## Overview

I used a Codex GPT-Sol 5.6 agent throughout the development of NUSynapxe as a
requirements partner, implementation assistant, testing aid, and documentation
assistant. I did not treat its output as automatically correct. I reviewed its
design choices, inspected its code changes, and required tests or other
verification before accepting a task as complete.

NUSynapxe was developed by a team of two. I worked mainly on the Receptionist
workflows and shared project infrastructure, while John worked mainly on the
Doctor workflows. My contributions included the initial Java 25 and JavaFX
setup, SQLite persistence foundation, Gradle quality tooling, CI and release
automation, patient registration and maintenance, patient search, appointment
booking, check-in, checkout and receipts, revenue reports, and the Receptionist
calendar. We collaborated on architecture, shared UI behaviour, testing,
documentation, and integration.

I customized the agent through the responsibilities recorded in `AGENTS.md`.
The **OpenSpec Manager** handled requirements and design artifacts; the
**Implementation** role handled code and focused debugging; the
**Receptionist Domain** role supplied patient-workflow, JavaFX, and validation
context; the **Quality Gate** ran tests and static analysis; and the
**Documentation** role kept the guides, specifications, and logs aligned with
the product. I chose a responsibility according to the type of decision being
made rather than asking one unrestricted agent prompt to do everything.

The five examples below were selected because they demonstrate different
parts of Agentic Software Engineering: (1) using a specification workflow to
control requirements, (2) using a domain-focused agent for a complex UI
workflow that still needed human visual judgement, (3) using automated
quality gates to detect regressions beyond the feature being changed, (4)
using the agent for production build and release engineering, and (5)
correcting stale planning artifacts instead of trusting their apparent status.

---

## Example 1: Controlling Changing Requirements with OpenSpec

**Prompt and context.** For non-trivial Receptionist features, I asked the
agent to use the OpenSpec workflow before implementation. A change normally
produced a proposal explaining the problem and scope, requirements with
observable scenarios, a design describing architectural decisions, and a task
list connecting those decisions to code and tests. Only after reviewing these
artifacts did I use the implementation skill. I followed this process for the
patient directory, scheduling dashboard, check-in queue, checkout and receipts,
and revenue reports.

**Why I formulated the task this way.** These features affected several
layers: JavaFX controllers, services, repositories, SQLite migrations, tests,
and documentation. They also contained privacy decisions. A Receptionist could
manage administrative patient information but should not have access to
diagnoses, consultation notes, follow-up notes, or prescriptions outside the
limited checkout workflow. If I had asked the agent only to hide fields in the
UI, it could have left the same information accessible through the service
layer. Writing the rule as a specification and testable scenario made the
boundary explicit.

**What the agent assumed.** The agent could identify likely layers and propose
reasonable scenarios, but it did not know which clinic policies were correct
unless I stated them. For example, patient registration evolved to require
separate country-code and phone-number controls, Singapore as the issuing
country for NRIC and FIN, a separate read-only patient-details view, and
reversible deactivation. These were product decisions, not facts the agent
could safely infer.

**How the prompt evolved.** My initial requests described a feature at a high
level. After reviewing the proposed scenarios, I added the missing privacy,
validation, and state-transition rules. The agent then updated the proposal,
specification, design, and tasks before changing the service, persistence, and
UI layers. This was more reliable than repeatedly patching whichever screen
made a problem visible.

**Verification.** I checked that strict OpenSpec validation passed, that each
scenario had implementation and test evidence, and that the archived
specification matched the User Guide and actual application. I also reviewed
the resulting code instead of treating a valid specification as proof that the
implementation was correct.

**Engineering judgement required.** The agent helped expose dependencies and
translate decisions into consistent artifacts. However, I remained responsible
for the confidentiality boundary, the precise validation rules, and whether a
requirement belonged in the current scope. A complete-looking proposal could
still encode the wrong product decision.

**When prompting was less effective.** The full OpenSpec sequence created too
much overhead for small presentation fixes such as spacing or label changes.
For those changes, a narrow implementation request and targeted regression test
were faster and clearer than creating a proposal, design, specification, and
task list.

**What I would do differently.** I would create a requirement-to-test matrix
inside each substantial change from the beginning. This would make omissions
visible before implementation and reduce the effort needed during final
verification. The effective workflow was:

`problem -> scenarios -> engineering decision -> approved specification -> implementation -> verification`

not simply `feature request -> generated code`.

---

## Example 2: Building Searchable Receptionist Workflows and Correcting the UI

**Prompt and context.** I used the Receptionist Domain role to replace basic
CRUD-style screens with workflows suited to front-desk staff. One major task
was the patient directory: search had to match full names such as “Alden Tan”,
NRIC/FIN or passport, phone number, and email; results had to appear in a table
without internal patient or Doctor IDs; and the first action had to be
**View**, followed by separate **Edit**, **Deactivate**, and **Delete** actions.
Edit mode then needed **Save** and **Discard changes**.

I also asked the agent to replace ordinary selection dropdowns with searchable
patient and Doctor fields. Suggestions had to filter while typing, support
mouse selection and Up/Down/Enter keyboard navigation, and appear over the
surrounding interface instead of compressing the form. The component was then
reused in appointment booking, filters, checkout, receipts, revenue reports,
and the Receptionist calendar.

**Why I formulated the task this way.** The original controls technically
allowed users to select records, but they did not scale or match how a
Receptionist searches while speaking to a patient. I described the complete
interaction rather than requesting a generic “search bar” because filtering,
focus, keyboard behaviour, overlay placement, selection, and clearing are all
separate requirements.

**What the agent assumed.** The agent often converted visual descriptions such
as “same size” or “less squeezed” into fixed CSS widths. That did not account
for JavaFX layout rules, DatePicker editor padding, smaller window sizes, or
popup z-order. Some versions looked plausible in code but clipped dates,
compressed suggestion lists, or placed notifications below the page header
instead of at the top centre of the window.

**How the prompt evolved.** I moved from subjective wording to observable
criteria. I supplied screenshots and specified that dates must remain fully
visible, suggestions must overlay other controls, titles and subtitles need
clear spacing, booking dialogs must scroll, notifications must appear at the
top centre and fade after six seconds, and fields in a row must have consistent
dimensions. This gave the agent testable targets instead of asking it to make
the interface “nicer”.

**Verification.** I manually inspected the screens and used TestFX coverage for
full-name search, mouse and keyboard suggestion selection, equal control sizes,
calendar refresh after booking, inclusive calendar date ranges, and patient
state transitions. I also ran role-specific tests because shared controls and
CSS could affect the Doctor workspace as well as the Receptionist workspace.

**Engineering judgement required.** The agent was effective at applying a
reusable interaction pattern across many screens and connecting the UI to the
service and repository layers. It could not decide whether the resulting
hierarchy felt clear or whether spacing was appropriate at different window
sizes. Those decisions required human visual review.

**When prompting was less effective.** Broad batches of UI feedback caused the
agent to edit several shared components at once. This increased the regression
surface and made it difficult to identify which change caused a layout or
TestFX failure. Repeatedly rephrasing subjective feedback was less useful than
providing a screenshot and one measurable acceptance criterion at a time.

**What I would do differently.** I would prepare a UI acceptance checklist
covering minimum window size, hierarchy, spacing, keyboard navigation, popup
layering, scrollability, date visibility, and screenshots at common
resolutions. I would also ask the agent to list the affected screens before
editing shared CSS or controls. This would preserve the productivity benefit
without allowing a local UI change to spread unexpectedly.

---

## Example 3: Using Quality Gates to Catch Regressions

**Prompt and context.** I configured the Quality Gate role so that completion
meant more than successful compilation or one happy-path test. During
implementation, the agent first ran focused JUnit or TestFX tests. Once those
passed, it ran the broader Gradle verification pipeline, including Spotless,
Checkstyle, PMD for production and test code, SpotBugs with FindSecBugs, JaCoCo,
Javadoc checks, and architecture tests. CI repeated these checks on Java 25
under a virtual display and published test and coverage reports.

**Why I formulated the task this way.** A feature can work in isolation while
breaking an earlier workflow. Receptionist changes frequently crossed service,
database, and JavaFX boundaries, and reusable controls were shared between
roles. Focused tests gave fast feedback, while the full quality gate checked
behaviour and code outside the immediate request.

**What the agent did effectively.** It converted reported defects into
regression tests. Examples included full-name patient search, Singapore
NRIC/FIN issuance rules, phone normalization, blocked patient deletion,
suggestion-popup navigation, duplicate checkout prevention, receipt
persistence, automatic calendar refresh, and revenue filtering by dates,
patient, Doctor, and payment method. It also found non-functional issues such
as duplicate literals, formatting violations, and static-analysis warnings.

**Where the agent required correction.** TestFX was the most difficult area.
A test could pass alone but fail in the full suite because a popup, animation,
or lazily loaded schedule was not ready. The agent sometimes responded by
weakening an assertion, which could hide the requirement rather than solve the
timing problem. I reviewed test changes to ensure they removed timing
assumptions without removing the behaviour being tested.

**How the prompt evolved.** I stopped accepting summaries such as “all relevant
tests pass”. I required the agent to name the commands it ran, report each
failed quality tool, distinguish a reproducible defect from an environment
limitation, and avoid claiming completion while a required check was pending.
For asynchronous UI tests, I asked it to wait for observable state instead of
adding arbitrary delays.

**Verification.** I inspected the assertions, reran failing tests individually,
then reran the full suite to expose order-dependent behaviour. For release and
CI work, I also recorded what could not be verified locally, such as network,
GitHub authentication, or platform-specific packaging. A plausible
configuration file was not treated as proof that the external workflow ran.

**Engineering judgement required.** The agent made repetitive verification
routine and helped maintain coverage across many workflows. I still had to
decide whether a test genuinely proved the user-visible requirement, whether a
warning represented a real design problem, and whether a failure was caused by
the product, the test, or the environment.

**What I would do differently.** I would define a dedicated TestFX skill with
approved wait helpers and rules against arbitrary sleeps or weakened
assertions. It would distinguish waiting for a node, data, animation, or popup.
I would also require the agent’s final report to separate verified results from
external checks that remain outstanding.

---

## Example 4: Building Reproducible CI and Cross-platform Releases

**Prompt and context.** I used the agent to establish the shared engineering
foundation for NUSynapxe. This included Java 25 and JavaFX, the Gradle Wrapper,
SQLite JDBC, test and static-analysis dependencies, GitHub Actions CI,
Docusaurus documentation, Dependabot, and formal release automation. The
release workflow had to validate a version tag, run the appropriate quality
checks, build native packages on Windows, macOS, and Linux, and attach the
resulting `.msi`, `.dmg`, and `.deb` files to one GitHub release.

**Why I formulated the task this way.** Build and release configuration is
repetitive but exacting. A wrong plugin version, task dependency, artifact path,
Java module, or workflow permission can make an otherwise correct application
impossible to build or distribute. I therefore asked the agent to investigate
compatibility first, pin versions explicitly, and connect each release step to
an observable output instead of merely generating a collection of plausible
configuration files.

**What the agent assumed.** The agent initially treated locally plausible
configuration as stronger evidence than it was. A Gradle file can parse while
the GitHub runner still lacks a packaging tool, an artifact path can look
correct while referring to a directory created only on another operating
system, and a workflow can be syntactically valid while lacking the permissions
needed to publish a release. The agent also could not infer whether a network,
certificate, or authentication failure came from the project or the execution
environment without further evidence.

**How the prompt evolved.** The task began as project setup, then became a
sequence of narrower checks: confirm tool versions, create the Gradle quality
tasks, run them locally, reproduce them in CI, publish reports, build one native
package per operating system, and finally publish all three assets from a
version tag. When an integration problem occurred, I asked the agent to inspect
the failing command and actual report or artifact path rather than redesigning
the whole workflow.

**Verification.** I used the pinned Gradle Wrapper and Java 25 toolchain, ran
the local quality gate, checked that CI used the same commands, and inspected
the public workflow results and release assets. The successful release
contained the expected Windows, macOS, and Linux installers. The Docusaurus
production build and GitHub Pages deployment provided separate evidence for
the documentation pipeline. Where local verification was impossible, I
recorded the limitation instead of reporting the task as fully proven.

**Engineering judgement required.** The agent was useful for coordinating
versions, Gradle tasks, workflow YAML, and report locations, but I remained
responsible for deciding the release shape and the acceptable platform-specific
differences. For example, macOS could not run the TestFX suite in the same way
as Linux under a virtual display, so the workflow needed a deliberate platform
decision rather than blindly using identical commands everywhere.

**When prompting was less effective.** Repeated prompting did not fix external
certificate chains, unavailable network access, missing GitHub authentication,
or operating-system packaging prerequisites. In those situations, reading the
actual error and separating project failures from environment failures was
faster than asking the agent to keep proposing configuration changes.

**What I would do differently.** I would define a release acceptance matrix at
the start, listing the required operating systems, artifact names, bundled
runtime expectations, smoke tests, checksums, and publication evidence. I would
also add an automated launch smoke test for each packaged application where the
runner permits it. This would give the agent an exact definition of “release
ready” and make external verification gaps visible earlier.

---

## Example 5: Correcting Stale OpenSpec Artifacts Before Archival

**Prompt and context.** Near the end of the project, I asked the agent to audit
and archive completed OpenSpec changes. The Receptionist revenue-report feature
was implemented and tested, but its task file still showed `0/12` completed.
The Doctor dashboard/calendar change showed `37/37`, but it remained active
instead of archived. I asked the agent to reconcile the artifacts with the
actual implementation, validate them strictly, synchronize the main specs, and
archive only changes supported by evidence.

**Why I formulated the task this way.** Simply changing every unchecked box to
checked would have made the status look complete without proving anything. I
required a mapping from each task and requirement to code, tests,
documentation, or a successful verification command. This treated the
planning artifacts as an auditable engineering record rather than an
administrative checklist.

**What the agent discovered.** Strict validation found that the revenue delta
modified an existing checkout requirement but omitted two earlier scenarios:
duplicate checkout rejection and viewing a receipt without creating another
payment. Because a modified OpenSpec requirement replaces the whole block,
archiving it in that state would have silently removed valid behaviour from the
main specification. The audit also found stale main-spec language describing a
weekly Calendar picker, a visible first-day preference, Patient ID columns, and
an Edit first directory action, even though the implemented product used date
ranges, no first day selector, no internal ID columns, and a View-first flow.

**How the prompt evolved.** The task moved from “mark and archive” to a
verification sequence: read every proposal, design, delta spec, and task file;
find implementation and test evidence; preserve omitted scenarios; compare
each delta with its main capability; update obsolete contracts; validate all
specifications; and only then move the changes into the dated archive. During
that review, the agent also noticed that the export requirement promised
totals, breakdowns, and details, while the CSV and JSON output did not preserve
all of them. I corrected the implementation and regression test before treating
the change as complete.

**Verification.** The revenue change finished with `12/12` evidenced tasks and
the Doctor change with `37/37`. I checked that all twenty Doctor-change
requirements and their scenarios appeared in the corresponding main specs.
After synchronization and archival, OpenSpec reported no active changes and
all fifteen main specifications passed strict validation. I reran the focused
Receptionist tests and the complete Gradle quality gate after correcting the
exports.

**Engineering judgement required.** The agent could find textual differences,
but deciding which document represented the current product still required
judgement. Blindly synchronizing an older revenue delta would have reintroduced
a Patient ID requirement that a later privacy and UI decision had removed. I
had to preserve the approved current behaviour while retaining the historical
rationale in the archived change.

**When prompting was less effective.** Status numbers alone were misleading.
`0/12` did not mean the feature was absent, and `37/37` did not prove that the
main specifications were synchronized. Likewise, valid Markdown did not prove
that the export implementation satisfied the written behaviour. The agent
created extra work when it treated artifact completeness, semantic correctness,
and implementation evidence as the same thing.

**What I would do differently.** I would update task checkboxes immediately
after each verified increment and archive a change soon after it reaches a
stable release, rather than allowing implementation and planning artifacts to
drift. I would also automate a requirement-to-test evidence table and make
strict OpenSpec validation part of the pull-request quality gate. This would
turn final archival into a confirmation step rather than a late reconstruction
exercise.

---

## What I Would Do Differently Overall

The customized agent was most effective when it had a specific role, relevant
repository context, an explicit artifact to produce, objective verification
commands, and a clear stopping condition. It was less reliable when the task
depended on unstated clinic policy, subjective visual judgement, asynchronous
UI behaviour, or changes to shared components outside the immediate prompt.

If I repeated the project, I would separate exploration, decision,
implementation, and verification more deliberately. I would use a UI
acceptance checklist, a requirement-to-test matrix, and a dedicated TestFX
skill. Before changing shared CSS, controls, schemas, or authorization
services, I would require a short blast-radius assessment listing affected
roles and regression suites. I would also automate an interaction log after
each stable change so that the original request, corrections, commands,
limitations, and final result are consistently recorded.

Not every task benefited from additional prompting. Small syntax corrections,
straightforward layout adjustments, and external environment problems were
often faster to resolve by inspecting the code or error directly. Similarly,
a full specification workflow was unnecessary for an isolated visual fix. The
agent’s process should be proportional to the risk and scope of the task.

My main lesson is that designing an effective single AI agent is an exercise in
workflow and constraint design rather than prompt cleverness. AI increased my
productivity in repository exploration, multi-layer implementation, repetitive
testing, build configuration, and documentation synchronization. It did not
replace human responsibility for product decisions, privacy boundaries,
visual quality, scope control, or verification. The most reliable pattern was:

`human-defined intent -> agent-assisted planning and implementation -> automated evidence -> human review`

The value of Agentic Software Engineering was not that the agent produced the
correct answer on every first attempt. Its value was that a well designed
process made the output inspectable, correctable, and progressively more
reliable.
