# ArchUnit Architecture Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pinned ArchUnit test dependency and enforce NuSynapxe's documented package boundaries and cycle-free core architecture in the normal Gradle quality gate.

**Architecture:** Keep the existing `domain`, `persistence`, `service`, `ui`, and `tools` package layout. Express dependency direction as named ArchUnit rules over production bytecode, excluding test classes; allow `ApplicationRouter` and `tools` to retain their documented composition/bootstrap responsibilities.

**Tech Stack:** Java 25, Gradle 9.7.1, JUnit Jupiter 6.1.3, ArchUnit core 1.5.0, Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, OpenSpec.

**Spec:** `docs/superpowers/specs/2026-09-13-archunit-architecture-tests-design.md`

## Global Constraints

- Keep `com.tngtech.archunit:archunit:1.5.0` test-only and pinned.
- Import only production classes below `nusynapxe`; do not make test packages part of the architecture subject.
- `domain` cannot depend on `ui`, `service`, `persistence`, or `tools`.
- `persistence` cannot depend on `ui`, `service`, or `tools`.
- `service` cannot depend on `ui` or `tools`.
- UI classes cannot depend on `tools` or directly on `persistence`, except `nusynapxe.ui.ApplicationRouter`.
- The `domain`, `persistence`, `service`, `ui`, and `tools` slices must be cycle-free.
- Preserve existing production interfaces and composition-root behavior.

---

### Task 1: Add the pinned ArchUnit test dependency

**Files:**
- Modify: `build.gradle:69-81`

**Interfaces:**
- Produces the `com.tngtech.archunit:archunit:1.5.0` test classpath entry used by Task 2.

- [ ] **Step 1: Add the test-only dependency**

Insert this line with the other JUnit/TestFX test dependencies:

```groovy
testImplementation 'com.tngtech.archunit:archunit:1.5.0'
```

Do not add ArchUnit to `implementation`, and do not add the ArchUnit JUnit
engine because the tests will call the plain ArchUnit API from JUnit Jupiter.

- [ ] **Step 2: Resolve the dependency**

Run:

```powershell
.\gradlew.bat dependencies --configuration testCompileClasspath --no-daemon --console=plain
```

Expected: the dependency report contains `com.tngtech.archunit:archunit:1.5.0`.

- [ ] **Step 3: Commit the build change**

```powershell
git add build.gradle
git commit -m "build: add ArchUnit test dependency"
```

### Task 2: Add executable package-boundary and cycle rules

**Files:**
- Create: `src/test/java/nusynapxe/architecture/ArchitectureTest.java`

**Interfaces:**
- Consumes: `com.tngtech.archunit.core.importer.ClassFileImporter`,
  `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`, and the ArchUnit rule DSL.
- Produces: five named JUnit tests that fail when a forbidden dependency or
  core package cycle is introduced.

- [ ] **Step 1: Write the architecture test before production changes**

Create the test with the following structure and rules:

```java
package nusynapxe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  private static final String DOMAIN = "nusynapxe.domain..";
  private static final String PERSISTENCE = "nusynapxe.persistence..";
  private static final String SERVICE = "nusynapxe.service..";
  private static final String UI = "nusynapxe.ui..";
  private static final String TOOLS = "nusynapxe.tools..";
  private static final String APPLICATION_ROUTER = "nusynapxe.ui.ApplicationRouter";

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("nusynapxe");

  private static final ArchRule DOMAIN_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(UI, SERVICE, PERSISTENCE, TOOLS);

  private static final ArchRule PERSISTENCE_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(PERSISTENCE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(UI, SERVICE, TOOLS);

  private static final ArchRule SERVICE_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(SERVICE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(UI, TOOLS);

  private static final ArchRule UI_BOUNDARY =
      noClasses()
          .that()
          .resideInAnyPackage(UI)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(TOOLS);

  private static final ArchRule UI_PERSISTENCE_COMPOSITION_ROOT =
      noClasses()
          .that()
          .resideInAnyPackage(UI)
          .and()
          .doNotHaveFullyQualifiedName(APPLICATION_ROUTER)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(PERSISTENCE);

  private static final ArchRule CORE_SLICES_ARE_CYCLE_FREE =
      slices().matching("nusynapxe.(*)..").should().beFreeOfCycles();

  @Test
  void domainRemainsIndependentFromOuterLayers() {
    DOMAIN_BOUNDARY.check(MAIN_CLASSES);
  }

  @Test
  void persistenceRemainsIndependentFromOuterLayers() {
    PERSISTENCE_BOUNDARY.check(MAIN_CLASSES);
  }

  @Test
  void serviceRemainsIndependentFromUiAndTools() {
    SERVICE_BOUNDARY.check(MAIN_CLASSES);
  }

  @Test
  void uiUsesServicesInsteadOfPersistenceExceptAtTheCompositionRoot() {
    UI_BOUNDARY.check(MAIN_CLASSES);
    UI_PERSISTENCE_COMPOSITION_ROOT.check(MAIN_CLASSES);
  }

  @Test
  void corePackagesRemainFreeOfCycles() {
    CORE_SLICES_ARE_CYCLE_FREE.check(MAIN_CLASSES);
  }
}
```

The import option is required so the architecture package's own test class is
not treated as production architecture. The two UI rules deliberately keep
the `ApplicationRouter` persistence exception visible in the rule name and
failure output.

