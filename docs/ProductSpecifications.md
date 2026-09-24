---
title: Product Specifications
sidebar_label: Product Specifications
---

# Product Specifications

This document defines the formal behavioral specifications, target personas, prioritized user stories, use cases, and illustrative Gherkin feature scenarios for NUSynapxe.

For architectural decisions, class layouts, and database schema, see [Architecture & System Design](ArchitectureAndDesign.md). For verification procedures and test execution, see [Testing Strategy](TestingStrategy.md).

---

## 1. Target User Profile & Personas

NUSynapxe is designed for healthcare professionals operating within outpatient medical practices, general practitioner (GP) clinics, and specialist medical centres:

| Persona Role | Primary Operational Responsibilities | Technical Profile | Key Requirements & Ergonomic Needs |
| --- | --- | --- | --- |
| **Clinic Receptionist** | Patient registration and verification, appointment booking and rescheduling, arrival queue management, checkout billing, payment processing, and daily financial revenue reporting. | Comfortable with modern desktop applications, office suites, and web services; prioritizes keyboard navigation and rapid data entry. | Fast patient lookup across multiple identifiers (name, phone, NRIC), instantaneous visual calendar conflict detection, clear receipt numbers, and strict protection against viewing sensitive clinical notes. |
| **Attending Physician / Doctor** | Patient clinical consultations, cross-doctor historical records review, diagnostic charting, clinical examination findings, itemized medication prescribing, personal calendar availability, and time-off blocking. | Proficient with clinical software; demands zero unnecessary clicks, rapid responsive search, and distraction-free documentation during consultations. | Responsive daily schedule view, one-click access to historical medical notes across all clinic colleagues, rapid prescription builder with autocomplete, and complete confidentiality for in-progress drafts. |
| **System Administrator** | Software deployment, first-run database bootstrap, staff credential provisioning, account lifecycle maintenance, security enforcement, and database migration monitoring. | Experienced with system administration, IT security protocols, terminal tooling, and database operations. | Tamper-proof credential storage (PBKDF2 salting/hashing), strict role-based access control, zero access to private patient clinical data, and clean transactional database migration paths. |

---

## 2. Prioritized User Stories

Priorities are designated as follows:
- `* * *` : Essential / High Priority (Core MVP)
- `* *`   : Important / Medium Priority
- `*`     : Desirable / Low Priority

### 2.1 System Administration & Security Stories

| Priority | Persona Role | User Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | System Administrator | As an administrator, I want to initialize the root administrator account on first system launch, so that the clinic database is bootstrapped securely. | First-run setup wizard triggers only when the user table is empty. Requires username and password with a minimum length of 8 characters. Rejects subsequent setup attempts once configured. |
| `* * *` | System Administrator | As an administrator, I want to provision staff accounts with distinct Doctor or Receptionist roles, so that employees can access their respective clinical or front-desk tools. | Creates unique user credentials with specified roles. System administrators cannot access patient medical records or book appointments. |
| `* * *` | Staff Member | As a clinic staff member, I want to authenticate securely with my username and password, so that I am routed directly to my authorized workspace. | Passwords hashed using PBKDF2WithHmacSHA256 with per-account cryptographically secure salt. Sessions reside purely in volatile memory. Generic error returned on authentication failure. |
| `* *` | System Administrator | As an administrator, I want to view all active staff accounts in a centralized directory, so that I can audit staff access and account statuses. | Renders compact table displaying username, staff display name, assigned role, and active status. Plaintext passwords and salts are never exposed. |

### 2.2 Patient Identity & Directory Management Stories

