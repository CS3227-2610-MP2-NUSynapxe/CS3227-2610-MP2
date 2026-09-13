# ArchUnit Architecture Tests Design

## Context

NuSynapxe is a Java 25 desktop application organized into `domain`,
`persistence`, `service`, `ui`, and `tools` packages. The package layout is
documented, but the dependency direction is currently enforced only by code
review and the existing static-analysis tools. A small set of composition
roots intentionally coordinates otherwise separate layers: the application
router opens the database, and demo-data tooling coordinates services and
repositories.

## Goals

- Add a pinned ArchUnit test dependency without adding a runtime dependency.
- Make the documented dependency direction executable in the normal Gradle
  test and `check` lifecycle.
- Keep the existing composition-root responsibilities explicit rather than
  forcing an unrelated refactor.
- Produce actionable failures when a future class crosses a forbidden package
  boundary or introduces a cycle among the core packages.

## Non-goals

- Reorganizing production packages or changing service, persistence, or UI
  interfaces.
- Treating the `tools` package as a normal runtime layer; it remains a
  bootstrap/demo-data composition utility.
- Replacing Checkstyle, PMD, SpotBugs, or unit/UI tests.

## Rules

`ArchitectureTest` imports the production classes below `nusynapxe` and
asserts these package-level constraints:

1. `domain` must not depend on `ui`, `service`, `persistence`, or `tools`.
2. `persistence` must not depend on `ui`, `service`, or `tools`.
3. `service` must not depend on `ui` or `tools`.
4. `ui` must not depend on `tools` or directly on `persistence`, except for
   `ApplicationRouter`, which is the composition root that opens the
   application database.
5. The `domain`, `persistence`, `service`, `ui`, and `tools` package slices must
   be free of dependency cycles.

The rules intentionally allow `service` to depend on `persistence` and
`domain`, and `ui` to depend on `service` and `domain`. They also allow the
demo-data package to coordinate lower-level components because seeding is an
explicit bootstrap concern rather than a production request path.

## Implementation

- Add `com.tngtech.archunit:archunit:1.5.0` to `testImplementation`; keep it
  test-only and pinned alongside the other quality-tool versions.
- Add `src/test/java/nusynapxe/architecture/ArchitectureTest.java` using the
  plain ArchUnit API and JUnit assertions, so it runs with the project's
  existing JUnit Platform configuration.
- Keep the rules named and grouped by boundary so a failed test identifies the
  violated dependency direction.
- Update the Developer Guide and OpenSpec delta artifacts with the dependency,
  rules, and verification command.

## Verification

- Run the focused architecture test first and observe it fail before adding
  the production/build implementation.
- Run the full Gradle quality gate (`check` and `javadoc`) with the repository
  JDK, including the new architecture tests.
- Run strict OpenSpec validation and `git diff --check`.
