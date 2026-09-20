# John's Reflection on Agentic Software Engineering

## Scope and perspective

For this reflection I am focusing on the Doctor and System Admin work that I
handled in NUSynapxe. I used Codex as one configurable AI software-engineering
agent rather than as an autonomous teammate. I changed its operating mode by
selecting an appropriate skill, giving it repository-specific constraints, and
checking its output. I remained responsible for deciding what the product
should do, which privacy and authorization boundaries were acceptable, whether
a visual result matched the intended workflow, and whether the available
verification evidence was strong enough.

The most useful combination was OpenSpec with Superpowers. OpenSpec gave
feature work a durable structure: proposal, design, specifications, tasks,
implementation, validation, synchronization, and archive. Superpowers gave
the agent a disciplined method for clarifying intent, choosing a process level,
planning implementation, debugging failures, and verifying completion. The
repository's `AGENTS.md` then described how to select a role such as OpenSpec
Manager, Implementation, Quality Gate, or Documentation. These descriptions
did not create separate agents. They made the same Codex agent behave
differently according to the risk and shape of the task.

I selected a skill set using the following decision rule:

| Task signal | Skill combination I used |
| --- | --- |
| The change crossed domain, persistence, authorization, UI, tests, and documentation | OpenSpec exploration/proposal, followed by implementation and archive skills |
| The requirement was ambiguous or had several architectural consequences | Superpowers brainstorming and an explicit design approval step |
| The work changed Doctor-owned data or appointment behaviour | Doctor-domain constraints, service-layer authorization, and focused domain/UI tests |
| The work changed JavaFX layout or interaction | JavaFX implementation guidance, TestFX checks, and visual evidence where needed |
| I was about to claim that a change was complete | Quality-gate and verification skills, with a distinction between local and remote evidence |

I did not select every skill for every task. That was an important part of
working effectively with one agent: the skill set had to be narrow enough to
focus the agent, but complete enough to cover the risk of the requested
change.

### Further reading on the two frameworks

Readers who want to inspect the skills and workflows directly can refer to the
upstream repositories:

