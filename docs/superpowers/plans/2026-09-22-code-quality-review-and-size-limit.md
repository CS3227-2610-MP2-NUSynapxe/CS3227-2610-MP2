# Code-quality review remediation and 500-line source policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all eight open PR review threads, reduce every Java source file to at most 500 physical lines, eliminate all Javadoc warnings, and raise the enforced JaCoCo branch minimum to 70 percent without changing clinic behavior or authorization boundaries.

**Architecture:** Preserve the existing service and repository APIs while extracting focused UI, query, seed-data, and test collaborators. Asynchronous UI operations will capture immutable inputs before submission and use explicit selection or workspace lifecycle generations before applying callbacks. The build will enforce the source-size, Javadoc, static-analysis, and coverage rules through the normal Gradle `check` task.

**Tech Stack:** Java 25, JavaFX 25.0.4, Gradle Wrapper, SQLite, JUnit 6, TestFX, ArchUnit, JaCoCo, Checkstyle, PMD, SpotBugs, Spotless, and GitHub CLI.

**Spec:** `docs/superpowers/specs/2026-09-22-code-quality-refactor-design.md`

## Global Constraints

- Preserve existing clinic workflows, role boundaries, privacy rules, and service authorization checks.
- Keep the shared SQLite connection behind serialized clinic task execution; do not introduce unconstrained parallel database access.
- Use `Asia/Singapore` for clinic-visible implicit time and retain injectable clocks in tests.
- Count physical lines, including comments, blank lines, and Javadocs; every Java file under `src/main/java` and `src/test/java` must contain at most 500 lines.
- The JaCoCo `BRANCH` `COVEREDRATIO` minimum must be `0.70` or higher after the new tests pass.
- The Gradle `javadoc` task must complete with zero warnings; warnings must be fixed at their source, not suppressed globally.
- Every behavior change follows red-green-refactor, with a failing focused test observed before production code is changed.
- Keep review-thread fixes, decompositions, test-source splits, and build/documentation changes in separate commits.

## Review Focus

- A queued Doctor action must update the appointment selected when the button was pressed, not a later selection; test a selection change while the task is pending.
- A clinical or prescription reload completing after a selection change must not overwrite the newly selected form; test both success and failure/stale paths.
- Doctor and Receptionist calendar workers must use the exact date values captured before submission; test control mutation after queuing a task.
- Concurrent Doctor-selector loads must update every target selector independently; filter selectors must remain empty when “All Doctors” is intended.
- A delayed appointment dialog must not render after its owning workspace logs out; test lifecycle invalidation before the load callback.
- Revenue receipts must remain in ascending date order with descending sequence numbers per date; test a multi-day result.
- Revenue export controls must not export the previous report while a new report is pending; test disabled controls and cleared state.
- Any future Java source over 500 physical lines must fail with the file path and measured count; test the gate against the real source tree.

---

### Task 1: Add deterministic asynchronous test support and guard Doctor selection actions

**Files:**
- Create: `src/test/java/nusynapxe/ui/QueuedClinicTaskRunner.java`
- Modify: `src/main/java/nusynapxe/ui/DoctorWorkspace.java`
- Modify: `src/main/java/nusynapxe/ui/DoctorConsultationPanel.java`
- Test: Create `src/test/java/nusynapxe/ui/DoctorWorkspaceAsyncTest.java`

**Interfaces:**
- `QueuedClinicTaskRunner` implements `ClinicTaskRunner`, stores submitted tasks and their success/failure callbacks, and exposes `runNext()` plus `pendingCount()` for deterministic tests.
- `DoctorWorkspace` captures `appointmentId` and `selection.generation` on the JavaFX thread before each appointment action and applies success only when that generation is still current.
- `DoctorConsultationPanel.load` continues to accept the captured generation and applies clinical data only when `selectionGeneration.getAsLong()` still matches.

- [ ] **Step 1: Write the failing Doctor action test.** Select appointment A, queue the Accept/Decline/Check-in/Complete operation, select appointment B before running the queued task, then execute the task and assert that only appointment A can be mutated and that the stale success does not refresh B. Add a second test that starts a new selection and asserts the old action controls are disabled or hidden while the load is pending.