| Priority | Persona Role | User Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | Clinic Receptionist | As a receptionist, I want to register new patients with standardized identity documents (NRIC, FIN, Passport), so that patient records are accurate and deduplicated. | Validates syntax: NRIC `[ST][0-9]{7}[A-Z]`, FIN `[FGM][0-9]{7}[A-Z]`, Passport 5–20 alphanumeric characters. Automatically locks country to Singapore (`SG`) for NRIC and FIN. |
| `* * *` | Clinic Receptionist | As a receptionist, I want the system to reject duplicate identity documents, so that duplicate patient files are prevented. | Enforces global uniqueness across the normalized tuple `(identity_type, issuing_country, identity_number)`. Returns a non-sensitive validation warning upon collision. |
| `* * *` | Clinic Receptionist | As a receptionist, I want to search patients by name, NRIC/FIN, phone, or email, so that I can quickly locate patient profiles during calls and walk-ins. | Case-insensitive multi-field search with SQL wildcard escaping. Results ordered deterministically by patient name, then numeric Patient ID. |
| `* * *` | Attending Physician | As a doctor, I want to search and view patient administrative details from my own workspace, so that I can verify patient identity without navigating away. | Embedded shared Patient Directory view accessible to doctors with administrative viewing and editing permissions. Excludes financial checkout functions. |
| `* *` | Clinic Receptionist | As a receptionist, I want patient telephone numbers to include international calling codes, so that foreign patients can be contacted reliably. | Normalizes phone fields into editable country code (`^[1-9][0-9]{0,2}$`) and digits-only local number using Google libphonenumber metadata. |
| `* *` | Staff Member | As a clinic staff member, I want to edit patient contact and residential information, so that patient records remain up-to-date. | Editable form preserving immutable Patient ID. Updates to document fields undergo syntax and duplicate validation before committing. |
| `* *` | Staff Member | As a clinic staff member, I want to deactivate inactive patients rather than deleting them, so that medical and audit histories are preserved. | Soft deactivation updates `active = 0`. Retains historical appointments, clinical records, and receipts while blocking future appointment scheduling. |
| `* *` | Staff Member | As a clinic staff member, I want to reactivate previously deactivated patients, so that returning patients can schedule visits without creating duplicate profiles. | Reactivation updates `active = 1`, immediately restoring scheduling eligibility while maintaining previous visit records. |
| `*` | Staff Member | As a clinic staff member, I want to safely delete accidental or duplicate patient registrations that have no medical records, so that data hygiene is maintained. | Preflight blocker inspection tallies foreign-key relationships across all related entity categories. Deletion is permitted only when all blocker counts are zero; cascade deletes are strictly prohibited. |

### 2.3 Appointment Scheduling & Queue Stories

| Priority | Persona Role | User Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | Clinic Receptionist | As a receptionist, I want to book patient appointments across clinic doctors in 30-minute intervals, so that consultations are scheduled without conflicts. | Checks that `start < end` and verifies that no active appointment or doctor time-off overlaps: `existing_start < new_end && existing_end > new_start`. Blocks inactive patients. Initial status is `PENDING`. |
| `* * *` | Attending Physician | As a doctor, I want to book an appointment directly from my calendar, so that follow-up or ad-hoc visits are immediately confirmed on my schedule. | Creates appointment for the authenticated doctor directly with initial status `ACCEPTED`. Applies the same schedule and time-off conflict checks. |
| `* * *` | Clinic Receptionist | As a receptionist, I want to check in patients whose scheduled appointment time has arrived, so that doctors are notified of patient arrival. | Check-in is permitted only at or after the scheduled appointment start time in Singapore local time (`Asia/Singapore`). Transitions status from `ACCEPTED` to `CHECKED_IN`. |
| `* * *` | Attending Physician | As a doctor, I want to review pending appointments assigned to me and accept or decline them, so that my clinical schedule is managed with my approval. | Doctor can accept (transitions to `ACCEPTED`) or decline (transitions to `DECLINED`). Declining releases the time slot for future bookings while preserving audit trails. |
| `* *` | Clinic Receptionist | As a receptionist, I want to reschedule an appointment to a new date or time, so that patient schedule changes are accommodated. | Modal dialog displays patient details and validates the new interval against doctor availability and conflicts before committing. |
| `* *` | Clinic Receptionist | As a receptionist, I want to cancel an appointment if a patient calls to cancel, so that the time slot is released for other patients. | Cancelling transitions status to `CANCELLED` and immediately frees the time slot for new bookings while retaining the record for reporting. |
| `* *` | Clinic Receptionist | As a receptionist, I want a unified Check-in Queue view defaulting to today's date, so that I can monitor waiting patients at a glance. | Displays accepted and checked-in appointments for the current Singapore date, sorted chronologically with live status badges. |

