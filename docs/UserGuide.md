# User Guide

NUSynapxe supports clinic staff through one shared visit workflow while
keeping administrative and medical information separate.

## First launch

From the repository root, start the desktop application:

```powershell
.\gradlew.bat run
```

On a new database, NUSynapxe displays **Create the first System Admin
account**. Enter a non-blank username, a password with at least eight
non-blank characters, and the matching confirmation. Select **Create System
Admin**. A successful setup always goes to Login; the setup form cannot be
used again once an account exists.

## Login and logout

Enter an enabled account's username and password on Login and select **Log
in**. The application opens the workspace for the account's role. Invalid,
unknown, and disabled credentials all show the same `Invalid username or
password` message. Select **Log out** in any workspace to clear the in-memory
session and return to Login. Closing and reopening the application also
requires a new login.

## System Admin workflow

1. Log in with the account created during first-run setup.
2. In **SYSTEM ADMIN workspace**, enter a unique username, display name,
   initial password, and select **Doctor** or **Receptionist**.
3. Select **Create account**. A successful account appears in the account
   list and can log in immediately.

System Admin is an account-administration role. It cannot read or edit patient
medical records through the protected services.

## Receptionist workflow

### Patient directory and basic data

Every patient receives an immutable, automatically generated Patient ID. The
interface displays it in a form such as `P000042`; staff do not enter or edit
this value, and it is not shown on the new-patient form. Appointments, payments, and retained records continue to use the
same Patient ID even when basic details are corrected.

Select the **Register new patient** tab to create a record:

1. Choose **NRIC**, **FIN**, **PASSPORT**, or **OTHER** as the identity type.
2. Select the issuing country from the country dropdown, which lists Singapore
   first and then every other ISO country alphabetically. Choosing **NRIC** or
   **FIN** automatically selects and locks Singapore; the service rejects any
   non-Singapore issuing country for these document types. The application stores the
   two-letter country code. NRIC must use `S` or `T`, seven digits, and a final
   letter. FIN must use `F`, `G`, or `M`, seven digits, and a final letter.
   Passport accepts 5–20 letters or digits. These checks do not verify a
   government checksum, so staff must still check the source document.
3. Select date of birth with the calendar, or jump directly with the adjacent
   month and year dropdowns. Changing month or year preserves the selected day
   where possible and otherwise uses that month's final day. The blank,
   read-only age field is filled using the current date in Singapore. Choose
   **Male** or **Female**; Male is listed first.
4. The phone country code is suggested from the issuing country (for example,
   `65` for Singapore) but can be edited. Both the country code and remaining
   phone number accept digits only. The fixed `+` displayed before the country
   code is not editable. The complete number is displayed conventionally with `+`. Enter an email
   containing `@` and an address.
5. Every field marked `*` is required. Height and weight are the only optional
   fields. Height is a positive whole number of centimetres, such as `171`;
   weight is positive kilograms with at most one decimal, such as `70.4`.
   There is no patient billing-information field; checkout is separate.
6. Select **Register patient**. The new record and patient selectors refresh
   automatically, and the blank registration form remains independent of edit.

The combination of identity type, issuing country, and identity number must
be unique after trimming and uppercasing. A duplicate is completely rejected
with `A patient with this identity document already exists`; no second patient
is created. The message and application logs do not repeat the full document
number.

Select **Search and manage patients** to find patients by Patient ID (for example,
`P000042` or `42`), identity type/number/country, name, phone, or email.
Search is case-insensitive, partial text is accepted, and **Clear search**
restores the full directory. No matches produce an empty list rather than an
application error.

The search tab contains only the search controls and results. Select a result
to open its permitted basic-data fields in a separate patient-details window.
Correct them and select **Save patient changes**. A failed validation or
duplicate identity leaves the entire stored record unchanged. Select
**Deactivate patient** when a record should no longer be active; the same
button changes to **Activate patient** for an inactive record. Either status
change preserves its Patient ID, appointments, payments, and clinical history.
A patient migrated from an
older database remains searchable, but its identity-document fields must be
completed before its next basic-data save.