- [ ] **Step 2: Run the focused test to verify it fails for the current mutable-selection implementation.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.DoctorWorkspaceAsyncTest --offline --no-daemon --console=plain
  ```

  Expected result: failure showing that the worker reads the later `selection.appointmentId` or that old controls remain active.

- [ ] **Step 3: Implement the smallest guard.** Add one helper in `DoctorWorkspace` that accepts a captured ID, generation, operation, and success message; clear the selected appointment/action controls when a new dashboard selection begins; pass the captured ID into the worker; and reject stale success callbacks. Keep clinical reload generation checks in `DoctorConsultationPanel` and do not duplicate service authorization in the UI.

- [ ] **Step 4: Run the focused Doctor tests and the existing Doctor UI tests.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.DoctorWorkspaceAsyncTest --tests nusynapxe.ui.DoctorViewTest --offline --no-daemon --console=plain
  ```

  Expected result: all selected tests pass.

- [ ] **Step 5: Commit.**

  ```powershell
  git add src/main/java/nusynapxe/ui/DoctorWorkspace.java src/main/java/nusynapxe/ui/DoctorConsultationPanel.java src/test/java/nusynapxe/ui
  git commit -m "fix: guard doctor actions against stale selections"
  ```

### Task 2: Snapshot calendar inputs and isolate Receptionist selector generations

**Files:**
- Modify: `src/main/java/nusynapxe/ui/DoctorCalendarView.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistCalendarView.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistDataLoader.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistAppointmentPanel.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistCheckoutPanel.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistRevenuePanel.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistWorkspace.java`
- Test: `src/test/java/nusynapxe/ui/DoctorCalendarViewTest.java`
- Test: `src/test/java/nusynapxe/ui/ReceptionistViewTest.java`
- Test: Create `src/test/java/nusynapxe/ui/ReceptionistDataLoaderTest.java`

**Interfaces:**
- `DoctorCalendarView.refresh()` and `ReceptionistCalendarView.refresh()` capture `LocalDate fromDate` and `LocalDate toDate` before calling `taskRunner.submit`; the task consumes only those locals.
- `ReceptionistDataLoader.refreshDoctors(SearchSuggestionField<Account>, Label, boolean selectFirst)` owns freshness per selector and selects the first Doctor only when `selectFirst` is true.
- Appointment booking passes `true`; calendar, queue, checkout, receipt, and revenue filters pass `false` and explicitly retain the empty filter selection.

- [ ] **Step 1: Add failing snapshot and selector-generation tests.** Queue a calendar refresh, mutate the DatePicker controls, run the worker, and assert the service receives the original dates. Start Doctor loads for two selectors, complete them independently, and assert both receive results. Assert that a filter selector remains `null` after loading while the booking selector chooses its first Doctor.

- [ ] **Step 2: Run the focused tests and observe the expected failures.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.DoctorCalendarViewTest --tests nusynapxe.ui.ReceptionistDataLoaderTest --tests nusynapxe.ui.ReceptionistViewTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 3: Implement immutable calendar snapshots and per-selector freshness.** Add local date variables before submission, change the worker lambdas to use them, replace the shared Doctor generation with an identity-keyed or selector-owned generation map, and move `clearSelection()` into the intended callback/selection policy instead of racing the load callback.

- [ ] **Step 4: Run focused tests, then the complete existing UI test classes.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.DoctorCalendarViewTest --tests nusynapxe.ui.ReceptionistDataLoaderTest --tests nusynapxe.ui.ReceptionistViewTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 5: Commit.**

  ```powershell
  git add src/main/java/nusynapxe/ui src/test/java/nusynapxe/ui
  git commit -m "fix: snapshot calendar and selector inputs"
  ```

### Task 3: Invalidate delayed appointment dialogs on workspace logout

**Files:**
- Create: `src/main/java/nusynapxe/ui/WorkspaceLifecycle.java`
- Modify: `src/main/java/nusynapxe/ui/AppointmentDialog.java`
- Modify: `src/main/java/nusynapxe/ui/DoctorWorkspace.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistWorkspace.java`
- Test: `src/test/java/nusynapxe/ui/AppointmentDialogLifecycleTest.java`