### 2.4 Clinical Consultation & Prescription Management Stories

| Priority | Persona Role | User Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | Attending Physician | As a doctor, I want a daily master-detail dashboard schedule, so that I can see today's agenda alongside the selected patient's clinical file. | Left pane displays scrollable 24-hour visual schedule with live Singapore clock line; right pane displays status-aware consultation workspace. |
| `* * *` | Attending Physician | As a doctor, I want to record diagnosis, consultation examination notes, and follow-up instructions for a checked-in patient, so that clinical care is documented. | Clinical records are editable only by the assigned doctor for visits in `CHECKED_IN` status. Notes are saved atomically with the consultation. |
| `* * *` | Attending Physician | As a doctor, I want to prescribe multiple medications with dosage, frequency, duration, and instructions, so that patients receive their prescriptions upon checkout. | Itemized prescription builder supporting multiple drugs per consultation. Requires non-empty drug name, dosage, frequency, duration, and instructions. |
| `* * *` | Attending Physician | As a doctor, I want to mark consultations completed, so that the appointment transfers to the receptionist for payment collection. | Atomically transitions status from `CHECKED_IN` to `COMPLETED` and routes the visit to the front-desk checkout queue for payment collection. |
| `* *` | Attending Physician | As a doctor, I want to inspect a patient's historical consultations conducted across all clinic doctors, so that I understand past medical history for continuity of care. | Read-only consultation history browser displaying completed/checked-out visits across all clinic physicians. Strictly excludes in-progress consultations of other doctors. |
| `* *` | Clinic Staff | As clinic staff, I want strict confidentiality boundaries between front-desk and clinical data, so that receptionists cannot view clinical notes. | Database queries use projection-specific SQL. Receptionist appointment queries join only administrative patient data and exclude clinical record tables. |

### 2.5 Physician Scheduling, Time-Off & Availability Stories

| Priority | Persona Role | User Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | Attending Physician | As a doctor, I want to block personal time-off on my calendar, so that receptionists cannot schedule appointments during my leave or administrative duties. | Validates non-overlapping intervals, renders purple blocked time cards, and enforces schedule conflict detection across booking operations. |
| `* *` | Attending Physician | As a doctor, I want to remove previously scheduled time-off blocks if my plans change, so that my calendar reopens for patient appointments. | Removal verifies ownership against the authenticated doctor session before deleting the time-off record. |
| `* *` | Attending Physician | As a doctor, I want to configure my recurring daily working hours and lunch breaks, so that my calendar displays visual working intervals. | Configures daily intervals up to 1440 minutes, supporting split intervals (e.g. morning and afternoon shifts around lunch). Visual shading only; does not block emergency bookings. |
| `* *` | Attending Physician | As a doctor, I want an infinite-scrolling agenda stream anchored to a chosen date, so that I can browse future appointments sequentially without pagination friction. | Dynamically loads appointment batches grouped by clinic date, displaying time ranges, patient names, and status badges. |

### 2.6 Financial Billing, Receipts & Revenue Reporting Stories

