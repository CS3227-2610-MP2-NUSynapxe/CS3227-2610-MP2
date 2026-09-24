---
title: Developer Guide
sidebar_label: Developer Guide
---

# Developer Guide

## 1. Development Prerequisites

NUSynapxe is built on modern Java and desktop UI standards. The project uses:
- **Java**: OpenJDK 25 (Java 25 toolchain)
- **Build System**: Gradle Wrapper 9.7.1
- **UI Toolkit**: JavaFX 25.0.4 (resolved via the `org.openjfx.javafxplugin` Gradle plugin; no external JavaFX SDK installation is needed)
- **Embedded Database**: SQLite JDBC 3.53.4.0 with Xerial SQLite driver
- **Documentation Platform**: Node.js 24 and Docusaurus 3.10.2
- **Testing & Verification**: JUnit 6.1.3 (via `junit-bom`), Mockito 5.23.0, TestFX 4.0.18, ArchUnit 1.5.0, Spotless 8.10.2 (Google Java Format 1.36.1), Checkstyle 14.1.0, PMD 7.26.0, SpotBugs 4.10.3 with FindSecBugs 1.14.0, and JaCoCo 0.8.14.

Use the checked-in Gradle Wrapper (`.\gradlew.bat` on Windows, `./gradlew` on macOS/Linux) rather than an externally installed Gradle binary. Native Windows packaging (`.msi`) additionally requires the [WiX Toolset v3](https://wixtoolset.org/) or newer on system `PATH`; WiX is not needed for routine development, compilation, or testing.

---

## 2. Getting Started

### 2.1 Running NUSynapxe from Source

Start the desktop application from the repository root:

```powershell
# Windows PowerShell
.\gradlew.bat run
```

```bash
# macOS / Linux
./gradlew run
```

The application creates or opens the local SQLite database described in the [User Guide](UserGuide.md) at `%USERPROFILE%\.nusynapxe\nusynapxe.db` (Windows) or `~/.nusynapxe/nusynapxe.db` (macOS/Linux). To isolate development data from your personal environment when running via Gradle, pass the `-PdemoDatabasePath` project property (which the `run` task in `build.gradle` forwards to the forked application JVM as the `nusynapxe.database` system property):

```powershell
# Windows PowerShell
.\gradlew.bat run -PdemoDatabasePath="build/dev-test.db"
```

```bash
# macOS / Linux
./gradlew run -PdemoDatabasePath="build/dev-test.db"
```

*(Note: When launching a standalone fat JAR directly via `java -jar`, pass `-Dnusynapxe.database="build/dev-test.db"` directly to the JVM).*

### 2.2 Useful Build and Quality Commands

Execute the complete local Java quality gate:

```powershell
.\gradlew.bat spotlessApply check javadoc --no-daemon --console=plain
```

Individual focused commands for targeted development:

```powershell
# Run the complete test suite
.\gradlew.bat test --no-daemon --console=plain

# Run specific unit/service tests
.\gradlew.bat test --tests nusynapxe.service.AppointmentServiceTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.persistence.SchemaMigrationTest --tests nusynapxe.persistence.PatientDirectoryRepositoryTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.service.PatientServiceValidationTest --tests nusynapxe.ui.ReceptionistBookingTest --no-daemon --console=plain
.\gradlew.bat test --tests nusynapxe.service.PatientServiceMaintenanceTest --tests nusynapxe.persistence.PatientDirectoryRepositoryTest --tests nusynapxe.ui.DoctorDashboardTest --no-daemon --console=plain

# Run static analysis and linting
.\gradlew.bat checkstyleMain checkstyleTest --no-daemon --console=plain
.\gradlew.bat pmdMain --no-daemon --console=plain
.\gradlew.bat spotbugsMain --no-daemon --console=plain

# Generate coverage reports
.\gradlew.bat jacocoTestReport --no-daemon --console=plain
```

---

## 3. Product Specifications

The system functional requirements, target user personas, priority-rated user stories, structured use cases (UC01–UC16), non-functional specifications, and Gherkin BDD specifications are maintained in the standalone [**Product Specifications**](ProductSpecifications.md) reference.

Key specifications include:
- **Target User Personas**: System Administrator (system bootstrap & staff provisioning), Clinic Receptionist (front-desk coordination & billing), and Attending Physician / Doctor (clinical workflow & scheduling).
- **User Stories**: Prioritized stories covering administrative, identity, appointment, consultation, time-off, and financial domains.
- **Use Cases (UC01–UC16)**: Formal definitions with preconditions, main success scenarios, and failure extensions covering authentication, patient registration, conflict-aware scheduling, check-in, clinical notes, prescriptions, checkout billing, and reporting.
- **Gherkin Specifications**: Executable BDD acceptance scenarios for automated validation.

---

## 4. Architecture and Design

The architectural structure, package modularization, persistence schema, data flow models, and runtime sequence interactions are documented in the standalone [**Architecture and Design**](ArchitectureAndDesign.md) reference.

Key architectural concepts include:
- **Three-Tier Modular Architecture**: Separation into UI / Presentation Layer (`nusynapxe.ui`), Service / Business Logic Layer (`nusynapxe.service`), Domain Model Layer (`nusynapxe.domain`), and Relational Persistence Layer (`nusynapxe.persistence`).
- **Database Schema & Relational Model**: Relational database schema, foreign key constraints, and transactional schema migrations.
- **Key System Sequence Diagrams**:
  1. *Asynchronous Background Task Flow*: Thread-safe UI dispatching via `ClinicTaskRunner` and `Platform.runLater`.
  2. *Preflight Blocker Cascade Check*: Atomic verification of dependent records preventing invalid patient deletions.
  3. *Conflict-Aware Appointment Scheduling*: Temporal collision detection (`starts_at < other_ends && ends_at > other_starts`) against active visits and doctor time-off.
  4. *Infinite-Scrolling Keyspaced Agenda*: Chronological chunked loading with cursor anchoring in Singapore local time.
  5. *Atomic Billing & Sequenced Receipt Issuance*: Transactional payment recording and synchronized daily receipt numbering.

---

## 5. Testing Strategy

The complete testing methodology, test pyramid distribution, quality gates, automated test inventory, and manual verification walkthroughs are documented in the standalone [**Testing Strategy**](TestingStrategy.md) reference.

Key testing policies include:
- **Comprehensive Test Pyramid**: Layered automated verification spanning unit, parameterized, ArchUnit structural, and TestFX UI headless tests.
- **Quality Verification Gates**: Strict enforcement of Spotless code formatting, Checkstyle syntax linting, PMD static analysis, SpotBugs + FindSecBugs security rules, maximum 500-line source file limits, and 80%+ JaCoCo branch and line coverage thresholds.
- **Manual Verification Walkthroughs**: Deterministic test scenarios with step-by-step test vectors for peer testing.

---

## 6. Development Workflow and Showcase Data

### 6.1 Running Demonstration Seed Data

For local feature exploration or manual validation, populate a realistic database containing doctors, receptionists, diverse patient profiles, appointments, clinical notes, prescriptions, and receipts:

```powershell
# Windows PowerShell
.\scripts\seed-demo-data.ps1 -Reset
```

```bash
# macOS / Linux
./gradlew demoData -PdemoDataCommand=seed -PdemoDatabasePath="build/demo.db" -PdemoDataReset=true
```

The seeder initializes sample accounts:
- **System Admin**: `admin.demo` / `DemoAdmin123!`
- **Doctors**: `ada` / `ada1234!`, `grace` / `grace123!`
- **Receptionist**: `reception` / `recept123!`

Creates realistic patient profiles across active and inactive states, multi-week appointment schedules, recurring working intervals, and historical clinical records.

---

## 7. Documentation Site Maintenance

The project documentation website is powered by [Docusaurus 3](https://docusaurus.io/). All documentation sources reside in `docs/` and configuration in `website/`.

### 7.1 Running the Documentation Server Locally

```bash
cd website
npm install
npm run start
```

Runs the development server at `http://localhost:3000/` with live hot-reloading.

### 7.2 Verifying Documentation Build

To verify that all Markdown files, Mermaid diagrams, and internal links compile without broken references:

```bash
cd website
npm run build
```

Generates static HTML files in `website/build/` and fails if broken links, malformed frontmatter, or syntax errors are detected.

---

## 8. Cross-Platform Release Packaging

NUSynapxe produces native desktop installers and fat JARs for cross-platform distribution.

### 8.1 Building Executable JARs

```powershell
# Universal Fat JAR (Windows x64, Linux x64, macOS ARM64)
.\gradlew.bat fatJar --no-daemon --console=plain

# Dedicated Platform JAR (e.g. Windows x64)
.\gradlew.bat fatJarWindowsX64 --no-daemon --console=plain
```

Output JARs are located in `build/libs/`.

### 8.2 Building Native Platform Installers

The project uses `jpackage` to compile self-contained native installers bundling a dedicated Java 25 runtime:
- **Windows (`.msi`)**: `.\gradlew.bat packageMsi` (requires WiX Toolset v3+ on `PATH`).
- **macOS (`.dmg`)**: `./gradlew packageDmg` (executed on macOS host).
- **Linux (`.deb`)**: `./gradlew packageDeb` (requires `fakeroot` on Debian/Ubuntu).

---

## 9. Platform Compatibility Matrix

| Target Platform & Architecture | Native Installer Package | Portable Executable JAR | Universal JAR Compatible? |
| --- | --- | --- | :---: |
| **Windows x64** (Intel / AMD) | `NUSynapxe-<version>-windows-x64.msi` | `NUSynapxe-<version>-windows-x64.jar` | **Yes** |
| **Windows ARM64** (Surface Pro, Snapdragon) | `NUSynapxe-<version>-windows-arm64-compat.msi` | `NUSynapxe-<version>-windows-arm64-compat.jar` | **No** (Requires Platform JAR) |
| **macOS Apple Silicon** (M1 / M2 / M3 / M4) | `NUSynapxe-<version>-macos-arm64.dmg` | `NUSynapxe-<version>-macos-arm64.jar` | **Yes** |
| **macOS Intel (x64)** (Pre-2020 Macs) | `NUSynapxe-<version>-macos-x64.dmg` | `NUSynapxe-<version>-macos-x64.jar` | **No** (Requires Platform JAR) |
| **Linux x64** (Ubuntu, Debian, Mint) | `NUSynapxe-<version>-linux-x64.deb` | `NUSynapxe-<version>-linux-x64.jar` | **Yes** |
| **Linux ARM64** (Raspberry Pi, ARM Linux) | `NUSynapxe-<version>-linux-arm64.deb` | `NUSynapxe-<version>-linux-arm64.jar` | **No** (Requires Platform JAR) |

---

## 10. Requirements-to-Implementation Mapping

| Functional Requirement | Implementation Classes | Automated Test Verification |
| --- | --- | --- |
| **Role-Based Authentication & Session Isolation** | `AuthenticationService`, `PasswordHasher`, `Session` | `AuthenticationServiceTest`, `AuthorizationTest` |
| **Patient Directory & Document Syntax Rules** | `PatientService`, `PatientDirectoryRepository` | `PatientServiceValidationTest`, `PatientDirectoryRepositoryTest` |
| **Safe Patient Deletion with Blocker Preflight** | `PatientService`, `PatientDeletionBlockers` | `PatientServiceMaintenanceTest`, `PatientDirectoryRepositoryTest` |
| **Appointment Lifecycle & Conflict Checking** | `AppointmentService`, `AppointmentTransitions` | `AppointmentServiceTest`, `AppointmentRepositoryScheduleTest` |
| **Arrival Check-in Gate with Singapore Time** | `AppointmentService`, `ReceptionistCheckInQueuePanel` | `AppointmentServiceTest`, `ReceptionistBookingTest` |
| **Assigned Doctor Consultation & Prescriptions** | `ClinicalService`, `ClinicalRecordRepository` | `ClinicalServiceTest`, `DoctorConsultationPanelTest` |
| **Cross-Doctor Consultation History** | `ClinicalService`, `DoctorClinicalHistoryPanel` | `ClinicalServiceTest`, `DoctorClinicalHistoryTest` |
| **Doctor Calendar, Agenda & Working Intervals** | `CalendarService`, `DoctorCalendarNavigationPane` | `CalendarServiceTest`, `DoctorCalendarNavigationTest`, `DoctorCalendarAppointmentTest`, `DoctorCalendarTimeOffTest` |
| **Atomic Billing Checkout & Receipts** | `BillingService`, `ReceiptRepository`, `PaymentRepository` | `BillingServiceTest`, `ReceiptRepositoryTest` |
| **Revenue Reports & CSV/JSON Exporting** | `RevenueReport`, `BillingService` | `RevenueReportTest`, `ReceptionistBookingTest` |
| **Versioned Relational SQLite Storage** | `SqliteDatabase`, `SchemaInitializer` | `SqliteDatabaseTest`, `SchemaMigrationTest` |
| **Layered Architecture & Package Direction** | `ArchitectureTest`, Gradle config | `ArchitectureTest`, `check` task |
| **Cross-Platform Delivery & Packaging** | `build.gradle`, `.github/workflows/release.yml` | GitHub Actions multi-platform release matrix |

---

## 11. Known Limitations & Future Work

1. **Single-Workstation Concurrency**: The application is optimized for single-workstation local desktop use with SQLite WAL mode. Future iterations could incorporate an optional remote PostgreSQL sync connector for multi-counter clinic networks.
2. **External Identity Verification**: Identity document syntax (NRIC/FIN/Passport) is strictly verified via pattern matching and issuing country checks, but does not query government identity registries (e.g. Singpass API).
3. **Calendar Shading vs Booking Policy**: Doctor working intervals and lunch breaks provide visual calendar shading to assist front-desk scheduling without strictly blocking emergency walk-in bookings.
4. **Code Signing**: Open-source binaries are unsigned. Operating systems may present a one-time trust prompt on first run. Future releases could integrate Apple Developer ID and Microsoft Authenticode code-signing certificates.
5. **Universal JAR Scope**: The universal JAR embeds native libraries for Windows x64, Linux x64, and macOS Apple Silicon. Other architectures (Windows ARM64, macOS Intel, Linux ARM64) require their respective dedicated platform JARs or native installers.

---

## 12. Acknowledgements

We gratefully acknowledge the following open-source projects, frameworks, specifications, and reference documentation that made the development of NUSynapxe possible:

| Source / Project | Role and Utilization in NUSynapxe |
| --- | --- |
| [OpenJFX](https://openjfx.io/) | High-performance desktop UI controls, scene graph, layouts, and JavaFX application lifecycle. |
| [SQLite](https://www.sqlite.org/docs.html) & [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc) | Zero-configuration embedded relational persistence, transactional schema initialization, and foreign key enforcement. |
| [Google libphonenumber](https://github.com/google/libphonenumber) | International telephone country calling-code mapping and metadata resolution. |
| [JUnit 5](https://junit.org/junit5/) & [Mockito](https://site.mockito.org/) | Comprehensive unit, parameterized, and service layer mock testing. |
| [TestFX](https://github.com/TestFX/TestFX) | Automated headless JavaFX user interface interaction, form automation, and scene assertion. |
| [ArchUnit](https://www.archunit.org/) | Automated structural linting and architectural dependency direction enforcement. |
| [Spotless](https://github.com/diffplug/spotless) & [Google Java Format](https://github.com/google/google-java-format) | Deterministic code formatting and automated style checking. |
| [Checkstyle](https://checkstyle.org/) & [PMD](https://pmd.github.io/) | Static source analysis, maintainability rules, and coding standards enforcement. |
| [SpotBugs](https://spotbugs.github.io/) & [FindSecBugs](https://find-sec-bugs.github.io/) | Bytecode security vulnerability detection and static bug pattern analysis. |
| [JaCoCo](https://www.jacoco.org/jacoco/) | Branch and instruction code coverage analysis and report generation. |
| [Docusaurus](https://docusaurus.io/) & [Mermaid](https://mermaid.js.org/) | Production documentation website generation, MDX rendering, and source-controlled architectural diagrams. |
| [WiX Toolset](https://wixtoolset.org/) & `jpackage` | Native Windows (`.msi`), macOS (`.dmg`), and Linux (`.deb`) installer compilation. |
