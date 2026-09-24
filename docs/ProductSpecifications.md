---
title: Product Specifications & Use Cases
sidebar_label: Requirements & Use Cases
---

# Product Specifications & Use Cases

This document details the user personas, prioritized user stories, and formal use case specifications for NUSynapxe, describing the operational expectations and business rules governing the system.

For the high-level system architecture and implementation design, see the [Architecture & System Design](ArchitectureAndDesign.md). For verification procedures, see the [Testing Strategy](TestingStrategy.md).

---

## 1. Target User Profile & Personas

NUSynapxe is tailored for three distinct healthcare roles operating within a clinic environment:

| Persona | Role | Primary Responsibilities | Technical Proficiency | Key Needs & Pain Points |
| --- | --- | --- | --- | --- |
| **Sarah Tan** | Clinic Receptionist | Front-desk patient registration, phone booking, arrival check-in, billing checkout, payment collection, daily revenue reporting. | Moderate (proficient with office suites, spreadsheets, web portals; prefers keyboard-friendly workflows). | Needs rapid search by phone/NRIC, instant visual conflict detection, clear receipt numbers, and strict protection against viewing sensitive clinical notes. |
| **Dr. Michael Lim** | Attending Physician / Doctor | Conducting consultations, reviewing historical cross-doctor records, writing diagnoses and examination notes, prescribing medications, managing personal clinic schedule and time-off. | Moderate (uses medical record systems, values high efficiency and zero distractions during patient consultations). | Needs master-detail schedule layout, one-click access to patient history, rapid drug prescription builder, and assurance that in-progress consultations remain private. |
| **Alex Chen** | System Administrator | IT provisioning, clinic onboarding, initial system bootstrap, staff account lifecycle management, ensuring software integrity. | High (familiar with system setup, security policies, password standards). | Needs tamper-proof password storage, role isolation, clean database migration paths, and clear separation from clinical data. |

---

## 2. Prioritized User Stories

Priorities are designated as follows:
- `* * *` : Essential / High Priority (Core MVP)
- `* *`   : Important / Medium Priority
- `*`     : Desirable / Low Priority