| Priority | Persona Role | User Story Text | Business Rationale & Acceptance Criteria |
| :---: | --- | --- | --- |
| `* * *` | Clinic Receptionist | As a receptionist, I want to collect payment for completed visits and issue receipts, so that billing is finalized immediately upon patient departure. | Enforces positive amount input, supports Cash, Card, Transfer, and Other payment methods, and generates sequential daily receipts (formatted as `Receipt YYYY-MM-DD-####` in receipt details and `YYYY-MM-DD-N` in the history list). |
| `* *` | Clinic Receptionist | As a receptionist, I want to browse past receipts and review individual transaction details, so that patient billing inquiries can be resolved. | Receipt directory showing receipt ID, date, patient, doctor, payment method, and amount. Read-only audit view. |
| `* *` | Clinic Receptionist | As a receptionist, I want to generate revenue reports across date ranges, doctors, and payment methods, so that clinic income can be audited. | Aggregates successful payments within inclusive date ranges. Breaks down totals by payment method and attending clinician. |
| `* *` | Clinic Receptionist | As a receptionist, I want to export financial summaries to CSV and JSON formats, so that financial data can be imported into accounting systems. | Exports UTF-8 encoded files formatted according to RFC 4180 CSV specifications (quoting delimiters and escaping embedded quotes) and structured JSON for accounting ingestion. |

---

## 3. Formal Use Cases

### UC01: System Bootstrap and Root Administrator Setup
- **Actor**: Unauthenticated User (Clinic Installer)
- **Preconditions**: Clinic database is empty (no accounts exist in `users` table).
- **Main Success Scenario**:
  1. User launches application.
  2. System detects uninitialized database and displays **Create the first System Admin account** view.
  3. User enters Administrator Username, Password (minimum 8 characters), and matching Confirmation Password.
  4. System hashes password with PBKDF2WithHmacSHA256 and unique random salt, writes administrator row to `users`, and redirects to Login screen.
- **Extensions / Alternative Flows**:
  - 2a. Password is shorter than 8 characters or confirmation does not match.
    - System displays validation error banner at top of window. Form remains open.
  - 4a. User attempts to access setup wizard after an administrator exists.
    - System denies access and redirects immediately to Login screen.

### UC02: Staff Account Creation & Directory Listing
- **Actor**: System Administrator
- **Preconditions**: Administrator is logged into System Admin Workspace.
- **Main Success Scenario**:
  1. Administrator enters unique username, staff display name, role (`Doctor` or `Receptionist`), and initial password.
  2. Administrator submits form via **Create account**.
  3. System validates input, generates password salt and hash, persists account, and refreshes the staff accounts table.
- **Extensions**:
  - 2a. Username already exists.
    - System rejects creation and displays `The username is already in use or the account could not be created`.
  - 2b. Password contains fewer than 8 non-blank characters.
    - System rejects creation and displays `Password must contain at least 8 non-blank characters`.

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
  - 4a. Document syntax invalid (e.g. NRIC does not match `[ST][0-9]{7}[A-Z]`).
    - System displays descriptive validation message and keeps form open.
  - 4b. Telephone country code invalid or telephone number contains non-digits.
    - System displays phone format validation notice and keeps form open.
  - 6a. Identity tuple already exists in database.
    - System rejects write with `A patient with this identity document already exists` without echoing sensitive document details in logs.

### UC04: Patient Basic Data Edit & Telephone Normalization
- **Actor**: Clinic Receptionist or Doctor
- **Preconditions**: User opens existing patient record in Patient Directory.
- **Main Success Scenario**:
  1. User clicks **Edit** on patient profile.
  2. User updates contact details, telephone calling code, address, height, or weight.
  3. User clicks **Save changes**.
  4. System validates inputs, commits changes to database, and displays updated profile.
- **Extensions**:
  - 2a. User updates document number to one that collides with another registered patient.
    - System aborts update and displays duplicate identity error. Form remains open with user edits preserved.
  - 2b. User clicks **Discard changes**.
    - System discards unsaved form inputs and reloads the existing persisted profile.

