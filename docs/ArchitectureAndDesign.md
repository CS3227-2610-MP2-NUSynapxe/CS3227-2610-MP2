---
title: Architecture & System Design
sidebar_label: Architecture & Design
---

# Architecture & System Design

This document details the architectural principles, component structure, database schema, authorization mechanics, and workflow rules of NUSynapxe.

For product specifications and use case flows, see [Product Specifications & Use Cases](ProductSpecifications.md). For automated and manual testing strategies, see [Testing Strategy](TestingStrategy.md).

---

## 1. System Architecture

### 1.1 Component & Tier Architecture

NUSynapxe enforces a strict layered architecture with unidirectional dependencies from presentation down to persistence:

```mermaid
flowchart TB
    subgraph PresentationLayer["Presentation Layer (JavaFX)"]
        Router["ApplicationRouter (Composition Root)"]
        UIComp["UiComponents (Shared Design System)"]
        AdminView["SystemAdminView"]
        ReceptView["ReceptionistView & Subviews"]
        DocView["DoctorView & Subviews"]
        SharedDir["PatientDirectoryView (Shared Component)"]
    end

    subgraph ServiceLayer["Service Layer (Business Rules & Authorization)"]
        AuthService["AuthenticationService & Session"]
        AccountService["AccountService"]
        PatientService["PatientService"]
        ApptService["AppointmentService"]
        ClinicalService["ClinicalService"]
        CalendarService["CalendarService"]
        BillingService["BillingService"]
        Authorizer["Authorization Guard"]
    end

    subgraph PersistenceLayer["Persistence Layer (Projections & Transactions)"]
        SqliteDb["SqliteDatabase & Transactions"]
        SchemaInit["SchemaInitializer (v1 - v5 Migrations)"]
        AccountRepo["AccountRepository"]
        PatientRepo["PatientDirectoryRepository"]
        ApptRepo["AppointmentRepository"]
        ClinicalRepo["ClinicalRecordRepository"]
        TimeOffRepo["DoctorTimeOffRepository"]
        WorkingRepo["DoctorWorkingIntervalRepository"]
        BillingRepo["PaymentReceiptRepository"]
    end

    subgraph DomainLayer["Domain Layer (Immutable Records & Enums)"]
        Records["Patient, Appointment, ClinicalRecord, Prescription, etc."]
        Enums["Role, AppointmentStatus, IdentityType, PaymentMethod"]
        Values["Money, TimeSlot, DoctorCalendarWeek, CalendarScheduleCursor"]
    end

    subgraph StorageLayer["Storage Layer (Embedded)"]
        SQLiteFile[("SQLite Database (~/.nusynapxe/nusynapxe.db)")]
    end

    %% Dependencies
    Router --> AdminView & ReceptView & DocView
    ReceptView & DocView --> SharedDir
    AdminView & ReceptView & DocView & SharedDir --> ServiceLayer
    PresentationLayer -. uses .-> UIComp
    ServiceLayer --> Authorizer
    ServiceLayer --> PersistenceLayer
    ServiceLayer -. consumes & produces .-> DomainLayer
    PersistenceLayer -. maps .-> DomainLayer
    PersistenceLayer --> SqliteDb
    SqliteDb --> SQLiteFile
```

### 1.2 Package Layout and Responsibilities

```text
src/main/java/nusynapxe/             Application entry point, paths, and router
src/main/java/nusynapxe/domain/      Immutable records, value objects, and enums
src/main/java/nusynapxe/tools/       Database seeding and demo data utilities
src/main/java/nusynapxe/persistence/ SQLite connection, migrations, repositories
src/main/java/nusynapxe/service/     Authorization, business validation, use-cases
src/main/java/nusynapxe/ui/          JavaFX programmatic views and UI components
src/test/java/nusynapxe/             Unit, integration, persistence, and TestFX tests
config/checkstyle/                   Checkstyle ruleset
config/pmd/                          PMD ruleset
config/spotbugs/                     SpotBugs security and bug exclusions
website/                             Docusaurus documentation website
```