**Interfaces:**
- `WorkspaceLifecycle` exposes `BooleanSupplier isActive()` and `void invalidate()`; it is active at construction and permanently inactive after invalidation.
- Appointment-dialog entry points accept an owner-active supplier in the task-aware overloads. Existing compatibility overloads delegate to an always-active supplier for deterministic tests.
- Every Doctor and Receptionist workspace dialog callback passes its workspace lifecycle supplier and invalidates it before invoking `onLogout`.

- [ ] **Step 1: Add a failing lifecycle test.** Start an appointment editor load with a queued runner, invalidate the owning lifecycle, run the queued callback, and assert that no editor stage is created and no workspace feedback is mutated.

- [ ] **Step 2: Run the focused test and observe the current delayed callback failure.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.AppointmentDialogLifecycleTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 3: Implement lifecycle composition.** Combine the owner-active check with the existing per-dialog generation and `dialog.isShowing()` checks before rendering, failure feedback, and every delayed action callback. Pass the lifecycle supplier through Doctor and Receptionist calendar/appointment callbacks.

- [ ] **Step 4: Run dialog, Doctor, Receptionist, and calendar UI tests.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.AppointmentDialogLifecycleTest --tests nusynapxe.ui.DoctorViewTest --tests nusynapxe.ui.ReceptionistViewTest --tests nusynapxe.ui.DoctorCalendarViewTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 5: Commit.**

  ```powershell
  git add src/main/java/nusynapxe/ui src/test/java/nusynapxe/ui/AppointmentDialogLifecycleTest.java
  git commit -m "fix: invalidate appointment dialogs on logout"
  ```

### Task 4: Preserve receipt ordering and make revenue exports pending-safe

**Files:**
- Modify: `src/main/java/nusynapxe/persistence/ReceiptRepository.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistRevenuePanel.java`
- Test: `src/test/java/nusynapxe/service/BillingServiceTest.java`
- Test: `src/test/java/nusynapxe/ui/ReceptionistViewTest.java`

**Interfaces:**
- `ReceiptRepository.FIND_RANGE_QUERY` orders `r.receipt_date ASC, r.sequence_number DESC`.
- `ReceptionistRevenuePanel` stores export button references, clears `currentReport` and disables both export buttons before submitting a report, and enables them only after the matching success callback installs a report.
- `export(boolean)` returns without opening an export when no completed report is available.

- [ ] **Step 1: Add failing tests.** Insert receipts on at least two dates and assert the range result is ascending by date and descending by sequence within each date. In a TestFX scenario, generate a report with a queued task, assert both export buttons are disabled and the previous report is not exportable, then complete the task and assert they become enabled.

- [ ] **Step 2: Run the focused tests and observe the ordering/pending-state failures.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.service.BillingServiceTest --tests nusynapxe.ui.ReceptionistViewTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 3: Make the SQL and UI state changes.** Update the `ORDER BY`, revise the repository Javadoc return order, add pending state transitions around `dataLoader.revenueReport`, and guard both export paths against a null current report.

- [ ] **Step 4: Run billing and Receptionist tests, then inspect the generated report contents.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.service.BillingServiceTest --tests nusynapxe.ui.ReceptionistViewTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 5: Commit.**

  ```powershell
  git add src/main/java/nusynapxe/persistence/ReceiptRepository.java src/main/java/nusynapxe/ui/ReceptionistRevenuePanel.java src/test/java/nusynapxe/service/BillingServiceTest.java src/test/java/nusynapxe/ui/ReceptionistViewTest.java
  git commit -m "fix: preserve receipt order and pending report state"
  ```

### Task 5: Decompose remaining oversized UI controllers

**Files:**
- Modify/create under `src/main/java/nusynapxe/ui/`: `PatientDirectoryView.java`, `PatientDirectoryFormView.java`, `PatientDirectoryTableView.java`, `DoctorWorkspace.java`, `DoctorAppointmentActions.java`, `AppointmentDialog.java`, `AppointmentEditorView.java`, `AppointmentDialogLoader.java`, `CalendarTimeGrid.java`, `CalendarTimeGridLayout.java`, `DoctorCalendarView.java`, `DoctorCalendarToolbar.java`, `ReceptionistCheckoutPanel.java`, and `ReceptionistReceiptPanel.java`.
- Test: `src/test/java/nusynapxe/ui/DoctorViewTest.java`
- Test: `src/test/java/nusynapxe/ui/DoctorCalendarViewTest.java`
- Test: `src/test/java/nusynapxe/ui/ReceptionistViewTest.java`