### UC05: Patient Status Deactivation and Reactivation
- **Actor**: Clinic Receptionist or Doctor
- **Preconditions**: User views an existing patient profile in Patient Directory.
- **Main Success Scenario (Deactivation)**:
  1. User selects **Deactivate patient**.
  2. System sets `active = 0` on patient record and updates status badge to `INACTIVE`.
  3. Patient is immediately excluded from new appointment booking selectors.
- **Alternative Flow (Reactivation)**:
  1. User selects **Activate patient** on an inactive patient profile.
  2. System sets `active = 1` on patient record and updates status badge to `ACTIVE`.
  3. Patient is immediately re-enabled for appointment scheduling.

### UC06: Safe Patient Deletion with Preflight Blocker Inspection
- **Actor**: Clinic Receptionist or Doctor
- **Preconditions**: User opens existing patient record in Patient Directory.
- **Main Success Scenario**:
  1. User selects **Delete patient**.
  2. System executes preflight blocker inspection across appointments, clinical records, prescriptions, payments, receipts, and foreign keys.
  3. All counts return zero; system displays permanent deletion confirmation dialog.
  4. User confirms deletion.
  5. System executes atomic transaction verifying zero blockers and deleting patient row.
- **Extensions**:
  - 2a. Blocker check detects existing records (e.g. 2 appointments, 1 payment).
    - System aborts deletion and opens the `Delete Blocked` dialog displaying itemized category counts and recommending deactivation instead.

### UC07: Multi-Day Clinic Appointment Booking & Conflict Detection
- **Actor**: Clinic Receptionist or Doctor (via Doctor Calendar **Add appointment**)
- **Preconditions**: User is logged in; active patient and doctor exist.
- **Main Success Scenario**:
  1. User opens Appointments booking form or clicks empty slot in Calendar (or uses **Add appointment** in Doctor Calendar).
  2. User selects active Patient, Doctor, Date, Start Time (`00:00`–`23:30`), and End Time.
  3. User submits via **Book appointment**.
  4. System executes transactional conflict query:
     `existing_start < new_end AND existing_end > new_start` across non-cancelled appointments and doctor time-off.
  5. System inserts appointment with initial state `PENDING` (when booked by Receptionist) or `ACCEPTED` (when booked directly by the assigned Doctor) and refreshes schedule.
- **Extensions**:
  - 2a. Patient is marked INACTIVE.
    - System excludes patient from search suggestions; service layer rejects booking.
  - 4a. Interval overlaps an existing active visit or doctor time-off.
    - System rejects booking with scheduling conflict notice.

### UC08: Appointment Rescheduling and Cancellation
- **Actor**: Clinic Receptionist
- **Preconditions**: Appointment exists in `PENDING` or `ACCEPTED` state.
- **Main Success Scenario (Rescheduling)**:
  1. Receptionist opens appointment and selects **Reschedule**.
  2. Receptionist selects new date and time slot.
  3. System validates interval availability and updates `starts_at` and `ends_at`.
- **Alternative Flow (Cancellation)**:
  1. Receptionist opens appointment and selects **Cancel appointment**.
  2. Receptionist confirms cancellation.
  3. System transitions status to `CANCELLED`, immediately freeing the slot while preserving the record for auditing.

### UC09: Patient Check-in with Singapore Time Validation
- **Actor**: Clinic Receptionist
- **Preconditions**: Appointment is in `ACCEPTED` state.
- **Main Success Scenario**:
  1. Patient arrives at clinic; Receptionist opens **Check-in Queue**.
  2. Receptionist selects appointment.
  3. Current Singapore local time is at or after scheduled start time; **Check in patient** action is enabled.
  4. Receptionist clicks **Check in patient**.
  5. System transitions status to `CHECKED_IN` and updates queue.
- **Extensions**:
  - 3a. Current time is earlier than scheduled start time.
    - **Check in patient** button is disabled in the appointment dialog until the appointment start time is reached.