### 1.3 Enforced Dependency Rules

Architectural rules are codified and continuously verified via **ArchUnit 1.5.0** in `ArchitectureTest.java`:
1. **Domain Isolation**: Classes in `nusynapxe.domain` have zero dependencies on outer layers (persistence, service, UI, or tools).
2. **Persistence Boundary**: `nusynapxe.persistence` cannot depend on `service`, `ui`, or `tools`.
3. **Service Layer Boundary**: `nusynapxe.service` cannot depend on `ui` or `tools`.
4. **UI Isolation**: `nusynapxe.ui` cannot directly access `persistence` or `tools`. The sole exception is `ApplicationRouter`, which acts as the composition root by opening the database and wiring dependencies.
5. **No Package Cycles**: Slice assertions verify zero cyclical dependencies between `domain`, `persistence`, `service`, `ui`, and `tools`.

Run the automated architecture check:

```powershell
.\gradlew.bat test --tests nusynapxe.architecture.ArchitectureTest --no-daemon --console=plain
```

### 1.4 Application Startup & Session Routing Sequence

```mermaid
sequenceDiagram
    autonumber
    actor User as Clinic Staff
    participant Main as NUSynapxeApp
    participant Router as ApplicationRouter
    participant DB as SqliteDatabase
    participant Schema as SchemaInitializer
    participant Auth as AuthenticationService
    participant View as Role Workspace View

    User->>Main: Launch application
    Main->>Router: init() & start(Stage)
    Router->>DB: SqliteDatabase.open(path)
    DB->>DB: PRAGMA foreign_keys = ON;
    DB->>Schema: initialize(connection)
    Schema->>DB: Execute versioned migrations (v1 to v5)
    Router->>Auth: hasAccounts()
    alt Zero Accounts in Database
        Router->>View: Render Initial Setup Wizard
        User->>View: Enter Admin Credentials
        View->>Router: AccountService.createAdmin(...)
    end
    Router->>View: Render Login Screen
    User->>View: Enter Username & Password
    View->>Auth: login(username, password)
    Auth->>Auth: Verify PBKDF2 hash & active status
    Auth-->>View: In-Memory Session (User, Role)
    alt Role == SYSTEM_ADMIN
        Router->>View: Open SystemAdminView
    else Role == RECEPTIONIST
        Router->>View: Open ReceptionistView (Directory)
    else Role == DOCTOR
        Router->>View: Open DoctorView (Dashboard)
    end
```

---

## 2. Persistence & Schema Design

### 2.1 SQLite Connection Lifecycle and Settings

`SqliteDatabase.java` manages SQLite JDBC connections. On initialization:
1. Creates parent directory if missing (`%USERPROFILE%\.nusynapxe` or `~/.nusynapxe`).
2. Opens connection with `PRAGMA foreign_keys = ON;` to enforce relational integrity.
3. Configures WAL (Write-Ahead Logging) mode for concurrent read performance and crash resilience.
4. Executes `SchemaInitializer.java` to inspect `app_metadata.schema_version` and apply pending schema migrations.

### 2.2 Entity-Relationship Diagram

