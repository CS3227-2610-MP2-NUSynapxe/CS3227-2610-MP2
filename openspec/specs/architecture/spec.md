# architecture Specification

## Purpose

Protects the production package boundaries and prevents dependency cycles from
silently weakening the application's layered architecture.

## Requirements

### Requirement: Enforce package dependency direction

The test quality gate SHALL run ArchUnit rules over production classes and
reject forbidden dependencies from `domain` to outer packages, from
`persistence` to UI/services/tools, from `service` to UI/tools, and from UI to
tools or persistence except `ApplicationRouter`; the `domain`, `persistence`,
`service`, `ui`, and `tools` package slices SHALL remain free of cycles.

#### Scenario: Architecture rules pass for the current package graph

- **WHEN** `ArchitectureTest` imports the production classes
- **THEN** every named package-boundary and cycle rule passes

#### Scenario: A forbidden dependency is introduced

- **WHEN** a class in a protected package references a forbidden package
- **THEN** the corresponding ArchUnit test fails with the violating class and
  dependency

### Requirement: Keep architecture checks in the test quality gate

The project SHALL keep ArchUnit test-only and pinned, and the standard Gradle
`test` and `check` tasks SHALL execute the architecture tests alongside the
existing JUnit and static-analysis checks.

#### Scenario: Focused architecture verification is available

- **WHEN** a developer runs
  `.\gradlew.bat test --tests nusynapxe.architecture.ArchitectureTest`
- **THEN** the five named architecture checks execute against production
  bytecode only