### UC10: Doctor Clinical Consultation & Multi-Medication Prescription
- **Actor**: Assigned Doctor
- **Preconditions**: Appointment is in `CHECKED_IN` state and assigned to the authenticated doctor.
- **Main Success Scenario**:
  1. Doctor selects appointment in Dashboard.
  2. Doctor records Diagnosis, Examination Notes, and Follow-up Instructions.
  3. Doctor clicks **Save consultation**.
  4. Doctor enters Medication Name, Dosage, Frequency, Duration, and Instructions, and clicks **Add prescription**.
  5. Doctor reviews completed consultation and selects **Mark consultation completed**.
  6. System atomically transitions appointment to `COMPLETED` and transfers the visit to the reception checkout queue.
- **Extensions**:
  - 1a. Different doctor attempts to access or modify consultation.
    - Service layer throws `AccessDeniedException`; UI detail card remains inaccessible.
  - 4a. Doctor attempts to submit prescription with empty required field (medication name, dosage, frequency, duration, or instructions).
    - System highlights required fields and prevents addition.

### UC11: Cross-Doctor Historical Consultation Inspection
- **Actor**: Any Authenticated Doctor
- **Preconditions**: Patient has completed past visits with one or more clinic doctors.
- **Main Success Scenario**:
  1. Doctor opens Patient Directory and clicks **Consultation history** for selected patient.
  2. System queries terminal clinical records (`COMPLETED` or `CHECKED_OUT`) ordered newest first.
  3. Doctor reviews historical diagnoses, clinical notes, attending physician name, and issued prescriptions.
- **Extensions**:
  - 2a. Patient has ongoing/in-progress consultations with another doctor.
    - Query projection strictly excludes non-terminal appointments, protecting active consultation privacy.

### UC12: Doctor Personal Time-Off Blocking & Availability Updates
- **Actor**: Attending Physician
- **Preconditions**: Doctor is logged into Doctor Workspace.
- **Main Success Scenario**:
  1. Doctor navigates to **Calendar** and clicks **Block time**.
  2. Doctor selects the Date and enters the Start Time and End Time.
  3. System verifies non-overlapping intervals and commits time-off block.
  4. Calendar renders purple time-off block; front-desk bookings during this period are rejected.
- **Extensions**:
  - 2a. Time-off block overlaps an existing active appointment.
    - System rejects block and prompts doctor to reschedule or cancel existing appointments first.

### UC13: Doctor Working Intervals & Split Lunch Break Configuration
- **Actor**: Attending Physician
- **Preconditions**: Doctor is logged into Doctor Workspace.
- **Main Success Scenario**:
  1. Doctor navigates to **Calendar** > **Settings**.
  2. Doctor configures daily shift intervals (e.g. `08:30`–`12:30` and `13:30`–`17:30`, creating a 1-hour lunch break).
  3. Doctor clicks **Save schedule**.
  4. System validates intervals do not overlap and persists working intervals atomically.
  5. Calendar updates background shading to reflect working shifts and lunch breaks.

### UC14: Doctor Infinite-Scrolling Agenda Browsing
- **Actor**: Attending Physician
- **Preconditions**: Doctor is logged into Doctor Workspace.
- **Main Success Scenario**:
  1. Doctor switches calendar view mode to **Agenda**.
  2. Doctor selects anchor date; system fetches first page of upcoming appointments.
  3. Doctor scrolls downward; system detects scroll threshold and requests subsequent page using cursor `(startsAt, appointmentId)`.
  4. System appends newly loaded appointments seamlessly without duplicate headers or skipped items.