```mermaid
erDiagram
    users ||--o{ appointments : "doctor assigns"
    users ||--o{ doctor_time_off : "doctor owns"
    users ||--o{ doctor_calendar_settings : "doctor owns"
    users ||--o{ doctor_working_intervals : "doctor owns"
    users ||--o{ clinical_records : "doctor authors"
    users ||--o{ payments : "receptionist / doctor links"

    patients ||--o{ appointments : "books"
    patients ||--o{ payments : "pays"

    appointments ||--o| clinical_records : "documents"
    appointments ||--o| payments : "settles"

    clinical_records ||--o{ prescriptions : "prescribes"

    users {
        INTEGER id PK
        TEXT username UK
        TEXT display_name
        TEXT role
        INTEGER active
        BLOB password_salt
        BLOB password_verifier
        TEXT created_at
    }

    patients {
        INTEGER id PK
        TEXT identity_type
        TEXT issuing_country
        TEXT identity_number
        TEXT full_name
        TEXT date_of_birth
        TEXT sex
        TEXT phone_country_code
        TEXT phone_number
        TEXT email
        TEXT residential_address
        INTEGER height_cm
        REAL weight_kg
        INTEGER active
        TEXT created_at
    }

    appointments {
        INTEGER id PK
        INTEGER patient_id FK
        INTEGER doctor_id FK
        TEXT starts_at
        TEXT ends_at
        TEXT status
        TEXT created_at
    }

    doctor_time_off {
        INTEGER id PK
        INTEGER doctor_id FK
        TEXT starts_at
        TEXT ends_at
        TEXT created_at
    }

    doctor_calendar_settings {
        INTEGER id PK
        INTEGER doctor_id FK
        INTEGER first_day_of_week
        TEXT created_at
    }

    doctor_working_intervals {
        INTEGER id PK
        INTEGER doctor_id FK
        INTEGER day_of_week
        INTEGER start_minute
        INTEGER end_minute
    }

    clinical_records {
        INTEGER id PK
        INTEGER appointment_id FK
        INTEGER patient_id FK
        INTEGER doctor_id FK
        TEXT diagnosis
        TEXT examination_notes
        TEXT follow_up_instructions
        TEXT created_at
    }

    prescriptions {
        INTEGER id PK
        INTEGER clinical_record_id FK
        TEXT medication_name
        TEXT dosage
        TEXT frequency
        TEXT duration
        TEXT instructions
    }

    payments {
        INTEGER id PK
        INTEGER appointment_id FK
        INTEGER patient_id FK
        INTEGER amount_cents
        TEXT payment_method
        TEXT receipt_number UK
        TEXT paid_at
    }

    app_metadata {
        TEXT key PK
        TEXT value
    }
```

### 2.3 Schema Version Evolution & Migrations

Migrations execute within an explicit SQLite transaction; failure rolls back all statement changes and leaves `schema_version` unchanged:
- **Version 1**: Initial baseline tables (`users`, `patients`, `appointments`, `clinical_records`, `prescriptions`, `payments`, `app_metadata`).
- **Version 2**: Adds nullable identity columns (`identity_type`, `issuing_country`, `identity_number`), `sex`, `height_cm`, `weight_kg`, and `active` flag to `patients` for backward compatibility.
- **Version 3**: Drops legacy `billing_information` column from `patients` table (payment records are preserved in `payments`) and normalizes legacy sex values to `FEMALE` or `MALE`.
- **Version 4**: Renames legacy `phone` to `phone_number`, adds nullable `phone_country_code`, and requires country code validation on subsequent patient saves.
- **Version 5**: Introduces `doctor_calendar_settings` and `doctor_working_intervals` supporting custom daily shift intervals and lunch breaks (up to 1440 minutes). Default seed supplies Mon-Fri `08:00`-`18:00` display intervals.

### 2.4 Safe Patient Deletion Sequence with Preflight Blocker Check

To maintain relational integrity, NUSynapxe **never uses cascade deletions** (`ON DELETE CASCADE`). Deletion is strictly reserved for unused records. If any linked record exists, deletion is rejected with itemized blocker counts:

```mermaid
sequenceDiagram
    autonumber
    actor Staff as Clinic Staff
    participant UI as PatientDirectoryView
    participant Service as PatientService
    participant Repo as PatientDirectoryRepository
    participant DB as SQLite Database

    Staff->>UI: Click "Delete patient"
    UI->>Service: deletePatient(sessionId, patientId)
    Service->>Service: Authorization.requirePatientAdministration()
    Service->>Repo: inspectDeletionBlockers(patientId)
    Repo->>DB: COUNT(*) FROM appointments WHERE patient_id = ?
    Repo->>DB: COUNT(*) FROM clinical_records WHERE patient_id = ?
    Repo->>DB: COUNT(*) FROM prescriptions via clinical_records
    Repo->>DB: COUNT(*) FROM payments WHERE patient_id = ?
    Repo-->>Service: PatientDeletionBlockers(apptCount, clinicalCount, prescrCount, payCount)
    alt Any Blocker Count > 0
        Service-->>UI: throw PatientDeletionBlockedException(blockers)
        UI->>Staff: Display "Delete Blocked" Dialog with Category Counts & Deactivation Suggestion
    else All Blocker Counts == 0
        Service->>Repo: deleteIfUnrelated(patientId)
        Repo->>DB: BEGIN TRANSACTION
        Repo->>DB: Re-verify all counts == 0
        Repo->>DB: DELETE FROM patients WHERE id = ?
        Repo->>DB: COMMIT
        Repo-->>Service: Success
        Service-->>UI: Deletion Completed
        UI->>Staff: Patient Removed & Directory Refreshed
    end
```