**Interfaces:**
- Existing public view factories and test-facing node IDs remain unchanged.
- Extracted components receive only the services, session, clock, runner, feedback label, and callbacks they use.
- `DoctorWorkspace`, `AppointmentDialog`, and the two receptionist panels retain small composition facades; feature behavior moves to focused collaborators.

- [ ] **Step 1: Add executable size assertions for the current UI files and record the failing list.** Extend `ArchitectureTest` with one helper that scans `src/main/java` and asserts a 500-line maximum for the files being decomposed. Run the test and preserve the failure output as the red baseline.

- [ ] **Step 2: Extract Patient Directory responsibilities.** Move patient table construction/row actions and registration/edit form construction into focused package-private classes. Keep `PatientDirectoryView` responsible for navigation, selection, shared feedback, and lifecycle callbacks. Run the existing Receptionist and Doctor UI tests after each extraction.

- [ ] **Step 3: Extract Doctor appointment actions and selection rendering.** Move action-button handlers, captured-selection operations, and selected-pane rendering out of `DoctorWorkspace`; leave dashboard/calendar/patient-page composition in the workspace. Re-run the Doctor async and UI tests.

- [ ] **Step 4: Extract appointment-dialog loading/rendering.** Move editor control construction and asynchronous editor-data loading into `AppointmentEditorView` and `AppointmentDialogLoader`; keep static compatibility entry points in `AppointmentDialog` and route lifecycle guards through the new collaborators. Re-run dialog and appointment workflow tests.

- [ ] **Step 5: Extract calendar layout and toolbar composition.** Move pure grid geometry/layout-node assembly into `CalendarTimeGridLayout` and toolbar construction into `DoctorCalendarToolbar`. Preserve `CalendarTimeGrid.InteractionHandlers`, node IDs, clipping, and current-time behavior. Re-run all calendar tests.

- [ ] **Step 6: Split checkout queue and receipt history.** Move receipt filters, receipt table, and preview/export-facing presentation into `ReceptionistReceiptPanel`; retain queue/check-in state in `ReceptionistCheckoutPanel`. Re-run the Receptionist checkout and revenue tests.

- [ ] **Step 7: Run the source-size assertions and the full focused UI suite.** Every production Java file must be at most 500 physical lines before committing.

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.architecture.ArchitectureTest --tests nusynapxe.ui.DoctorViewTest --tests nusynapxe.ui.DoctorCalendarViewTest --tests nusynapxe.ui.ReceptionistViewTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 8: Commit.**

  ```powershell
  git add src/main/java/nusynapxe/ui src/test/java/nusynapxe/architecture/ArchitectureTest.java src/test/java/nusynapxe/ui
  git commit -m "refactor: split remaining oversized ui controllers"
  ```

### Task 6: Decompose oversized persistence and demo-data classes

**Files:**
- Modify/create under `src/main/java/nusynapxe/persistence/`: `AppointmentRepository.java`, `AppointmentQueryRepository.java`, `PatientRepository.java`, and `PatientQueryRepository.java`.
- Modify/create under `src/main/java/nusynapxe/tools/`: `DemoDataSeeder.java`, `DemoDataAccountSeeder.java`, `DemoDataPatientSeeder.java`, `DemoDataScheduleSeeder.java`, and `DemoDataClinicalSeeder.java`.
- Test: `src/test/java/nusynapxe/persistence/AppointmentRepositoryScheduleTest.java`
- Test: `src/test/java/nusynapxe/persistence/PatientDirectoryRepositoryTest.java`
- Test: `src/test/java/nusynapxe/tools/DemoDataSeederTest.java`

**Interfaces:**
- `AppointmentRepository` and `PatientRepository` remain the service-facing entry points; read/query collaborators are private or package-private delegates behind those facades.
- `DemoDataSeeder.seed` and `DemoDataSeeder.reset` retain their existing signatures and transaction behavior; domain-specific seeders receive the active connection and required IDs explicitly.

- [ ] **Step 1: Add characterization tests for facade behavior.** Assert that appointment search/projection, patient search/update/delete, demo seeding, and reset produce the same rows and authorization-visible results before extraction.