Receptionists can view and maintain only basic identity, demographic,
measurement, contact, and address data. The directory never returns
diagnoses, consultation or follow-up notes, or prescriptions, and basic-data
changes do not alter them.

### Book and manage a visit

Open the separate **Appointments across all Doctors** feature tab.

1. Choose a patient and a Doctor.
2. Enter appointment times as `yyyy-MM-dd HH:mm` and select **Book
   appointment**. The new appointment starts as `PENDING` and awaits the
   assigned Doctor's acceptance.
3. Reopen the appointment tab after another staff member changes an appointment;
   its data reloads automatically. The scheduler covers every Doctor, but overlapping appointments and Doctor
   time-off are rejected. Adjacent appointments are allowed.

To change an existing pending or accepted visit, select it, enter the new
start and end times, and choose **Reschedule selected**. Choose **Cancel
selected** to cancel it before completion.

### Check in, checkout, and revenue

1. After the assigned Doctor accepts the appointment, reopen **Appointments
   across all Doctors** and select the appointment; the list refreshes automatically.
2. At or after the scheduled start time, select **Check in selected**. The
   appointment changes to `CHECKED_IN`.
3. The Doctor records the consultation and selects **Mark consultation
   completed**. Open the separate **Checkout** tab and select the appointment.
4. Enter a positive charge in major currency units, such as `45.00`, choose
   Cash, Card, Transfer, or Other, and select **Complete checkout**. This
   records the successful payment and changes the appointment to
   `CHECKED_OUT`.
5. Open **Daily revenue**, enter a date as `yyyy-MM-dd`, and select **Show revenue** to see the count
   and total of successful checkouts for that local clinic date.

Zero, negative, malformed, or missing amounts are rejected. Cancelled visits
and unsuccessful payment attempts do not contribute to the revenue summary.

Receptionists can see the basic patient data described above, but no clinical
record, diagnosis, consultation note, follow-up note, or prescription is
returned by Receptionist services or screens.

The Receptionist header keeps **Log out** at the top right. There is no manual
Refresh button: searches and successful writes update their affected data, and
opening a feature tab reloads information that another workflow may have changed.

## Doctor workflow

1. Log in with a Doctor account. The workspace shows only that Doctor's
   appointment schedule.
2. Select a pending appointment and choose **Accept selected**, or enter new
   times and choose **Reschedule selected** for a pending or accepted visit.
3. Enter a non-overlapping `yyyy-MM-dd HH:mm` interval and select **Block time
   off** to make that period unavailable for future bookings.
4. After Reception has checked in the patient, select **Refresh schedule** and
   select the appointment. Enter the diagnosis, consultation notes, and
   follow-up notes, then choose **Save consultation**.
5. Complete all prescription fields—medication, dosage, frequency, duration,
   and instructions—and choose **Add prescription**.
6. Select **Mark consultation completed**. This makes the visit available for
   Receptionist checkout.

Only the assigned Doctor can read or change the clinical record and
prescriptions for an appointment. A Doctor cannot manage another Doctor's
schedule or time-off.

## Appointment states

```text
PENDING -> ACCEPTED -> CHECKED_IN -> COMPLETED -> CHECKED_OUT
    \          /
     \-> CANCELLED
```

Cancellation is available before completion from `PENDING` or `ACCEPTED`.
Invalid transitions are rejected without changing the stored appointment.

## Local data and privacy cautions

By default, the database is stored at:

```text
Windows: %USERPROFILE%\.nusynapxe\nusynapxe.db
macOS/Linux: ~/.nusynapxe/nusynapxe.db
```

The database is local to the current computer and contains identity-document
numbers, patient data, and clinical information. Do not commit it to version
control, include it in interaction logs, or attach it to bug reports. Error
reports and screenshots should not expose real identity numbers. Close
NUSynapxe before copying, backing up, or removing the file.
For a development-only database, set the `nusynapxe.database` Java system
property to an isolated path.