---

## 3. Account, Session, and Authorization Design

### 3.1 Cryptographic Password Storage

`AccountService.java` delegates password verification and salting to `PasswordHasher.java`:
- **Algorithm**: `PBKDF2WithHmacSHA256`
- **Salt**: 16 cryptographically secure random bytes generated per account via `SecureRandom`
- **Iterations**: 65,536 iterations
- **Key Length**: 256 bits
- **Zeroing Memory**: Password char arrays are scrubbed immediately after verification. Plaintext passwords and derived hashes are never recorded in application log files.

### 3.2 Volatile In-Memory Session

Authentication produces an immutable `Session` object containing the authenticated `User`, their assigned `Role`, and login timestamp. Sessions exist purely in JVM volatile heap memory and are **never serialized to SQLite or written to disk**. When the application closes or the user clicks **Log out**, the session reference is discarded.

### 3.3 Role Authorization Matrix

Every public service method enforces authorization guards through `Authorization.java`:

| Operation | System Admin | Receptionist | Attending Doctor | Other Doctors |
| --- | :---: | :---: | :---: | :---: |
| Create Staff Account | ✅ | ❌ | ❌ | ❌ |
| View Staff Account List | ✅ | ❌ | ❌ | ❌ |
| Register / Edit Patient Basic Data | ❌ | ✅ | ✅ | ✅ |
| Activate / Deactivate Patient | ❌ | ✅ | ✅ | ✅ |
| Safe Delete Unused Patient | ❌ | ✅ | ✅ | ✅ |
| Book / Reschedule / Cancel Appointment | ❌ | ✅ | ❌ | ❌ |
| Check-in Arriving Patient | ❌ | ✅ | ❌ | ❌ |
| Complete Billing Checkout & Receipt | ❌ | ✅ | ❌ | ❌ |
| View Financial Reports & Export CSV/JSON | ❌ | ✅ | ❌ | ❌ |
| Accept / Decline Assigned Visit | ❌ | ❌ | ✅ | ❌ |
| Record Diagnosis & Consultation Notes | ❌ | ❌ | ✅ | ❌ |
| Build & Issue Multi-Drug Prescriptions | ❌ | ❌ | ✅ | ❌ |
| Mark Consultation Completed | ❌ | ❌ | ✅ | ❌ |
| Inspect Completed Patient Clinical History | ❌ | ❌ | ✅ | ✅ |
| Inspect In-Progress Patient Consultations | ❌ | ❌ | ✅ | ❌ |
| Configure Personal Working Hours & Time-Off | ❌ | ❌ | ✅ (own only) | ❌ |

---

## 4. Workflow Rules & Implementation Design

### 4.1 Appointment Finite State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: Receptionist books visit
    PENDING --> ACCEPTED: Doctor accepts visit
    PENDING --> DECLINED: Doctor declines visit (slot released)
    PENDING --> CANCELLED: Receptionist cancels visit (slot released)
    ACCEPTED --> CHECKED_IN: Receptionist checks in patient (at or after start time)
    ACCEPTED --> DECLINED: Doctor declines visit (slot released)
    ACCEPTED --> CANCELLED: Receptionist cancels visit (slot released)
    CHECKED_IN --> COMPLETED: Doctor saves consultation & marks completed
    COMPLETED --> CHECKED_OUT: Receptionist records payment & issues receipt
    DECLINED --> [*]
    CANCELLED --> [*]
    CHECKED_OUT --> [*]