- [ ] **Step 2: Run the characterization tests and verify the baseline is green.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.persistence.AppointmentRepositoryScheduleTest --tests nusynapxe.persistence.PatientDirectoryRepositoryTest --tests nusynapxe.tools.DemoDataSeederTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 3: Move read/query SQL and row mapping into focused collaborators.** Keep transaction boundaries, prepared-statement parameter order, projection ordering, and `Optional` receipt semantics unchanged.

- [ ] **Step 4: Move demo-data domain blocks into seeders.** The orchestrator controls reset/seed order and transaction rollback; account, patient, schedule, and clinical seeders do not open or close the database independently.

- [ ] **Step 5: Run persistence, service, integration, and architecture tests.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.persistence.* --tests nusynapxe.service.* --tests nusynapxe.architecture.ArchitectureTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 6: Confirm every new and existing production file is at most 500 lines, then commit.**

  ```powershell
  git add src/main/java/nusynapxe/persistence src/main/java/nusynapxe/tools src/test/java/nusynapxe/persistence src/test/java/nusynapxe/tools
  git commit -m "refactor: split oversized persistence and seed classes"
  ```

### Task 7: Split oversized test classes and centralize shared fixtures

**Files:**
- Split `src/test/java/nusynapxe/ui/ReceptionistViewTest.java` into focused Receptionist workflow classes, each at most 500 lines.
- Split `src/test/java/nusynapxe/ui/DoctorCalendarViewTest.java` into focused calendar navigation, appointment, and time-off classes, each at most 500 lines.
- Split `src/test/java/nusynapxe/ui/DoctorViewTest.java` into focused dashboard/clinical, patient-directory, and layout classes, each at most 500 lines.
- Split `src/test/java/nusynapxe/service/PatientServiceTest.java` into identity/validation, administrative maintenance, and deletion classes, each at most 500 lines.
- Create or modify focused fixtures under `src/test/java/nusynapxe/testsupport/` only when setup is shared by at least two classes.

**Interfaces:**
- Test names and assertions remain behaviorally identical; only class ownership and shared setup change.
- Shared fixture helpers expose explicit database/session construction and do not hide assertions or silently swallow failures.

- [ ] **Step 1: Move one coherent group of tests at a time without changing assertions.** Keep the original test method bodies intact during each move and update only class-level lifecycle/setup references.

- [ ] **Step 2: Run each moved class immediately.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.ui.* --tests nusynapxe.service.* --offline --no-daemon --console=plain
  ```

- [ ] **Step 3: Extract only repeated setup that is identical across at least two classes.** Keep scenario-specific setup in the owning test so failures remain local and readable.

- [ ] **Step 4: Count every test source file and verify no file exceeds 500 physical lines.**

- [ ] **Step 5: Commit.**

  ```powershell
  git add src/test/java
  git commit -m "test: split oversized workflow test classes"
  ```

### Task 8: Make Javadoc warning-free

**Files:**
- Modify all warning-producing public/package members reported by `javadoc`, including `AppointmentListRow.java`, `ClinicClock.java`, `BillingService.java`, `ClinicTaskRunner.java`, `ApplicationRouter.java`, `DoctorView.java`, `LoginView.java`, `ReceptionistView.java`, `ReportExporter.java`, `SetupView.java`, and `SystemAdminView.java`.

**Interfaces:**
- Public and package-visible APIs gain accurate `@param`, `@return`, `@throws`, `@param <T>`, record-component, constructor, and member documentation where Javadoc currently reports a warning.
- Documentation describes actual nullability, threading, lifecycle, and compatibility behavior; it must not promise asynchronous behavior for immediate test runners.

- [ ] **Step 1: Run the Javadoc task and capture the complete warning list.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat javadoc --offline --no-daemon --console=plain
  ```

  Expected baseline: the current nonfatal warnings are listed by source and member.

- [ ] **Step 2: Add documentation for every reported member.** Document record components and compact constructors explicitly, add generic type parameters to `ClinicTaskRunner`, and document all public factory parameters/returns and helper contracts.

- [ ] **Step 3: Re-run Javadoc and repeat until output contains no `warning:` lines.** Do not add a blanket `-Xdoclint:none` or warning suppression.

