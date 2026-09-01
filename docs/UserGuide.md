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
this value. Appointments, payments, and retained records continue to use the
same Patient ID even when basic details are corrected.

Select the **Register new patient** tab to create a record:

1. Choose **NRIC**, **FIN**, **PASSPORT**, or **OTHER** as the identity type.
2. Select the issuing country from the country dropdown, which lists Singapore
   first and then every other ISO country alphabetically. Choosing **NRIC** or
   **FIN** automatically selects Singapore. The application stores the
   two-letter country code but deliberately does not validate a document's
   country-specific format, length, or checksum. Staff must check the source
   document.
3. Enter the identity-document number, first and last name, date of birth as
   `yyyy-MM-dd`, and choose **Female** or **Male**. Enter phone, email, and
   address. Height in centimetres and weight in kilograms are optional
   positive numbers. There is no patient billing-information field; checkout
   payments are managed separately.
4. Enter phone using an optional leading `+` followed only by digits. There
   is no Singapore-specific length requirement. Spaces, hyphens, letters,
   multiple `+` characters, and a `+` without digits are rejected.
5. Select **Register patient**. The generated Patient ID appears in that tab's
   read-only field. Registration does not reuse or overwrite the edit form.

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

Select a result to populate that tab's permitted basic-data fields. Correct them and
select **Save patient changes**. A failed validation or duplicate identity
leaves the entire stored record unchanged. **Deactivate patient** is located
with the search results and edit action; select it when
a record should no longer be active; deactivation does not delete its Patient
ID, appointments, payments, or clinical history. A patient migrated from an
older database remains searchable, but its identity-document fields must be
completed before its next basic-data save.

Receptionists can view and maintain only basic identity, demographic,
measurement, contact, and address data. The directory never returns
diagnoses, consultation or follow-up notes, or prescriptions, and basic-data
changes do not alter them.

### Book and manage a visit

1. Select the patient from the patient directory and choose a Doctor.
2. Enter appointment times as `yyyy-MM-dd HH:mm` and select **Book
   appointment**. The new appointment starts as `PENDING` and awaits the
   assigned Doctor's acceptance.
3. Use **Refresh** after another staff member changes the appointment. The
   scheduler covers every Doctor, but overlapping appointments and Doctor
   time-off are rejected. Adjacent appointments are allowed.

To change an existing pending or accepted visit, select it, enter the new
start and end times, and choose **Reschedule selected**. Choose **Cancel
selected** to cancel it before completion.

### Check in, checkout, and revenue

1. After the assigned Doctor accepts the appointment, select **Refresh** and
   select the appointment.
2. At or after the scheduled start time, select **Check in selected**. The
   appointment changes to `CHECKED_IN`.
3. The Doctor records the consultation and selects **Mark consultation
   completed**. Refresh the receptionist workspace.
4. Enter a positive charge in major currency units, such as `45.00`, choose
   Cash, Card, Transfer, or Other, and select **Complete checkout**. This
   records the successful payment and changes the appointment to
   `CHECKED_OUT`.
5. Enter a date as `yyyy-MM-dd` and select **Show revenue** to see the count
   and total of successful checkouts for that local clinic date.

Zero, negative, malformed, or missing amounts are rejected. Cancelled visits
and unsuccessful payment attempts do not contribute to the revenue summary.

Receptionists can see the basic patient data described above, but no clinical
record, diagnosis, consultation note, follow-up note, or prescription is
returned by Receptionist services or screens.

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
