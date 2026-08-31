# Developer Guide

## Prerequisites

- JDK 25, with `JAVA_HOME` pointing to the selected JDK
- Node.js 24 or later for the Docusaurus site
- npm, installed with Node.js

The Gradle Wrapper downloads and uses Gradle 9.7.1. JavaFX is resolved from
Maven Central by the JavaFX Gradle plugin, so no manual JavaFX SDK installation
is required.

## Source layout

```text
src/main/java/nusynapxe/             Application and domain code
src/main/java/nusynapxe/ui/          JavaFX views
src/main/java/nusynapxe/persistence/ SQLite integration
src/test/java/nusynapxe/             JUnit, Mockito, and TestFX tests
config/checkstyle/                   Checkstyle configuration
config/pmd/                          PMD ruleset
config/spotbugs/                     SpotBugs filters
website/                             Docusaurus site configuration
```

The initial storage boundary is `SqliteDatabase`. It creates the parent
directory and an `app_metadata` table on first open. Feature-specific tables
and repositories should be added under `nusynapxe.persistence` as the domain
model is introduced.

## Gradle commands

Run the application:

```powershell
.\gradlew.bat run
```

Run the full local verification suite:

```powershell
.\gradlew.bat spotlessApply check --no-daemon --console=plain
```

Useful focused commands are:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat checkstyleMain checkstyleTest --no-daemon --console=plain
.\gradlew.bat pmdMain --no-daemon --console=plain
.\gradlew.bat spotbugsMain --no-daemon --console=plain
.\gradlew.bat jacocoTestReport --no-daemon --console=plain
```

`spotlessApply` changes Java source formatting. `spotlessCheck` is the
read-only CI equivalent. Checkstyle, PMD, and SpotBugs fail the build on
violations. Test-source PMD and SpotBugs are disabled because the respective
analysers otherwise report on framework-specific test harness code; production
source remains enforced.

## Tests

JUnit Jupiter is the unit-test platform. Mockito is available through
`mockito-junit-jupiter` for collaborator isolation. TestFX is configured for
JavaFX scene-graph interaction tests. TestFX needs a display; on Linux CI run
the Gradle suite through `xvfb-run`.

JaCoCo produces HTML and XML reports after `test`:

```text
build/reports/jacoco/test/html/index.html
build/reports/jacoco/test/jacocoTestReport.xml
```

The initial project generates coverage reports without enforcing a percentage
threshold. A threshold should be introduced after the first feature slice has
enough meaningful production behavior to measure.

## Documentation

From the repository root:

```powershell
Set-Location website
npm ci
npm run start
```

`docusaurus.config.js` reads the repository-root README as the `/` overview
page and exposes `docs/developer-guide.md` and `docs/user-guide.md` as separate
navigation entries.