| Priority | User Role | Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | System Admin | As an administrator, I want to create the root admin account on first launch, so that the clinic database is initialized securely. | First-run wizard detects empty user table, enforces minimum 8-character password, and prevents further admin setup. |
| `* * *` | System Admin | As an administrator, I want to provision staff accounts with Doctor or Receptionist roles, so that employees can access their respective workspaces. | Creates accounts with unique usernames and role assignments. System Admin cannot access clinical records. |
| `* * *` | Staff Member | As a clinic staff member, I want to authenticate with my username and password, so that I am routed directly to my authorized workspace. | PBKDF2 hashing with per-account salt. Session stored strictly in volatile memory. Invalid login returns generic security message. |
| `* * *` | Receptionist | As a receptionist, I want to register new patients with standardized identity documents (NRIC, FIN, Passport), so that patient records are accurate and deduplicated. | Validates syntax: NRIC `[ST][0-9]{7}[A-Z]`, FIN `[FGM][0-9]{7}[A-Z]`, Passport 5–20 alphanumeric. Locks Singapore country code for NRIC/FIN. Rejects duplicates across `(type, country, number)`. |
| `* * *` | Receptionist | As a receptionist, I want to search patients by name, NRIC/FIN, phone, or email, so that I can quickly locate patient files without remembering internal IDs. | Case-insensitive multi-field search with SQL wildcard escaping and deterministic name-then-ID sorting. |
| `* * *` | Receptionist | As a receptionist, I want to book appointments across doctors in 30-minute intervals, so that patient visits are scheduled without conflicts. | Checks that `start < end` and verifies that no active booking or doctor time-off overlaps the interval: `existing_start < new_end && existing_end > new_start`. Rejects bookings for inactive patients. |
| `* * *` | Receptionist | As a receptionist, I want to check in patients whose scheduled appointment time has arrived, so that doctors are notified of patient arrival. | Check-in allowed only at or after scheduled start time in Singapore local time (`Asia/Singapore`). Transitions status from `ACCEPTED` to `CHECKED_IN`. |
| `* * *` | Receptionist | As a receptionist, I want to complete checkout billing and record payment methods, so that patients receive an itemized receipt. | Accepts positive minor currency units (cents), records payment method (Cash, Card, Transfer, Other), transitions status to `CHECKED_OUT`, and generates sequential daily receipt number. |
| `* * *` | Doctor | As a doctor, I want a daily master-detail dashboard schedule, so that I can see today's agenda alongside the selected patient's clinical file. | Left pane displays scrollable 24-hour visual schedule with live Singapore clock line; right pane displays status-aware consultation workspace. |
| `* * *` | Doctor | As a doctor, I want to accept or decline pending appointments, so that my clinical availability is under my direct control. | Doctors can accept or decline assigned appointments. Declining releases the time slot for future bookings while preserving audit history. |
| `* * *` | Doctor | As a doctor, I want to record diagnosis, consultation examination notes, and follow-up notes for a checked-in patient, so that clinical care is documented. | Only the assigned doctor can author consultation notes. Notes are saved atomically with the consultation. |
| `* * *` | Doctor | As a doctor, I want to prescribe multiple medications with dosage, frequency, and instructions, so that patients receive their prescriptions upon checkout. | Itemized prescription builder supporting multiple drugs per consultation with non-empty drug name, dosage, frequency, and duration. |
| `* * *` | Doctor | As a doctor, I want to mark consultations completed, so that the appointment transfers to the receptionist for payment collection. | Atomically transitions status from `CHECKED_IN` to `COMPLETED` and locks clinical consultation records. |
| `* *` | Doctor | As a doctor, I want to view a patient's historical consultations conducted across all clinic doctors, so that I understand past medical history for continuity of care. | Read-only consultation history browser showing completed/checked-out visits across all physicians. Excludes in-progress consultations of other doctors. |
| `* *` | Doctor | As a doctor, I want to block personal time-off on my calendar, so that receptionists cannot schedule appointments during my leave. | Validates non-overlapping intervals, renders purple blocked time cards, and enforces schedule conflict detection. |
| `* *` | Doctor | As a doctor, I want to configure my recurring daily working hours and lunch breaks, so that my calendar displays visual working intervals. | Configures daily intervals up to 1440 minutes, supporting split intervals (e.g. morning and afternoon shifts around lunch). Visual shading only; does not block emergencies. |
| `* *` | Doctor | As a doctor, I want to browse my schedule in an infinite-scrolling chronological agenda view, so that I can review upcoming appointments across weeks. | Keyspacing pagination with `(startsAt, appointmentId)` cursor preventing duplicates or skipped records. |
| `* *` | Receptionist | As a receptionist, I want to generate revenue reports for any custom date range, so that clinic finances can be audited. | Aggregates successful payments, calculates total revenue in exact cents, breaks down revenue by payment method and doctor, and displays itemized receipt table. |
| `* *` | Receptionist | As a receptionist, I want to export revenue reports to CSV and JSON formats, so that financial data can be ingested into external accounting systems. | Sanitizes CSV escaping (quotes, commas, line breaks) and JSON formatting without altering stored records. |
| `* *` | Staff Member | As a receptionist or doctor, I want to deactivate inactive patients rather than deleting them, so that clinical and financial audit trails are preserved. | Soft-deactivation sets `active = 0`. Preserves patient ID, historical appointments, and receipts. Blocks future appointment bookings. |
| `*` | Staff Member | As a receptionist or doctor, I want to safely delete accidental or duplicate patient registrations that have no medical records, so that data hygiene is maintained. | Preflight blocker checks count foreign-key relationships across 6 categories. Blocks deletion and displays detailed category counts if any related record exists. |

---

## 3. Product Specifications & Formal Use Cases