- [OpenSpec on GitHub](https://github.com/Fission-AI/OpenSpec) — the
  specification-driven workflow and its CLI/tool integrations.
- [Superpowers on GitHub](https://github.com/obra/superpowers) — the
  composable skills and software-development methodology used for planning,
  debugging, testing, review, and verification.

These links are useful because a reflection can describe what a skill did in
one project, but the repository shows how the skill is intended to be reused.
They also make clear that OpenSpec and Superpowers are complementary rather
than competing substitutes. OpenSpec primarily organizes agreement about the
software change; Superpowers primarily organizes how the agent thinks and
works through the change.

## Skill 1: OpenSpec for feature decomposition and traceability

### What tasks was the agent customized to perform, and how did I choose this skill set?

I used the OpenSpec workflow for substantial changes to Doctor scheduling,
Doctor calendar and Schedule views, Doctor appointment workflows, Doctor
patient-directory behaviour, System Admin account management, and the wider
JavaFX foundation that supported those screens. These changes were not safely
described as “edit this controller.” They involved service methods,
authorization, persistence, projections, UI routing, tests, documentation, and
sometimes CI or demo-data tooling.

The appropriate skill set was therefore the OpenSpec Manager workflow rather
than direct implementation. The agent first had to understand the existing
architecture and the user outcome, then record the intended scope before
touching production code. A representative command-level outline was:

```text
$openspec-explore       # clarify the workflow and its boundaries
$openspec-propose       # create proposal, design, delta spec, and tasks
$openspec-apply-change  # implement the approved tasks
$openspec-archive-change # sync, validate, and archive completed work
```

The commands above are chat skills in this project, not commands to paste
blindly into PowerShell. That distinction itself became part of the operating
knowledge needed by the agent.

### How did I define this skill and make sure it was working?

I defined the skill as a lifecycle with required artifacts and stopping points.
The important rule was that an implementation task had to be traceable to an
approved proposal, design, specification, and task entry. The skill also had
to say how to finish: run strict change validation, validate synchronized main
specifications, inspect the archive, and confirm that no active change was
accidentally left behind.

I checked that the skill was working through concrete outputs rather than by
trusting the agent's summary. For the Doctor Calendar and Schedule work, the
logs recorded separate changes with 21/21 and 19/19 tasks complete. The
Schedule design included immutable cursor and page projections and a stable
`(starts_at, appointment_id)` ordering, which showed that the design had
captured a real pagination invariant rather than only a screen description.
The archive process preserved `.openspec.yaml`, proposals, designs, specs, and
tasks. `openspec validate --specs` and strict change validation supplied the
machine-checkable part of the evidence.

The most useful part of the skill was its ability to preserve decisions. For
example, Doctor calendar working hours and breaks were deliberately visual
preferences. They did not silently become new appointment-conflict rules.
Doctor ownership remained enforced by the service layer, while calendar
projections exposed only the administrative information required for the
Doctor's schedule. These decisions appeared in the artifacts and could be
checked after implementation.

### What did the agent handle effectively?

The agent was effective at decomposing a broad feature into cross-layer work.
It connected a Doctor calendar requirement to persistence, repository queries,
timezone calculations, pagination, service authorization, JavaFX projections,
and tests. It also kept documentation and verification tasks visible instead
of treating them as optional cleanup.

OpenSpec improved productivity because it gave the agent a finite queue of
work. It reduced the chance of implementing the visible weekly grid while
forgetting equal-timestamp pagination, cross-midnight display, persisted time
off, or the distinction between visual working intervals and booking rules.
It also improved reviewability: I could inspect the proposal or task list and
correct the direction before a large amount of code was written.

### Where did the agent require guidance or create additional work?

The agent still needed human decisions at the points where product intent was
not derivable from code. I had to clarify that the Doctor Dashboard should be
a current-day work view, that Calendar was the planning and availability view,
that working hours were visual-only, that a cross-midnight appointment needed
correct visible labels, and that no unnecessary work-location setting should
be introduced.

OpenSpec sometimes created additional work rather than reducing it. A small
change could require updating a proposal, design, delta specification, task
list, main specification, documentation, and archive state. When a requirement
changed late, keeping all artifacts consistent took longer than editing one
class. The agent also initially used an unsupported validation form in one
workflow; the repository-specific command was
`openspec validate "<change-name>" --type change --strict --no-interactive`.
That kind of tooling detail had to be corrected from actual CLI behaviour.

### What would I change in the instructions or skill set?

I would add a “change size” decision to the OpenSpec skill. A multi-layer
Doctor workflow should use the full artifact lifecycle, but a documentation
typo or a one-line styling fix should use a lighter path while still recording
the relevant verification. I would also add an evidence column to every task:

```text
- [ ] Implement duration-based calendar geometry
  Evidence: focused test / full Gradle gate / visual check / not yet verified
```

The archive skill should refuse to mark a task complete when its evidence is
only an agent assertion. A useful additional tool would compare requirement
names and scenarios between the delta spec and the synchronized main spec,
then report missing or renamed items before archive.

### What did this teach me about designing one effective agent?

OpenSpec taught me that a single agent needs memory in the form of artifacts,
not just a long conversation. The agent can reason across many files, but it
needs a stable representation of the intended outcome and the boundaries. A
single Codex instance can act as analyst, planner, implementer, and archivist
if each mode has explicit inputs, outputs, and a clear handoff. Without those
handoffs, it is too easy for the implementation mode to silently replace a
human-approved design with a locally convenient solution.

### Further insight: specifications became coordination memory

The deeper lesson was that OpenSpec did more than create documentation. It
became coordination memory between different moments of the same agent. A
long agent session can lose the reasoning behind a decision, especially after
several implementation and debugging turns. A proposal records why the
change exists; a design records how the parts fit together; a specification
records what must be true; and a task list records what remains to be done.

This separation also made disagreement easier to locate. If the code and the
specification disagreed, I could ask whether the requirement had changed, the
design was incomplete, or the implementation was wrong. Without that
separation, the agent could quietly rewrite the meaning of the requirement
while claiming that it was only refactoring. In that sense, OpenSpec acted as
a lightweight agreement protocol between me and Codex.

There was a trade-off. Specifications can become stale, and stale artifacts
are worse than no artifacts if they create false confidence. I therefore
learned that synchronization and archive validation are not administrative
steps. They are the point at which the project checks whether the written
agreement still describes the implementation. The process is valuable only
when the artifacts are actively reconciled rather than generated once and
forgotten.

## Skill 2: Superpowers brainstorming and implementation planning

### What tasks was the agent customized to perform, and how did I choose this skill set?

I used Superpowers when a request needed more than code generation: the agent
had to determine the level of process, expose assumptions, compare approaches,
and obtain approval before implementation. This was especially useful for the
ArchUnit architecture checks and for the Doctor Calendar/UI redesign work.
Both could have been implemented quickly in a narrow sense, but the wrong
interpretation would have changed package boundaries or workflow behaviour.

The Superpowers brainstorming skill classifies work before proceeding. The
central idea can be summarized as:

```text
Classify: spike / bounded / architectural
Understand intent -> propose design -> receive approval -> plan -> implement -> verify
```

For architectural work, the skill required a written design and then a
written implementation plan. For a bounded change, it required a short design
and approval before editing. This helped me decide whether the agent should
ask more questions, write a plan, or move directly to a focused fix.

### How did I define this skill and make sure it was working?

I defined the skill using hard gates rather than a vague instruction to “think
first.” The agent had to identify the purpose, constraints, non-goals, and
success criteria; present the proposed approach; and wait for approval at the
appropriate stage. The implementation plan also had to name files, interfaces,
commands, expected results, and failure handling.

The ArchUnit plan was a good test of the skill. It explicitly stated that
ArchUnit was test-only and pinned, that production classes should be imported
without test classes, and that the existing `ApplicationRouter` composition
root was an intentional exception. It divided the work into dependency,
architecture tests, documentation/OpenSpec alignment, and the full quality
gate. A representative plan excerpt was:

```text
- [ ] Add the pinned test-only dependency
- [ ] Write the focused architecture test
- [ ] Run the complete quality gate
- [ ] Validate OpenSpec and whitespace
```

I knew the skill worked when the resulting plan prevented unrelated refactoring
and when the rules were concrete enough to fail with an actionable dependency
violation. The plan's success condition was not “architecture improved”; it
was five named tests and a repeatable Gradle/OpenSpec verification sequence.

### What did the agent handle effectively?

The agent was good at turning broad ideas into bounded engineering decisions.
For architecture checks, it preserved the existing package organization and
made the intended direction executable instead of moving classes around. For
the JavaFX work, it separated presentation changes from persistence and
authorization changes. This helped keep a visual redesign from accidentally
becoming a workflow rewrite.

The planning skill improved productivity by making review cheaper. I could
review one design or plan before reviewing many changed files. It also improved
code quality because non-goals were written down: do not add a runtime
dependency for ArchUnit, do not force an unrelated package refactor, preserve
semantic TestFX IDs, and retain the composition-root exception.

### Where did the agent require guidance or create additional work?

The agent could generate a coherent plan even when the human decision was not
settled. I still had to decide which UI behaviour was desirable, whether a
layout change preserved the Doctor workflow, and which architecture exception
was legitimate. A plan did not remove the need for product judgement.

The approval gates also introduced extra time. There were moments when I knew
the likely implementation and still had to review the design before letting
the agent continue. That cost was worthwhile for architecture and workflow
changes, but excessive for a purely mechanical documentation edit. The agent
also needed correction when a later screenshot or interaction requirement
invalidated an earlier plan. The plan had to be revised rather than treated as
an authority greater than the new evidence.

### What would I change in the instructions or skill set?

I would add an explicit “assumption register” to every Superpowers plan. Each
assumption would be labelled as one of:

```text
confirmed by user | confirmed by source | proposed default | unresolved
```

The agent should stop before implementing an unresolved assumption that could
change privacy, authorization, persisted data, or user-visible workflow. I
would also add an automatic plan-to-diff check that reports files changed
outside the plan and asks whether the scope intentionally expanded.

An additional useful tool would be a lightweight architecture diagram or
package-dependency graph generated from the plan. For Doctor calendar changes,
a timeline visualizer showing source timestamps, Singapore-local display
times, intervals, and duration heights would have made some geometry decisions
easier to review than prose alone.

### What did this teach me about designing one effective agent?

Superpowers showed me that a single agent needs controlled transitions between
thinking and acting. The most important distinction was not between different
AI personalities; it was between “I am exploring a design,” “I am implementing
an approved design,” and “I am verifying a result.” An effective single agent
must know which transition it is allowed to make and what evidence permits the
next transition.

### Further insight: process should be proportional to risk

The most useful Superpowers idea was not that every task should follow the
longest possible workflow. It was that the workflow should be selected based
on risk. A spike is appropriate when the question is feasibility. A bounded
change is appropriate when the existing flow is understood and the edit is
small. An architectural change needs explicit alternatives, a written design,
and a plan because its consequences spread across the system.

This gave me a better definition of productivity. Productivity is not the
number of lines the agent produces per minute. It is the amount of useful,
reviewable progress produced per unit of human attention. A short design gate
can save more time than it costs if it prevents a wrong architecture from
being implemented. Conversely, forcing a full architecture process onto a
minor text correction consumes attention without reducing meaningful risk.

The plan also worked as an executable hypothesis. It stated what I believed
the repository should look like after the change and which commands could
falsify that belief. This is stronger than a checklist that only records
whether files were edited. It turns planning into a testable engineering
argument.

## Skill 3: Doctor-domain reasoning and authorization-aware implementation

### What tasks was the agent customized to perform, and how did I choose this skill set?

I used a Doctor-domain skill for work involving Doctor ownership, schedules,
appointments, time off, calendar projections, patient access, clinical
workflow actions, and Doctor-specific navigation. The key reason for selecting
this skill was that a Doctor screen is not merely a table or calendar. It has
ownership rules, privacy implications, temporal edge cases, and state
transitions that must be enforced below the UI.

The skill-selection question was: “Does this request change what a signed-in
Doctor may see, edit, schedule, or complete, or does it change how Doctor-owned
time is represented?” If yes, I routed the task through domain and service
authorization analysis before asking the agent to edit JavaFX code. If the task
was only visual, I still checked that semantic IDs, role boundaries, and
service calls remained unchanged.

### How did I define this skill and make sure it was working?

I defined the skill with invariants instead of only UI instructions. The
important invariants included:

- Doctor queries and settings are scoped to the signed-in Doctor.
- Authorization and ownership are enforced in services, not only by hiding UI
  controls.
- Calendar projections contain the administrative appointment information
  required for scheduling but do not load unnecessary clinical data.
- Calendar calculations use the Singapore clinic timezone.
- Working intervals and breaks affect presentation unless a separate approved
  requirement changes booking rules.
- Pagination is deterministic when appointments share a start time.
- Appointment block height represents duration rather than content length.

I checked the skill using a boundary-to-test mapping. For example, stable
`(starts_at, appointment_id)` keyset pagination was tested at page boundaries
and equal timestamps. Calendar geometry was checked for 30-minute and
60-minute appointments. Cross-midnight display was checked using the visible
start and end minute values. Authorization and service tests checked that a
Doctor could not operate on another Doctor's data.

The skill was working when a UI request caused the agent to inspect the
repository, service, and test layers rather than adding a screen-only guard.
It was also working when a visual request preserved the underlying workflow
rules. That distinction matters because a JavaFX control can look correct while
the service API remains unsafe or inconsistent.

### What did the agent handle effectively?

The agent handled cross-layer consistency particularly well once the Doctor
invariants were explicit. It implemented weekly Calendar and future Schedule
modes, Doctor-scoped queries, immutable page projections, date navigation,
persisted calendar settings, time-off projections, appointment actions, and
duration-based layout. It also reused `CalendarTimeGrid` for the one-day
Doctor Dashboard instead of introducing a second timeline renderer.

This improved productivity because repeated reasoning about dates, intervals,
and appointment ownership was centralized. It improved code quality because
the same `AppointmentService`, `AppointmentTransitions`, and calendar
calculation logic remained responsible for lifecycle and geometry decisions.
It improved testing efficiency because the agent could add focused tests at
the domain, repository, service, and UI levels instead of relying only on
large end-to-end tests.

### Where did the agent require guidance or create additional work?

The agent needed correction whenever a reasonable generic calendar design
conflicted with the clinic's intended semantics. I had to clarify that working
hours should be visual-only, that breaks should be shown without changing
booking rules, that the Doctor Dashboard and Calendar served different jobs,
and that a schedule should behave as an unbounded future stream rather than a
fixed set of preloaded dates.

Temporal work also created additional debugging. A calendar assertion can use
“today” as the first column even when the configured week starts earlier, and a
cross-midnight appointment can be stored in one form but displayed in another.
The agent needed targeted corrections to test assumptions and formatting. The
extra work was still valuable, but it showed that agent-generated date logic
must be challenged with boundary cases rather than accepted because ordinary
examples pass.

### What would I change in the instructions or skill set?

I would add a Doctor workflow state table to the skill. It should list each
operation, the allowed role and owner, the service method, the repository
projection, the UI entry point, and the regression test. I would also require
an explicit timezone policy in every temporal design and a table of edge cases:

```text
same start time | page boundary | empty day | cross-midnight | daylight/timezone boundary
```

Useful additional tools would include a deterministic clock fixture, a
timezone-aware appointment generator, and a visual calendar assertion helper
that reports the expected and actual minute-to-pixel conversion. Those tools
would reduce the amount of manual reasoning required for every calendar
revision.

### What did this teach me about designing one effective agent?

The Doctor work taught me that a single agent needs domain constraints before
it receives implementation freedom. Without the invariants, the agent tends
to optimize for the visible screen. With them, it can choose whether a rule
belongs in a repository query, a service authorization check, a transition
object, a calculation helper, or a JavaFX renderer. The goal is not to make
the agent memorize every class; it is to make the security and workflow
boundaries impossible to overlook.

### Further insight: domain skills expose hidden coupling

The Doctor features showed that domain skills are a form of dependency
analysis. A request such as “show the Doctor's schedule” appears to belong to
the UI, but it also depends on identity, ownership, date/time conversion,
pagination, repository ordering, and the shape of the projection returned by
the service. A request such as “make an appointment block represent one hour”
looks like a layout calculation, but it depends on how the system represents
intervals and whether the renderer is allowed to use content-driven sizing.

The skill helped the agent discover these hidden dependencies before making a
local change. It also helped me distinguish a policy from an implementation
detail. Singapore-local time was a policy for the clinic calendar. The choice
to store a cursor containing `starts_at` and `appointment_id` was an
implementation that made deterministic pagination possible. A strong skill
should preserve the policy while allowing the implementation to evolve.

Another insight was that privacy often depends on what is not loaded. A
calendar projection that excludes unnecessary clinical data is safer than a
large projection whose extra fields are merely hidden by the view. This made
repository and service design part of the Doctor UI skill, not a separate
database concern.

## Skill 4: System Admin JavaFX interaction and visual verification

### What tasks was the agent customized to perform, and how did I choose this skill set?

I selected a JavaFX UI and visual-verification skill for System Admin setup,
account creation, role selection, current-account presentation, shared
headers, login/setup cards, window sizing, feedback surfaces, and the visual
modernization of the desktop shell. I chose it when success depended on both
behaviour and presentation. A normal unit test could prove that an account was
created, but it could not prove that the role selector was readable, the table
was scannable, or that changing routes preserved the maximized window state.

The skill set combined implementation guidance, semantic TestFX assertions,
and temporary visual smoke checks. I did not treat pixel similarity as the
only definition of correctness. The more useful hierarchy was: service and
authorization behaviour first, semantic controls and labels second, then
visual inspection for layout defects that automated selectors might miss.

### How did I define this skill and make sure it was working?

I defined the skill around observable UI contracts. Examples included:

- the System Admin account list is a typed table with meaningful column
  headings rather than raw object-like text;
- the role selector displays readable values in both its closed cell and popup;
- semantic TestFX IDs and action labels remain stable;
- the feedback surface does not reserve a large empty area when no message is
  present;
- the application opens maximized but retains a usable restored size; and
- route changes do not silently reset the scene dimensions.

I made sure the skill worked by combining focused TestFX checks with visual
evidence. The System Admin test asserted the compact selector, its selected
value, table type, column count, headings, and refreshed account count. When a
screenshot showed clipped dropdown text, a temporary visual smoke test opened
the selector and captured both closed and popup states. After inspection, the
temporary test was deleted, while the semantic regression assertions remained.

### What did the agent handle effectively?

The agent was effective at applying a shared visual language across the login,
setup, Doctor, and System Admin surfaces. It introduced reusable UI helpers,
shared CSS, cards, headers, feedback states, status badges, and a consistent
scene layout. For System Admin specifically, it replaced raw account rows with
a `TableView<Account>`, added readable Username, Display Name, Role, and Status
columns, and used title-case role values with accessible contrast.

This improved productivity because UI changes were implemented through shared
components instead of repeated one-off styling. It improved code quality by
keeping service calls and semantic identifiers stable while changing
presentation. It improved testing efficiency because one focused System Admin
test could verify several high-value interaction contracts without relying on
fragile screen coordinates.

### Where did the agent require guidance or create additional work?

The agent needed concrete feedback for visual issues. An empty green feedback
area looked like an unfinished component even though the validation logic was
working. A maximized window was appropriate for the desktop workflow, but a
restored window was still needed. The selected role text was clipped in the
button cell even after the popup options were readable. These details were
easier for me to identify from screenshots and interaction than from source
inspection.

This created additional work because each visual symptom could expose a
different lifecycle cause. The window reset was caused by route changes
recreating scene sizing, not by a single CSS value. The selector issue was in
the selected button cell, not in the popup cells. A generic “make it look
better” instruction would not have produced these corrections reliably.

### What would I change in the instructions or skill set?

I would add a UI acceptance template that asks for the following for every
screen: initial state, empty state, validation state, success state, focus and
keyboard behaviour, resize/maximize behaviour, and route-transition behaviour.
The skill should also require a short statement distinguishing a temporary
visual probe from a permanent regression test.

Additional tools would include an automatic JavaFX screenshot harness with
stable synthetic credentials, a control-tree dump, and a layout diagnostic that
reports clipped text, minimum sizes, and unmanaged nodes. A keyboard-navigation
probe would also be useful because a visually correct JavaFX control is not
necessarily accessible or usable without a mouse.

### What did this teach me about designing one effective agent?

The System Admin work taught me that a single agent needs two feedback channels:
semantic tests for behaviour and visual evidence for presentation. Neither is
enough alone. The agent can write a passing test around a control that is still
clipped, and it can make a screenshot look attractive while weakening the
underlying workflow. The skill must keep the channels connected but must not
confuse them.

### Further insight: a UI is an interaction contract

The System Admin work changed how I think about JavaFX testing. A UI is not
just a collection of nodes with colours and spacing. It is an interaction
contract: what appears initially, what can receive focus, what text is
readable, what feedback appears after an action, what survives navigation, and
what the user can understand without inspecting the implementation.

This explains why visual verification was needed even after semantic TestFX
assertions passed. A test could find the role selector and confirm its value
while the selected text was vertically clipped. A test could confirm that a
route changed while missing the fact that the window silently returned to a
smaller size. Conversely, a screenshot could look good while the account
creation service or authorization rule was wrong. The most reliable approach
was therefore layered evidence: service tests for behaviour, TestFX for
semantic interaction, and a temporary screenshot probe for visual defects.

The temporary nature of the visual probe was also instructive. Not every
investigation should become permanent production code, but the finding should
become permanent knowledge or a regression assertion when the defect matters.
That distinction prevents the test suite from filling with one-off probes
while still preserving the value of exploratory verification.

## Skill 5: Evidence-driven quality gates and systematic debugging

### What tasks was the agent customized to perform, and how did I choose this skill set?

I selected the Quality Gate and systematic-debugging skills whenever the task
involved a failure, a regression, or a claim that work was complete. This
included Gradle tests, TestFX stability, Spotless, Checkstyle, PMD, SpotBugs
with FindSecBugs, JaCoCo, Javadoc, ArchUnit, strict OpenSpec validation,
documentation builds, and whitespace checks. It also included diagnosing
specific defects such as incorrect documentation routes, window-size resets,
selector clipping, calendar geometry, and cross-midnight display.

The appropriate skill was determined by uncertainty. If the cause was already
known and the change was mechanical, I used a focused implementation task. If
several causes were plausible, the agent had to use a debugging loop before
editing. If I was about to say “complete,” the verification skill required
fresh command output rather than relying on an earlier agent report.

### How did I define this skill and make sure it was working?

I defined the debugging loop as:

```text
Observe symptom -> isolate layer -> form hypothesis -> reproduce narrowly
-> make smallest fix -> run regression check -> run the relevant quality gate
```

I defined the quality gate with an evidence classification: focused test,
related suite, full local gate, visual check, static validation, or remote
verification. A passing local Gradle command could not be reported as proof of
a live pull-request comment or a remote documentation deployment.

I checked that the skill was working through concrete results. The Doctor
calendar work recorded focused tests, full Gradle gates, strict OpenSpec
validation, and archive checks. The quality process also recorded a 115-test
full gate for the later Doctor calendar work and identified existing Javadoc
warnings as non-failing. The Docusaurus route issue was diagnosed at the
broken-link stage after compilation had succeeded, so the agent corrected the
case-sensitive paths instead of disabling the check. This was a strong example
of using the failure location as evidence.

### What did the agent handle effectively?

The agent handled repetitive verification very effectively. It remembered to
run focused tests before broad checks, generated reports, checked whitespace,
validated OpenSpec artifacts, and kept documentation and quality gates in the
same completion story. It also converted several debugging observations into
durable tests: duration-based calendar height, equal timestamp pagination,
selector values, and scene/route behaviour.

This improved productivity because I did not need to manually remember every
quality tool after each feature. It improved code quality by catching issues
outside the immediate feature path. It improved testing efficiency by using a
focused test to narrow the failure before paying the cost of a full JavaFX or
Gradle run.

### Where did the agent require guidance or create additional work?

The agent could not independently verify everything. Offline dependency
resolution failed when the Gradle distribution or plugin marker was not
available. Some Node/npm and GitHub CLI checks were unavailable locally. The
correct response was to label those checks as unverified and obtain human
confirmation where appropriate; it would have been dangerous to convert a
missing report into a successful result.

There was also extra work from flaky or environment-sensitive tests. TestFX
input needed to be moved onto the JavaFX application thread. Calendar tests
needed stable timezone assumptions, including `ZoneId.of("Asia/Singapore")`
and a deterministic current date. Some UI fixes required a temporary visual
smoke test before the permanent test suite was rerun. These costs were real,
but they were preferable to claiming that an intermittent test was a product
failure or that a static check proved a live workflow.

### What would I change in the instructions or skill set?

I would add a preflight command that reports JDK, Gradle, Node, GitHub CLI,
display, dependency-cache, and timezone readiness before expensive checks. I
would also make the final verification report a table with four columns:

```text
Claim | Required command or observation | Result | Verification level
```

The agent should refuse phrases such as “all checks pass” when only a focused
test has run. It should say “the focused test passed; the full gate remains
unverified.” Additional tools that would help include a CI artifact parser, a
TestFX failure screenshot collector, a deterministic clock fixture, and an
IntelliJ debugger connector for scene ownership, JavaFX thread state, and
runtime values.

### What did this teach me about designing one effective agent?

The quality-gate work taught me that verification is part of the agent's
identity, not a final optional phase. An agent that writes code but cannot
explain exactly what was tested is only partially useful. A single agent can
implement and verify its own work, but it needs an enforced separation between
the implementation claim and the evidence review. Human judgement is still
needed for unavailable remote checks, visual acceptance, and deciding whether
a warning is acceptable.

### Further insight: verification calibrates confidence

I learned to treat verification as confidence calibration rather than a
binary pass/fail ritual. A focused unit test gives confidence about one rule.
A service test gives confidence about a boundary. A JavaFX test gives
confidence about a semantic interaction. A full Gradle gate gives confidence
that the repository remains compatible with its quality tools. An OpenSpec
validation gives confidence that the artifacts are structurally coherent. A
live CI or visual check gives a different kind of confidence again.

These forms of evidence are not interchangeable. A passing calendar unit test
does not prove that a maximized JavaFX scene remains stable after a route
change. A local CI YAML assertion does not prove that a real pull-request
comment rendered correctly. A successful compilation does not prove that the
user can understand a clipped selector. The agent became more trustworthy
when its report named the exact confidence level instead of compressing all
evidence into “tests passed.”

This also changed how I viewed failures. A failed command caused by a missing
dependency, display, or authentication token is not the same as a failed
product test. The agent should diagnose the verification environment first,
preserve the failure as a limitation, and avoid weakening the product checks
just to obtain a green result.

## Broader lessons and future improvements

### What worked best overall?

The strongest pattern was the combination of explicit scope, reusable skills,
and evidence. OpenSpec kept the feature intent stable. Superpowers slowed the
agent down at the right moments to expose assumptions. Domain instructions
protected Doctor ownership and workflow semantics. JavaFX-specific checks
connected behaviour with presentation. Quality gates made the final claims
auditable.

The agent was especially productive when the task was well-bounded but
cross-layer: it could search many files, identify repeated patterns, update
tests and documentation together, and run a large set of mechanical checks.
It was less reliable when the task depended on an unstated human preference,
an ambiguous domain policy, a visual judgement, or an unavailable external
system.

### What additional skills or tools would make Codex more useful?

If I repeated the project, I would add the following capabilities:

- a Doctor workflow matrix linking roles, ownership, service methods,
  projections, UI actions, and tests;
- a JavaFX visual harness for deterministic screenshots and control-tree
  inspection;
- a timezone and deterministic-clock testing toolkit;
- a plan-to-diff scope checker for OpenSpec and Superpowers plans;
- a CI artifact and remote-check status parser; and
- a debugger connector for JavaFX thread, scene, selection, and runtime-value
  inspection.

I would also improve the instructions by stating the edit boundary at the top
of every task, listing non-goals beside goals, and requiring the agent to say
which claims are confirmed, inferred, or still unverified.

### Additional insights from the project

Several broader lessons became clearer as the project grew:

1. **The most valuable customization was procedural.** The agent already knew
   many Java and JavaFX APIs. The greater improvement came from telling it
   when to stop, what to preserve, how to ask for clarification, and what
   evidence was required. A procedure can be reviewed and improved like code.

2. **Non-goals are an active engineering tool.** Statements such as “working
   hours are visual-only,” “keep ArchUnit test-only,” and “do not change the
   existing package structure” prevented scope drift. For an AI agent, a
   non-goal is not passive documentation; it blocks attractive but incorrect
   solutions.

3. **Human corrections are requirements discovery.** Corrections about
   calendar semantics, route sizing, selector readability, or test timing were
   not merely bug fixes. They revealed requirements that were not fully
   represented in the first prompt. The right response was to update the
   relevant skill or artifact, not just patch the current line of code.

4. **Reusable constraints are better than repeated warnings.** If Doctor
   ownership, Singapore timezone, service-layer authorization, and semantic
   TestFX IDs matter, they should appear in a skill or project instruction.
   Repeating them in every prompt is less reliable and makes the agent's
   behaviour depend too much on what fits in the current conversation.

5. **A single agent benefits from internal role separation.** The agent did
   not need five separate personalities, but it did need separate checklists
   for analysis, implementation, debugging, and verification. Role separation
   reduced the risk that the same agent would grade its own untested assumption
   as a completed requirement.

6. **The best future tool is not necessarily a stronger code generator.** A
   debugger connector, deterministic clock, JavaFX screenshot harness,
   plan-to-diff checker, and CI artifact parser would reduce uncertainty more
   than another autocomplete feature. The bottleneck was usually evidence and
   coordination, not typing speed.

### What did I learn about designing an effective single AI agent?

My central conclusion is that a single agent can perform several engineering
roles, but it cannot safely perform them all under one vague instruction. It
needs explicit modes such as explore, design, implement, debug, and verify.
Each mode needs its own stopping condition and evidence standard.

I also learned that a skill is more than a prompt or a list of technologies.
A good skill defines when it applies, what it may change, what it must not
change, which human decisions it must request, what artifacts it produces,
and how its output is validated. The most effective skill instructions made
the agent slower before risky changes and faster during repetitive work.

Finally, the human-agent relationship is best understood as a feedback loop.
Codex expanded my ability to search, reason across layers, draft code, produce
tests, and repeat quality checks. I supplied product intent, domain judgement,
visual acceptance, correction, and scepticism about incomplete evidence. The
goal of Agentic Software Engineering is therefore not to remove the human
from the process. It is to design the agent so that its speed is coupled to
clear constraints, inspectable artifacts, and honest verification.