```

### 4.2 Appointment Booking Sequence & Conflict Validation

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    participant UI as ReceptionistView
    participant ApptService as AppointmentService
    participant ApptRepo as AppointmentRepository
    participant DB as SQLite Database

    Receptionist->>UI: Enter Patient, Doctor, Date, Interval (e.g. 10:00 - 10:30)
    UI->>ApptService: bookAppointment(session, patientId, doctorId, startsAt, endsAt)
    ApptService->>ApptService: Authorization.requireRole(Role.RECEPTIONIST)
    ApptService->>ApptService: Validate: start < end & half-hour boundaries
    ApptService->>ApptRepo: checkConflicts(doctorId, startsAt, endsAt)
    ApptRepo->>DB: Query appointments: status IN ('PENDING', 'ACCEPTED', 'CHECKED_IN', 'COMPLETED', 'CHECKED_OUT') AND starts_at < endsAt AND ends_at > startsAt
    ApptRepo->>DB: Query doctor_time_off: starts_at < endsAt AND ends_at > startsAt
    alt Conflict Found
        ApptRepo-->>ApptService: Conflict detected
        ApptService-->>UI: throw ScheduleConflictException("Doctor is unavailable")
        UI->>Receptionist: Show conflict feedback
    else Interval Clear
        ApptRepo->>DB: INSERT INTO appointments (status='PENDING', ...)
        DB-->>ApptRepo: Generated Appointment ID
        ApptRepo-->>ApptService: Appointment record
        ApptService-->>UI: Appointment booked
        UI->>Receptionist: Success notice & reload appointments table
    end
```

### 4.3 Check-in to Consultation to Checkout End-to-End Workflow

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    actor Doctor
    participant UI_R as Receptionist Workspace
    participant UI_D as Doctor Workspace
    participant ApptService as AppointmentService
    participant ClinService as ClinicalService
    participant BillService as BillingService
    participant DB as SQLite Database

    Note over Receptionist, UI_R: 1. Patient Arrival & Check-in
    Receptionist->>UI_R: Select Accepted Appointment in Check-in Queue
    UI_R->>ApptService: checkInAppointment(session, appointmentId)
    ApptService->>ApptService: Verify Singapore local time >= appointment.startsAt
    ApptService->>DB: UPDATE appointments SET status = 'CHECKED_IN'
    ApptService-->>UI_R: Status updated

    Note over Doctor, UI_D: 2. Clinical Consultation & Prescriptions
    Doctor->>UI_D: Select Checked-in Visit on Dashboard
    UI_D->>ClinService: saveConsultation(session, apptId, diagnosis, examNotes, followUp)
    ClinService->>ClinService: Authorization.requireDoctorOwnership()
    ClinService->>DB: INSERT INTO clinical_records (...)
    Doctor->>UI_D: Add Prescriptions (Drug, Dosage, Frequency, Duration)
    UI_D->>ClinService: addPrescription(session, recordId, prescription)
    ClinService->>DB: INSERT INTO prescriptions (...)
    Doctor->>UI_D: Click "Mark consultation completed"
    UI_D->>ClinService: completeConsultation(session, apptId)
    ClinService->>DB: UPDATE appointments SET status = 'COMPLETED'
    ClinService-->>UI_D: Consultation finalized & locked

    Note over Receptionist, UI_R: 3. Billing & Daily Receipt Generation
    Receptionist->>UI_R: Open Checkout Tab & Select Completed Visit
    Receptionist->>UI_R: Enter Amount ($45.00) & Method (CARD)
    UI_R->>BillService: completeCheckout(session, apptId, 4500, CARD)
    BillService->>DB: BEGIN TRANSACTION
    BillService->>DB: Generate sequence: RCP-YYYYMMDD-XXXX
    BillService->>DB: INSERT INTO payments (amount_cents=4500, receipt_number=...)
    BillService->>DB: UPDATE appointments SET status = 'CHECKED_OUT'
    BillService->>DB: COMMIT
    BillService-->>UI_R: Receipt Details
    UI_R->>Receptionist: Render Receipt Preview