### UC01: System First-Run Setup & Administrator Provisioning
- **Actor**: System Administrator
- **Preconditions**: Database is uninitialized or user table contains zero records.
- **Trigger**: Application is launched for the first time.
- **Main Success Scenario**:
  1. Application detects zero registered accounts and displays the setup wizard.
  2. Administrator enters username, password (at least 8 non-blank characters), and matching confirmation.
  3. Administrator selects **Create System Admin**.
  4. System hashes password with PBKDF2WithHmacSHA256 and unique random salt, writes administrator row to `users`, and redirects to Login screen.
- **Extensions / Alternative Flows**:
  - *2a. Password is shorter than 8 characters or confirmation does not match.*
    - System displays validation error banner at top of window. Form remains open.
  - *4a. User attempts to access setup wizard after an administrator exists.*
    - System denies access and redirects immediately to Login.

### UC02: Staff Account Management
- **Actor**: System Administrator
- **Preconditions**: Administrator is logged into System Admin Workspace.
- **Main Success Scenario**:
  1. Administrator enters unique username, clinician/staff display name, role (`Doctor` or `Receptionist`), and initial password.
  2. Administrator submits form via **Create account**.
  3. System validates input, generates password salt and hash, persists account, and refreshes the staff accounts table.
- **Extensions**:
  - *2a. Username already exists.*
    - System rejects creation and displays `Username is already taken`.

### UC03: Patient Registration & NRIC/FIN Document Validation
- **Actor**: Clinic Receptionist or Doctor
- **Preconditions**: User is logged in with Receptionist or Doctor role.
- **Main Success Scenario**:
  1. User navigates to Patient Directory and selects **Register new patient**.
  2. User selects Identity Type (`NRIC`, `FIN`, `PASSPORT`, `OTHER`).
  3. System auto-selects and locks country to Singapore (`SG`) if NRIC or FIN is chosen.
  4. User enters Document Number, Full Name, Date of Birth, Sex, Phone Country Code, Phone Number, Email, and Address.
  5. System computes patient age dynamically based on Singapore calendar date.
  6. User submits form via **Register patient**.
  7. System validates syntax, normalizes document number, verifies global tuple uniqueness `(identity_type, issuing_country, identity_number)`, inserts patient record with `active = 1`, and returns to Directory.
- **Extensions**:
  - *4a. Document syntax invalid (e.g. NRIC does not match `[ST][0-9]{7}[A-Z]`).*
    - System displays descriptive validation message and keeps form open.
  - *6a. Identity tuple already exists in database.*
    - System rejects write with `A patient with this identity document already exists` without echoing sensitive document details in logs.

### UC04: Safe Patient Deletion with Preflight Blocker Inspection
- **Actor**: Clinic Receptionist or Doctor
- **Preconditions**: User opens existing patient record in Patient Directory.
- **Main Success Scenario**:
  1. User selects **Delete patient**.
  2. System executes preflight blocker inspection across appointments, clinical records, prescriptions, payments, receipts, and foreign keys.
  3. If all counts are zero, system displays confirmation dialog.
  4. User confirms permanent deletion.
  5. System executes atomic transaction verifying zero blockers and deleting patient row.
- **Extensions**:
  - *2a. Blocker check detects existing records (e.g. 2 appointments, 1 payment).*
    - System aborts deletion and opens the `Delete Blocked` dialog displaying itemized category counts and recommending deactivation instead.

### UC05: Multi-Day Clinic Appointment Booking & Conflict Detection
- **Actor**: Clinic Receptionist
- **Preconditions**: Receptionist is logged in; active patient and doctor exist.
- **Main Success Scenario**:
  1. Receptionist opens Appointments booking form or clicks empty slot in Calendar.
  2. Receptionist selects active Patient, Doctor, Date, Start Time (`00:00`–`23:30`), and End Time.
  3. Receptionist submits via **Book appointment**.
  4. System executes transactional conflict query:
     `existing_start < new_end AND existing_end > new_start` across non-cancelled appointments and doctor time-off.
  5. System inserts appointment with initial state `PENDING` and refreshes dashboard.