### UC15: Checkout Billing Settlement & Receipt Sequence Generation
- **Actor**: Clinic Receptionist
- **Preconditions**: Appointment is in `COMPLETED` state.
- **Main Success Scenario**:
  1. Receptionist selects completed visit in **Checkout** tab.
  2. Receptionist enters payment charge (e.g. `45.00`) and selects payment method (`CASH`, `CARD`, `TRANSFER`, `OTHER`).
  3. Receptionist clicks **Complete checkout**.
  4. System converts dollars to integer cents (`4500`), persists payment, generates unique daily receipt sequence number (e.g. `2026-10-15-1`), and transitions status to `CHECKED_OUT`.
  5. System displays receipt preview with Singapore timestamp.
- **Extensions**:
  - 2a. Entered amount is negative, zero, or contains invalid characters.
    - System rejects checkout and highlights amount field.

### UC16: Revenue Auditing & CSV/JSON Data Export
- **Actor**: Clinic Receptionist
- **Preconditions**: Receptionist is logged into Receptionist Workspace.
- **Main Success Scenario**:
  1. Receptionist opens **Revenue Reports**.
  2. Receptionist chooses Date Range (`From` and `To` dates).
  3. Receptionist selects optional filter criteria (Doctor, Payment Method).
  4. Receptionist clicks **Generate report**.
  5. System returns aggregated financial summaries: total gross revenue, transaction counts, breakdowns by clinician, and breakdowns by payment method.
  6. Receptionist clicks **Export CSV** or **Export JSON**.
  7. System serializes dataset into standard formatted file and saves to local file system.
- **Extensions**:
  - 2a. `From` date is chronologically after `To` date.
    - System displays range validation warning and prevents query submission.

---

## 4. Gherkin Product Specifications

```gherkin
Feature: System Initialization and Authentication
  As a clinic deployment administrator or staff member
  I want secure initial setup and cryptographic authentication
  So that unauthorized access to medical records is prevented

  Scenario: First-Run Administrator Bootstrap
    Given the clinic database is uninitialized with zero user accounts
    When the installer launches the NUSynapxe application
    Then the initial setup wizard "Create the first System Admin account" is presented
    When the administrator provides username "admin" and password "AdminPass123!"
    And confirms password "AdminPass123!"
    And clicks "Create System Admin"
    Then an administrator account is created with PBKDF2 hashing
    And the application routes to the Login screen
    And subsequent attempts to access the setup wizard are blocked

  Scenario: Successful Staff Authentication and Role Routing
    Given an active staff account exists with username "receptionist" and role "RECEPTIONIST"
    When the user enters username "receptionist" and valid credentials
    And submits the login form
    Then an in-memory session is established
    And the user is routed to the Receptionist Workspace
    And the password buffer is scrubbed from volatile memory

  Scenario: Authentication Rejection on Invalid Password
    Given an active staff account exists with username "dr.smith"
    When the user submits username "dr.smith" with incorrect password "wrongpass"
    Then the authentication attempt is rejected
    And a generic error message "Invalid username or password" is displayed
    And no session is created
```

### 4.2 Patient Registration & Document Validation

```gherkin
Feature: Patient Registration and Document Validation
  As a clinic receptionist or doctor
  I want standardized patient identity registration
  So that patient records are deduplicated and validated before entry

  Scenario: Register Patient with Singapore NRIC
    Given an authenticated receptionist is on the patient registration form
    When the receptionist selects identity type "NRIC"
    Then the issuing country is automatically locked to "SG"
    When the receptionist enters document number "S1234567A"
    And enters full name "Tan Ah Teck"
    And enters date of birth "1990-05-15"
    And enters phone country code "65" and phone number "91234567"
    And submits the registration form
    Then a new patient record is persisted with active status "1"
    And the patient is assigned a formatted identifier "P000001"
    And the directory list is refreshed

  Scenario: Reject Duplicate Identity Document
    Given a patient already exists with identity "NRIC", country "SG", and number "S1234567A"
    When a staff member attempts to register a new patient with identical identity details
    Then the registration transaction is rejected
    And the error message "A patient with this identity document already exists" is presented
    And no duplicate record is written to the database
```

### 4.3 Appointment Scheduling & Conflict Resolution