```

### 4.4 Doctor Calendar vs Infinite Scrolling Agenda

Doctor scheduling provides two distinct view modes:
1. **Interactive Multi-Day Time Grid (`Calendar`)**:
   - Visual grid displaying assigned appointments, shaded working intervals, and purple blocked time-off.
   - Dynamic Singapore red time line showing current time.
   - 100-pixel half-hour rows in full view; 44-pixel rows in compact dashboard view.
2. **Infinite Scrolling Agenda (`Agenda`)**:
   - Bounded chronological appointment stream starting from a selected Singapore anchor date.
   - Keyspacing pagination implemented via `CalendarScheduleCursor` containing `(startsAt, appointmentId)`.
   - SQLite orders rows deterministically by `starts_at ASC, id ASC`, reading one look-ahead row (`LIMIT pageSize + 1`) to accurately set `hasMore` without off-by-one errors or duplicate cards on page boundaries.

### 4.5 Revenue Calculation & Export Sanitization

- **Minor Unit Storage**: Monetary amounts are stored strictly as 64-bit integer cents (`amount_cents INTEGER`), eliminating IEEE-754 floating-point inaccuracies.
- **Strict Boundary Checks**: UI parses amounts using `BigDecimal` and checks for non-negative values. Arithmetic operations in `RevenueReport.java` use `Math.addExact()` to prevent integer overflow.
- **CSV Sanitization**: CSV export properly escapes values containing commas, double quotes (`"` &rarr; `""`), and newline characters (`\r\n`).
- **JSON Sanitization**: JSON export properly escapes Unicode control characters, tabs, quotes, and backslashes according to RFC 8259.

---

## 5. UI and TestFX Conventions

### 5.1 Scene Routing & Sizing

`ApplicationRouter.java` manages primary Stage transitions:
- Window opens **maximized** by default to provide an expansive clinical workspace.
- Minimum stage dimensions are locked at `980 x 640` pixels; restored initial default is `1200 x 760` pixels.
- All routed scenes share the single stylesheet `src/main/resources/nusynapxe/ui.css`.

### 5.2 Design System Components

`UiComponents.java` provides reusable, presentation-only component factories:
- `card(Node... children)`: Styled white content card with subtle border and drop shadow.
- `statusBadge(AppointmentStatus status)`: Produces a `Label` with the base `status-badge` class and semantic status class (`status-pending`, `status-accepted`, `status-checked-in`, `status-completed`, `status-checked-out`, `status-declined`, `status-cancelled`).
- `compactDatePicker()`: Custom styled date picker maintaining standard compact field height with integrated calendar icon.
- `feedback(String message, FeedbackType type)`: Standardized banner appearing below the header, automatically fading after 6 seconds.
- `errorBanner(String message)`: Prominent centered red error alert for form validation failures.

### 5.3 Stable TestFX IDs

Views are constructed programmatically in Java rather than FXML, ensuring every actionable node has an immutable, deterministic `setId()` for headless UI testing:

| Component ID | UI Description |
| --- | --- |
| `login-submit` | Login submit button |
| `setup-submit` | First-run setup submit button |
| `admin-account-submit` | Staff account creation button |
| `reception-patient-open-register` | Open patient registration button |
| `reception-patient-search` | Patient directory search input |
| `reception-patient-table` | Patient directory TableView |
| `reception-patient-view-<id>` | View action button for specific patient ID |
| `reception-book` | Book appointment submit button |
| `reception-checkout` | Complete checkout submit button |
| `doctor-dashboard-day-calendar` | Doctor dashboard single-day time grid |
| `doctor-consultation-save` | Save clinical consultation notes button |
| `doctor-history-patient` | Patient selector in consultation history view |
| `doctor-calendar-block-time` | Open block time-off modal button |
| `doctor-calendar-view-mode` | Calendar vs Agenda toggle button |
| `logout-button` | Top header logout button |\n