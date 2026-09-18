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

The three examples below were selected because they demonstrate different
parts of Agentic Software Engineering: (1) using a specification workflow to
control requirements, (2) using a domain-focused agent for a complex UI
workflow that still needed human visual judgement, and (3) using automated
quality gates to detect regressions beyond the feature being changed.

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
correct answer on every first attempt. Its value was that a well-designed
process made the output inspectable, correctable, and progressively more
reliable.