```gherkin
Feature: Appointment Scheduling and Interval Collision Protection
  As a receptionist or attending physician
  I want conflict-aware appointment booking
  So that double bookings and scheduling overlaps are prevented

  Scenario: Book Valid Appointment
    Given an active patient "P000001" and active doctor "dr.smith"
    When the receptionist books an appointment on "2026-10-15" from "09:00" to "09:30"
    Then the system checks for overlapping visits and doctor time-off
    And inserts the appointment with status "PENDING"
    And the visit appears on the clinic schedule

  Scenario: Prevent Double Booking Collision
    Given doctor "dr.smith" has an existing appointment on "2026-10-15" from "10:00" to "10:30"
    When the receptionist attempts to book an appointment for "dr.smith" from "10:15" to "10:45"
    Then the system rejects the booking due to an interval collision
    And displays "The requested interval conflicts with an existing booking or time off"

  Scenario: Doctor Blocks Personal Time-Off
    Given doctor "dr.smith" is logged in
    When the doctor blocks time on "2026-10-16" from "14:00" to "16:00"
    Then a blocked time interval is recorded in "doctor_time_off"
    And future booking attempts during "14:00" to "16:00" are rejected
```

### 4.4 Clinical Consultations & Prescriptions

```gherkin
Feature: Clinical Documentation and Prescription Issuance
  As an attending physician
  I want to document clinical findings and prescribe medications
  So that patient care is recorded and transferred for pharmacy checkout

  Scenario: Document Consultation and Complete Visit
    Given an appointment is in status "CHECKED_IN" assigned to "dr.smith"
    When "dr.smith" records diagnosis "Acute Bronchitis"
    And records consultation notes "Bilateral wheezing on expiration"
    And saves the clinical record
    And adds prescription "Salbutamol Inhaler" with dosage "2 puffs" and frequency "PRN"
    And selects "Mark consultation completed"
    Then the appointment status updates to "COMPLETED"
    And the consultation is marked as completed and ready for checkout
    And the visit becomes available in Receptionist Checkout
```

### 4.5 Checkout Billing & Daily Receipts

```gherkin
Feature: Checkout Billing and Receipt Issuance
  As a receptionist
  I want to collect payments and issue daily sequenced receipts
  So that clinic revenue is tracked and patients receive payment confirmation

  Scenario: Finalize Checkout and Issue Receipt
    Given an appointment is in status "COMPLETED"
    When the receptionist opens the visit in Checkout
    And enters payment amount "85.00" with method "CARD"
    And clicks "Complete checkout"
    Then a payment record of "8500" cents is stored in "payments"
    And a receipt is generated with sequence "2026-10-15-1"
    And the appointment status updates to "CHECKED_OUT"
    And the revenue report reflects the new transaction
```

---

## 5. Non-Functional Specifications

1. **Performance & Responsiveness**:
   - Patient search queries across all database records execute in under 100 milliseconds.
   - UI navigation and view switches complete instantaneously without UI freezing, using asynchronous task dispatching (`ClinicTaskRunner`).
2. **Security & Cryptography**:
   - Passwords hashed using PBKDF2 with HMAC-SHA256, 210,000 iterations, and 128-bit cryptographically secure random salt.
   - Session identifiers reside in volatile memory and terminate upon application exit or logout.
   - Role-based authorization enforced at the service layer prior to executing any repository read/write.
3. **Data Integrity & Crash Safety**:
   - Foreign key constraints enabled (`PRAGMA foreign_keys = ON`) on the database connection.
   - All multi-table updates (e.g. checkout payment + receipt generation + appointment status update) execute within atomic transactions.
4. **Usability & Ergonomics**:
   - Compliant with accessibility standards: high-contrast clinical color palette, standard keyboard shortcuts, and clear focus states.
   - Compact form controls with contextual validation feedback and automatic age derivation based on Singapore standard time.
