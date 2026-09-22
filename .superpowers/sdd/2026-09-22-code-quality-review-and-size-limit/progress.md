# SDD ledger — plan: docs/superpowers/plans/2026-09-22-code-quality-review-and-size-limit.md

Setup: current branch `refactor/improve-code-quality` is isolated from `master`; the worktree was clean before execution.

Ruling: Bash bookkeeping helpers could not run because `Bash/Service/CreateInstance` returned `E_ACCESSDENIED` even after an escalated retry; created this plan-scoped workspace and ledger manually in PowerShell, leaving the earlier plan workspace untouched. Cost if wrong: the helper's automatic task briefs are unavailable, so task boundaries and completion records must be maintained manually from the committed plan.

Pre-flight: shared interfaces
- Task 1 changes `DoctorWorkspace` selection/action state consumed by Task 3 lifecycle wiring and Task 5 Doctor decomposition; preserve captured-ID and generation contracts while extracting.
- Task 2 changes `ReceptionistDataLoader` selector-loading signatures consumed by Task 5 Receptionist decomposition; complete selector-policy changes before moving panel code.
- Task 3 adds `WorkspaceLifecycle` and dialog owner-active parameters consumed by Task 5 `AppointmentDialog` extraction; keep the lifecycle supplier at the facade boundary.
- Task 4 changes revenue pending state and receipt ordering consumed by Task 7 test splits and Task 9 coverage/size verification; preserve node IDs and export behavior.
- Task 5 produces the post-extraction UI class boundaries consumed by Task 9's 500-line gate and Task 10's final diff review.
- Task 6 preserves repository and seeder facades consumed by all service/integration tests and Task 9's source-size gate.
- Task 7 changes test class names and fixtures consumed by Task 9's source-size gate and full verification command.
- Task 8 documents the final public/package APIs produced by Tasks 1–7; run it after refactors so documentation describes final signatures.
- Task 9 produces the passing source-size, Javadoc, and branch-coverage gates required before Task 10 can push or resolve threads.

Task 1: complete (commit 73e22f6; tests: DoctorAppointmentTargetTest and DoctorViewTest -> passed)

Task 2: Ruling: the independent-selector test initially compared numeric generations across different selector keys; corrected it to assert freshness per selector, because equal generation numbers are valid for independent keys. Cost if wrong: the test would reject a correct per-selector generation design.
Task 2: complete (commit subject: fix: snapshot calendar and selector inputs; tests: CalendarRangeSnapshotTest, SelectorLoadGenerationTest, ReceptionistDataLoaderTest, DoctorCalendarViewTest, and ReceptionistViewTest -> passed)

Task 3: complete (commit subject: fix: guard appointment dialogs after logout; tests: WorkspaceLifecycleTest, DoctorCalendarViewTest, DoctorViewTest, and ReceptionistViewTest -> passed)

Task 4: complete (commit subject: fix: protect receipt ordering and pending exports; tests: ReceiptRepositoryTest and ReportExportStateTest -> passed)