- **Extensions**:
  - *2a. Patient is marked INACTIVE.*
    - System excludes patient from search suggestions; service layer rejects booking.
  - *4a. Interval overlaps an existing active visit or doctor time-off.*
    - System rejects booking with scheduling conflict notice.

### UC06: Patient Check-in with Singapore Time Validation
- **Actor**: Clinic Receptionist
- **Preconditions**: Appointment is in `ACCEPTED` state.
- **Main Success Scenario**:
  1. Patient arrives at clinic; Receptionist opens **Check-in Queue**.
  2. Receptionist selects appointment.
  3. If current Singapore local time is at or after scheduled start time, **Check in patient** action is enabled.
  4. Receptionist clicks **Check in patient**.
  5. System transitions status to `CHECKED_IN` and updates queue.
- **Extensions**:
  - *3a. Current time is earlier than scheduled start time.*
    - Check-in action is disabled with message indicating check-in is only available at or after start time.

### UC07: Doctor Clinical Consultation & Multi-Medication Prescription
- **Actor**: Assigned Doctor
- **Preconditions**: Appointment is in `CHECKED_IN` state and assigned to the authenticated doctor.
- **Main Success Scenario**:
  1. Doctor selects appointment in Dashboard.
  2. Doctor records Diagnosis, Examination Notes, and Follow-up Instructions.
  3. Doctor clicks **Save consultation**.
  4. Doctor enters Medication Name, Dosage, Frequency, Duration, and Instructions, and clicks **Add prescription**.
  5. Doctor reviews completed consultation and selects **Mark consultation completed**.
  6. System atomically transitions appointment to `COMPLETED` and locks clinical notes.
- **Extensions**:
  - *1a. Different doctor attempts to access or modify consultation.*
    - Service layer throws `AccessDeniedException`; UI detail card remains inaccessible.

### UC08: Cross-Doctor Historical Consultation Inspection
- **Actor**: Any Authenticated Doctor
- **Preconditions**: Patient has completed past visits with one or more clinic doctors.
- **Main Success Scenario**:
  1. Doctor opens Patient Directory and clicks **Consultation history** for selected patient.
  2. System queries terminal clinical records (`COMPLETED` or `CHECKED_OUT`) ordered newest first.
  3. Doctor reviews historical diagnoses, clinical notes, attending physician name, and issued prescriptions.
- **Extensions**:
  - *2a. Patient has ongoing/in-progress consultations with another doctor.*
    - Query projection strictly excludes non-terminal appointments, protecting active consultation privacy.

### UC09: Checkout Billing Settlement & Receipt Sequence Generation
- **Actor**: Clinic Receptionist
- **Preconditions**: Appointment is in `COMPLETED` state.
- **Main Success Scenario**:
  1. Receptionist selects completed visit in **Checkout** tab.
  2. Receptionist enters payment charge (e.g. `45.00`) and selects payment method (`CASH`, `CARD`, `TRANSFER`, `OTHER`).
  3. Receptionist clicks **Complete checkout**.
  4. System converts dollars to integer cents (`4500`), persists payment, generates unique daily receipt number (`RCP-<date>-<seq>`), and transitions status to `CHECKED_OUT`.
  5. System displays receipt preview with Singapore timestamp.

### UC10: Revenue Auditing & CSV/JSON Data Export
- **Actor**: Clinic Receptionist
- **Preconditions**: Receipts exist in database.
- **Main Success Scenario**:
  1. Receptionist navigates to **Revenue Reports**, selects date range (From/To), and optional filters (Doctor, Patient, Payment Method).
  2. Receptionist clicks **Generate report**.
  3. System aggregates total revenue, breakdown by method, breakdown by clinician, and renders receipt table.
  4. Receptionist clicks **Export CSV** or **Export JSON**.
  5. System serializes report with proper escaping and prompts user to save file.\n