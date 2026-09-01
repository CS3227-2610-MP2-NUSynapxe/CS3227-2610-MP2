## Purpose

Provides secure, role-aware access to the clinic application so each staff member can use the workflows appropriate to their responsibilities while medical information remains protected.

## ADDED Requirements

### Requirement: The system SHALL distinguish staff roles and enforce permissions at the service boundary

The system SHALL support the roles Doctor, Receptionist, and System Admin. Every protected operation SHALL evaluate the authenticated actor and required role or ownership in the application/service layer; hiding a control in the user interface alone SHALL NOT grant authorization.

#### Scenario: Doctor accesses a permitted clinical operation
- **WHEN** an authenticated Doctor edits clinical information for a patient linked to that Doctor's appointment
- **THEN** the operation is allowed and the clinical change is persisted

#### Scenario: Receptionist is denied access to clinical information
- **WHEN** an authenticated Receptionist requests a patient's medical notes or attempts to change them
- **THEN** the service rejects the request without returning the notes or changing stored clinical data

#### Scenario: System Admin is limited to account administration
- **WHEN** an authenticated System Admin attempts to read or modify patient medical information through a protected service
- **THEN** the service rejects the request without exposing the clinical data

### Requirement: The system SHALL provide a first-run System Admin setup flow

When the local database contains no user accounts, opening the application SHALL present a setup flow that creates the initial System Admin account. The setup flow SHALL collect a non-blank username and password, SHALL validate the password according to the application's password policy, and SHALL become unavailable once an account exists.

#### Scenario: Initial setup creates the first administrator
- **WHEN** the application is opened with an empty account store and valid System Admin credentials are submitted
- **THEN** one enabled System Admin account is persisted and the application proceeds to the login page

#### Scenario: Invalid initial setup is rejected
- **WHEN** the initial setup form is submitted with a blank username, blank password, or a password that fails the password policy
- **THEN** validation feedback is shown and no account is created

#### Scenario: Setup cannot be repeated
- **WHEN** the application is opened after at least one account has been persisted
- **THEN** the first-run setup controls are unavailable and the login page is shown instead

### Requirement: A System Admin SHALL be able to create Doctor and Receptionist accounts

An authenticated System Admin SHALL be able to create an enabled staff account with a unique username, display name, role, and initial password for either Doctor or Receptionist. The system SHALL reject duplicate usernames and invalid account fields without a partial account.

#### Scenario: Administrator creates a Doctor account
- **WHEN** a System Admin submits valid unique credentials and selects Doctor
- **THEN** an enabled Doctor account is persisted and is available for login

#### Scenario: Administrator creates a Receptionist account
- **WHEN** a System Admin submits valid unique credentials and selects Receptionist
- **THEN** an enabled Receptionist account is persisted and is available for login

#### Scenario: Duplicate username is rejected
- **WHEN** a System Admin submits an account using a username already assigned to an existing account
- **THEN** the service reports a validation error and leaves all existing accounts unchanged

### Requirement: The system SHALL authenticate users and manage an in-memory session

The login page SHALL accept a username and password. Valid credentials for an enabled account SHALL create a session containing that account's identity and role and SHALL route the user to the corresponding role workspace. Invalid credentials or disabled accounts SHALL be rejected without identifying which credential was incorrect. Logging out or closing and reopening the application SHALL require authentication again.

#### Scenario: Valid Doctor login opens the Doctor workspace
- **WHEN** an enabled Doctor submits the correct username and password
- **THEN** a session is created and the Doctor workspace is displayed

#### Scenario: Invalid login is rejected without account disclosure
- **WHEN** a user submits an unknown username, an incorrect password, or credentials for a disabled account
- **THEN** login is rejected with generic feedback and no authenticated workspace is shown

#### Scenario: Logout returns to login
- **WHEN** an authenticated user selects logout
- **THEN** the session is cleared and the login page is displayed without the previous user's workspace

#### Scenario: Application restart does not reuse a session
- **WHEN** the application is closed and opened again after a prior successful login
- **THEN** no prior session is accepted and the login page is displayed

### Requirement: Password credentials SHALL be stored as non-recoverable verifiers

The system SHALL NOT persist plaintext passwords or reversible password values. Each stored password verifier SHALL use a per-account salt and the configured password-verification process, and authentication SHALL compare the submitted password against that verifier.

#### Scenario: Stored account data does not contain a plaintext password
- **WHEN** an account is created and its persisted representation is inspected
- **THEN** the original password is not present as a stored value and a salt/verifier pair is present

#### Scenario: Correct and incorrect passwords are distinguished
- **WHEN** the correct password is submitted for an enabled account
- **THEN** authentication succeeds, while a different password is rejected