- [ ] **Step 4: Run Checkstyle, PMD, and Spotless after documentation changes.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat spotlessApply checkstyleMain checkstyleTest pmdMain pmdTest --offline --no-daemon --console=plain
  ```

- [ ] **Step 5: Commit.**

  ```powershell
  git add build.gradle src/main/java
  git commit -m "docs: make Java API documentation warning-free"
  ```

### Task 9: Enforce the 500-line gate and raise branch coverage to 70 percent

**Files:**
- Modify: `build.gradle`
- Modify: `src/test/java/nusynapxe/architecture/ArchitectureTest.java`
- Modify/add focused tests under `src/test/java/nusynapxe/ui/`, `src/test/java/nusynapxe/persistence/`, and `src/test/java/nusynapxe/service/`.

**Interfaces:**
- Gradle task `sourceFileSizeCheck` scans `sourceSets.main.allJava` and `sourceSets.test.allJava`, counts physical lines, and fails with `path: count > 500` entries.
- `check` depends on `sourceFileSizeCheck`.
- `jacocoTestCoverageVerification` uses `counter = 'BRANCH'`, `value = 'COVEREDRATIO'`, and `minimum = 0.70` while retaining the existing instruction and line minimums.

- [ ] **Step 1: Add the branch-focused tests before changing the coverage gate.** Add tests for stale action callbacks, per-selector loading, dialog invalidation, pending exports, receipt ordering, empty-filter preservation, and error/stale branches that are not already covered. The source-size red test was added in Task 5 and remains the executable baseline for the refactors.

- [ ] **Step 2: Run the focused tests and JaCoCo report to verify the new tests fail or the current branch ratio is below 70 percent.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test jacocoTestReport --rerun-tasks --offline --no-daemon --console=plain
  ```

- [ ] **Step 3: Add the Gradle source-size task and wire it into `check`.** Keep the diagnostic output deterministic by sorting violations by path. Retain the architecture test for composition-boundary assertions; do not duplicate all size logic in two unrelated mechanisms.

- [ ] **Step 4: Change the branch minimum to `0.70` after the new tests and refactors make the report pass.** Do not add JaCoCo exclusions for newly extracted production code; preserve only existing documented exclusions.

- [ ] **Step 5: Run the complete gate and inspect its reports.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat spotlessCheck check jacocoTestReport javadoc --offline --no-daemon --console=plain
  git diff --check
  ```

  Expected result: all tasks pass, Javadoc emits zero warnings, the branch ratio is at least 70 percent, and the source-size task reports no violations.

- [ ] **Step 6: Commit.**

  ```powershell
  git add build.gradle src/test/java
  git commit -m "build: enforce source size and branch coverage gates"
  ```

### Task 10: Review the final diff, push, reply to, and resolve PR threads

**Files:**
- This task modifies only Git history, GitHub PR #73, and review-thread replies. A verification failure returns to the owning implementation task before this task is retried.

**Interfaces:**
- Review-thread replies use the inline endpoint `repos/CS3227-2610-MP2-NUSynapxe/CS3227-2610-MP2/pulls/73/comments/{comment_id}/replies`.
- Thread resolution uses GraphQL `resolveReviewThread(input: {threadId: ...})` and must be verified by re-querying `reviewThreads.isResolved`.

- [ ] **Step 1: Inspect the complete staged diff and commit list.** Confirm each review fix and decomposition has a focused commit, no unrelated files changed, and all Java files are at most 500 lines.

  ```powershell
  git status --short
  git diff --check
  git log --oneline origin/master..HEAD
  git diff --stat origin/master...HEAD
  ```

- [ ] **Step 2: Run the complete verification command from Task 9 one final time and record test count, coverage percentages, warning count, and task status.**

- [ ] **Step 3: Push the branch.**

  ```powershell
  git push origin refactor/improve-code-quality
  ```

- [ ] **Step 4: Reply to each of the eight inline threads with the fixing commit and focused test evidence.** Use the exact comment IDs and thread IDs retrieved from the GitHub API; do not create top-level comments for inline feedback.

- [ ] **Step 5: Resolve each thread through GraphQL, then re-query all eight threads.** Expected result: every thread has `isResolved: true`, replies are visible, and PR #73 remains open with the updated verification summary.

- [ ] **Step 6: Verify the remote branch SHA equals local `HEAD`, the working tree is clean, and the PR description reports the final test/coverage/Javadoc results.**