- [ ] **Step 2: Run the focused architecture test**

Run:

```powershell
.\gradlew.bat test --tests nusynapxe.architecture.ArchitectureTest --no-daemon --console=plain
```

Expected: all five tests pass against the current package graph. If a rule
fails, inspect the reported class dependency and adjust only when it conflicts
with the approved design; do not weaken a rule to hide an unexplained edge.

- [ ] **Step 3: Commit the architecture tests**

```powershell
git add src/test/java/nusynapxe/architecture/ArchitectureTest.java
git commit -m "test: enforce package architecture with ArchUnit"
```

### Task 3: Align documentation and OpenSpec artifacts

**Files:**
- Modify: `README.md:111-143`
- Modify: `docs/DeveloperGuide.md:3-34,62-82`
- Modify: `openspec/changes/improve-doctor-dashboard-calendar/proposal.md`
- Modify: `openspec/changes/improve-doctor-dashboard-calendar/design.md`
- Create: `openspec/changes/improve-doctor-dashboard-calendar/specs/architecture/spec.md`
- Modify: `openspec/changes/improve-doctor-dashboard-calendar/tasks.md`

**Interfaces:**
- Consumes: the passing rules and dependency from Tasks 1–2.
- Produces: a documented architecture gate and OpenSpec traceability for the
  new follow-up.

- [ ] **Step 1: Document the dependency and command in the README**

Add `ArchUnit | 1.5.0` to the pinned toolchain table after JUnit Jupiter, and
change the quality-gate paragraph to say that `check` runs the JUnit suite,
including `nusynapxe.architecture.ArchitectureTest`, before Checkstyle, PMD,
SpotBugs, and JaCoCo.

- [ ] **Step 2: Document the package rules in the Developer Guide**

Add this subsection after the existing package-boundary explanation:

```markdown
### Architecture tests

`nusynapxe.architecture.ArchitectureTest` uses ArchUnit 1.5.0 to import only
production classes and enforce the package direction documented above. Domain
classes remain independent from outer layers; persistence cannot reach UI,
services, or tools; services cannot reach UI or tools; and UI classes cannot
reach tools or persistence except for `ApplicationRouter`, the database-opening
composition root. The `domain`, `persistence`, `service`, `ui`, and `tools`
slices must also remain free of cycles.

The architecture rules run as part of `.\gradlew.bat check`; run the focused
test with:

```powershell
.\gradlew.bat test --tests nusynapxe.architecture.ArchitectureTest --no-daemon --console=plain
```
```

- [ ] **Step 3: Update the OpenSpec delta**

Add a follow-up proposal/design entry describing the ArchUnit dependency and
the five rules. Add `specs/architecture/spec.md` with this requirement:

```markdown
# Architecture checks

## Requirements

### Requirement: Enforce package dependency direction

The test quality gate SHALL run ArchUnit rules over production classes and
reject forbidden dependencies from `domain` to outer packages, from
`persistence` to UI/services/tools, from `service` to UI/tools, and from UI to
tools or persistence except `ApplicationRouter`; the core package slices SHALL
remain free of cycles.

#### Scenario: Architecture rules pass for the current package graph

- **WHEN** `ArchitectureTest` imports the production classes
- **THEN** every named package-boundary and cycle rule passes

#### Scenario: A forbidden dependency is introduced

- **WHEN** a class in a protected package references a forbidden package
- **THEN** the corresponding ArchUnit test fails with the violating class and
  dependency
```

Append a section to `tasks.md` with two unchecked tasks: one for the dependency
and rules, and one for documentation plus quality verification. Do not mark
either task complete until the matching implementation and checks pass.

- [ ] **Step 4: Commit documentation and artifacts**

```powershell
git add README.md docs/DeveloperGuide.md openspec/changes/improve-doctor-dashboard-calendar
git commit -m "docs: document ArchUnit architecture gate"
```

### Task 4: Run the complete quality gate and close the OpenSpec tasks

**Files:**
- Modify: `openspec/changes/improve-doctor-dashboard-calendar/tasks.md`

**Interfaces:**
- Consumes: the dependency, tests, and documentation from Tasks 1–3.
- Produces: a verified, all-done OpenSpec change ready to archive.

- [ ] **Step 1: Apply formatting and run the full gate**

Run:

```powershell
.\gradlew.bat spotlessApply --offline --no-daemon --console=plain
.\gradlew.bat check javadoc --offline --no-daemon --console=plain
```

Expected: the full JUnit suite (including `ArchitectureTest`), formatting,
Checkstyle, PMD, SpotBugs, JaCoCo, and Javadoc all pass.

- [ ] **Step 2: Validate OpenSpec and whitespace**

Run:

```powershell
openspec validate improve-doctor-dashboard-calendar --type change --strict
openspec validate --specs --strict
git diff --check
```

Expected: the change and all main specs validate successfully, and Git reports
no whitespace errors.

- [ ] **Step 3: Mark both follow-up tasks complete**

Change only the two new architecture follow-up task entries from `- [ ]` to
`- [x]` after the checks pass. Confirm `openspec instructions apply
--change "improve-doctor-dashboard-calendar" --json` reports all tasks done.

- [ ] **Step 4: Commit the completion marker**

```powershell
git add openspec/changes/improve-doctor-dashboard-calendar/tasks.md
git commit -m "docs: complete ArchUnit architecture checks"
```
