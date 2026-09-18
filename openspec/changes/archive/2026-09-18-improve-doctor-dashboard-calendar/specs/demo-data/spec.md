## Purpose

Provides deterministic local-development data for demonstrating and testing
the complete multi-role clinic workflow safely.

## ADDED Requirements

### Requirement: The local demo seed SHALL provide representative clinic history

The local-development seed command SHALL create deterministic, workflow-ready
data without changing the production schema. It SHALL create 18 patients,
including active and inactive directory examples, and appointments for every
date in the inclusive `Asia/Singapore` window from seven days before today
through fourteen days after today. Both seeded Doctors SHALL have appointments
on each date, and the rows SHALL cover the appointment lifecycle statuses while
remaining non-overlapping for each Doctor.

Historical appointments with `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` status
SHALL have a linked clinical record. Historical `COMPLETED` and `CHECKED_OUT`
appointments SHALL also have at least one linked prescription. Other statuses
SHALL remain free of clinical records and prescriptions so the seed respects
the existing consultation workflow.

The seed wrapper SHALL print the same concise credentials used by the Java
seeder: `ada` / `ada1234!`, `grace` / `grace123!`, and `reception` /
`recept123!`. All seeded passwords SHALL satisfy the application's minimum
eight-character password policy.

#### Scenario: Developer seeds a fresh database

- **WHEN** the developer runs `scripts/seed-demo-data.ps1` against a fresh database
- **THEN** the command creates the representative patients, rolling appointment schedule, and linked historical clinical data, and prints credentials that can authenticate as either Doctor or the Receptionist

#### Scenario: Developer opens a historical appointment

- **WHEN** a seeded historical appointment has `CHECKED_IN`, `COMPLETED`, or `CHECKED_OUT` status
- **THEN** its clinical record is available through the appointment link, and completed or checked-out rows expose at least one prescription

#### Scenario: Developer signs in with showcase credentials

- **WHEN** the developer signs in with one of the printed short Doctor or Receptionist credential pairs
- **THEN** authentication succeeds and the account has the expected seeded role
