# User Guide

NUSynapxe supports clinic staff through one shared visit workflow while
keeping administrative and medical information separate.

## Interface overview

All screens use the same calm clinical visual language: a light workspace
background, white content cards, clear section headings, teal primary actions,
and a colour-coded badge for every workflow status. The desktop window opens maximized so
it uses the available monitor work area while retaining normal window controls.
When restored, it uses a compact `1200 x 760` scene and can be resized down to the
supported minimum of `980 x 640`; longer forms and result lists scroll inside
their content area.

Authenticated screens show the NUSynapxe name, the current role, the signed-in
username, and **Log out** in a shared header. Receptionists use the dark
left-hand navigation rail to switch between **Directory**, **Appointments**,
**Calendar**, **Check in**, **Checkout**, and **Revenue Reports**. Navigation
labels remain horizontal, and the separate **Navigation** heading is not a
selectable destination. Doctors use the same rail pattern for **Dashboard**,
**Patients**, and **Calendar**. The selected destination is highlighted.
Short operation notices appear below the header and close automatically after
six seconds.

## Getting started

### Installation

Tagged releases publish native installers and fat JARs as GitHub Release
assets. The native installers are the easiest option: download the installer
for your platform from the repository's Releases page and run it; no separate
Java installation is required, since each installer bundles a matching Java
runtime. The release provides Windows x64 (`.msi`), Windows ARM64 compatibility
(`.msi`, an x64 package for Windows emulation), Linux x64/ARM64 (`.deb`), and
macOS x64/ARM64 (`.dmg`) installers.

The release also provides one platform-specific fat JAR beside each installer
and one convenience JAR named `NUSynapxe-<version>.jar`. The convenience JAR
includes JavaFX runtime files for Windows x64, Linux x64, and macOS ARM64. It
requires Java 25 on the machine and can be started with:

```text
java --version
java -jar NUSynapxe-<version>.jar
```

Use the matching platform-specific fat JAR for macOS x64, Linux ARM64, or
Windows ARM64 compatibility, or use a native installer instead. To build and
run from source, see **First launch** below.

### First launch

From the repository root, start the desktop application:

```powershell
.\gradlew.bat run
```

On a new database, NUSynapxe displays **Create the first System Admin
account**. Enter a non-blank username, a password with at least eight
non-blank characters, and the matching confirmation. Select **Create System
Admin**. A successful setup always goes to Login; the setup form cannot be
used again once an account exists.

### Login and logout

Enter an enabled account's username and password on Login and select **Log
in**. The application opens the workspace for the account's role. Invalid,
unknown, and disabled credentials all show the same `Invalid username or
password` message. Select **Log out** in any workspace to clear the in-memory
session and return to Login. Closing and reopening the application also
requires a new login.

Password fields on setup, login, and staff-account creation include a faded eye
inside the right edge of the field that temporarily reveals or hides the entered
password. Validation errors appear in a red notification at the top centre of
the window and fade away after six seconds.

## System Admin workflow

1. Log in with the account created during first-run setup.
2. In **SYSTEM ADMIN workspace**, enter a unique username, display name,
   initial password, enter it again in **Confirm password**, and select
   **Doctor** or **Receptionist**. The two password entries must match.
3. Select **Create account**. A successful account appears in the Current
   staff accounts table, with its username, display name, role, and status,
   and can log in immediately.

System Admin is an account-administration role. It cannot read or edit patient
medical records through the protected services.

## Receptionist workflow

### Patient directory and basic data

Every patient receives an immutable internal Patient ID for database
relationships. Staff do not enter or edit it, and routine directory and detail
views omit it to leave more room for useful patient information. Appointments,
payments, and retained records continue to use the same internal ID when basic
details are corrected.

The **Patient directory** is one page. It initially shows the patient search
controls and a table with these columns: **Name**, **Date of birth**, **Phone**,
**Email**, **Status**, and **Actions**. The **Actions** column is fixed at the
far right and contains a **View** button for each row.
Select **Register new patient** at the bottom to switch to the registration
form. The identity, country, date, and sex dropdowns use compact controls so
the form remains easy to scan:

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
6. Select **Register patient**. On success, the form is cleared, the search is
   reset, patient selectors and directory results refresh, and the view returns
   to **Patient directory**. If validation or persistence fails, the form stays
   open with feedback so the entered values can be corrected. Select **Cancel**
   to discard an unfinished registration and return to the directory.

The combination of identity type, issuing country, and identity number must
be unique after trimming and uppercasing. A duplicate is completely rejected
with `A patient with this identity document already exists`; no second patient
is created. The message and application logs do not repeat the full document
number.

Use the **Patient directory** search controls to find patients by name,
NRIC/FIN or another identity document, phone, or email. The older generated-ID
lookup remains accepted for compatibility but is not needed for normal work.
Search is case-insensitive, partial text is accepted, and **Clear search**
restores the full directory. Every returned row keeps its own **View** action
when a new query replaces the current results, including after repeated
searches. No matches produce an empty list rather than an application error.

Select a row's **View** button to open a read-only page containing all permitted
administrative details. From there, select **Edit**, **Deactivate patient** (or
**Activate patient**), **Delete patient**, or **Back to patients**. **Edit**
opens the editable basic-data form; select **Save** to persist changes or
**Discard changes** to return to the read-only view without writing the draft.
A failed validation or duplicate identity keeps the edit page open with
feedback. Status changes preserve the patient's internal ID, appointments,
payments, and clinical history. A patient
migrated from an older database remains searchable, but its identity-document
fields must be completed before its next basic-data save.

Doctors have the same patient-directory permissions as Receptionists: they can
register, search, edit, activate, deactivate, and request deletion of patients.
Deleting is reserved for an accidental or unused patient with no appointments,
clinical records, prescriptions, payments, receipts, or other related data.
Select **Delete patient**, review the permanent-action confirmation, and select
**Delete permanently** only when the record is unused. If any related data is
found, deletion is refused and a popup lists each blocking category and count;
the patient and all history are preserved. Close the popup and use
**Deactivate patient** when the patient should remain in the system but must not
be selected for new bookings. Deactivation is reversible and does not remove
the Patient ID or history.

Receptionists can view and maintain only basic identity, demographic,
measurement, contact, and address data. The directory never returns
diagnoses, consultation or follow-up notes, or prescriptions, and basic-data
changes do not alter them.

### Book and manage a visit

Open **Appointments** in the left navigation. Use the
**Book appointment** sub-tab for new bookings and **Search and manage
appointments** for filters and existing appointments.

Alternatively, open **Calendar**, search for a Doctor, and select inclusive
**From** and **To** dates (up to 31 days). Every date in that range appears without
a week-number column. Clicking an empty date/time slot opens a scrollable
booking popup with that Doctor, date, start time, and a 30-minute end time
already filled. Select the patient and adjust any fields before booking. Select
an existing Calendar appointment to open its edit popup. Closing either popup
returns to Calendar, and successful changes refresh the grid immediately.

The scheduling dashboard shows a chronological all-Doctor table and summary
counts for Pending, Accepted, Checked in, and Completed appointments. Each row
has **Date**, **Time**, **Patient**, **Doctor**, and **Status** columns; generated
Patient and Doctor IDs are not displayed. The **Status** column renders a
colour-coded badge using the same colours as the Doctor Calendar: amber for
Pending, teal for Accepted/Checked in, blue for Completed/Checked out, orange
for Declined, and red for Cancelled. The Check-in Queue and Checkout tables
use the same status badges.
Use the
optional date, Doctor, status, and patient administrative filters to narrow
the table; choose **All statuses** to clear a previous status choice. Changing a
filter or selecting Search reloads the table. Selecting a patient
for a new booking only offers active patients. Existing appointments remain
available for historical viewing if a patient is later deactivated.

Patient and Doctor booking fields are search bars: type part of a name,
username, NRIC/FIN, phone, or email and choose a filtered suggestion underneath.
Use the mouse, or use Up/Down and Enter from the keyboard.
The date, start time, and end time are aligned on one row. Start and end times
use separate hour (`00`–`23`) and minute (`00` or `30`) dropdowns.

1. Choose a patient and a Doctor.
2. Select the appointment date with the calendar and choose start/end hours and
   half-hour minute values. Select **Book
   appointment**. The new appointment starts as `PENDING` and awaits the
   assigned Doctor's acceptance.
3. The scheduler covers every Doctor, but overlapping appointments and Doctor
   time-off are rejected. Adjacent appointments are allowed.

To change an existing pending or accepted visit, select it and choose
**Reschedule selected**. A popup shows the patient information and provides a
new date, start time, end time, **Reschedule appointment**, and **Cancel
appointment** actions. Successful actions close the popup and refresh the
dashboard.

### Check in, checkout, and revenue

The **Revenue Reports** page keeps equally sized From/To, patient, Doctor, and
payment-method filters on one row. Choose **All methods** to clear a previous
method selection. Generate a report to view receipt-backed totals,
breakdowns, and payment details. Use **Export CSV** or **Export JSON** to save
the current report.

The **Check-in Queue** tab is the Receptionist's front-desk view for arrivals.
It defaults to Singapore's current date and shows accepted appointments waiting
for arrival together with appointments already checked in. Use the Doctor,
patient, date, and queue-status filters to narrow the table. Select an appointment
to open its administrative details and choose **Check in patient** when the
appointment start time has arrived. The queue refreshes automatically after a
successful check-in. Clinical notes and prescriptions are never shown.

1. After the assigned Doctor accepts the appointment, open **Check in**, apply
   any patient, Doctor, date, or status filters, and select the appointment.
2. At or after its scheduled start time, choose **Check in patient** in the
   administrative-details popup.
3. The assigned Doctor records the consultation and selects **Mark consultation
   completed**.
4. Open **Checkout**, use its Patient, Doctor, and date filters to find the
   completed appointment, and select it to open the checkout window. Enter a
   positive charge in major currency units, such as `45.00`, choose Cash,
   Card, Transfer, or Other, and select **Complete checkout** in that window. This
   records the successful payment and changes the appointment to
   `CHECKED_OUT`.
5. After checkout, the receipt preview shows the daily receipt number and
   Singapore timestamp. Open the
   **Receipts** sub-tab to browse the receipt table and select a receipt to view
   its persisted details. Checkout, queue, receipt, and revenue result tables do
   not display generated Patient or Doctor ID columns.
6. Open **Revenue Reports**, choose an inclusive From/To range and any optional
   patient, Doctor, or payment-method filters, then select **Generate report**.
   The results show the successful-payment count, total, payment-method and
   Doctor breakdowns, and matching receipt rows.

Zero, negative, malformed, or missing amounts are rejected. Cancelled visits
and unsuccessful payment attempts do not contribute to the revenue summary.

Receptionists can see the basic patient data described above, but no clinical
record, diagnosis, consultation note, follow-up note, or prescription is
returned by Receptionist services or screens.

The Receptionist header keeps **Log out** at the top right. There is no manual
Refresh button: searches and successful writes update their affected data, and
opening a feature tab reloads information that another workflow may have changed.

## Doctor workflow

1. Log in with a Doctor account. **Dashboard** is the default destination and
   shows a compact, scrollable calendar for the current Singapore-local day in
   the left side of a schedule/detail layout. Use **Today**, the previous/next
   arrows, the date picker, or the refresh icon to navigate and reload the same
   day. Select an appointment block to reveal its consultation context and
   status-specific appointment actions on the right. The detail header names
   the patient, scheduled time, and lifecycle status; its colour follows the
   status while the written status remains visible. The patient card is
   read-only. Until a visit is selected, the detail area explains what to do.
   Changing to a day without that appointment clears the selection, while
   refreshing retains a selection that is still visible.
   Date fields throughout the workspace use the same compact, minimal control
   treatment.
2. Select **Patients** to open the administrative directory. Doctors can
   register, search, edit, activate, deactivate, and safely delete patients
   there using the same administrative fields as Receptionists. The directory
   includes inactive patients and contains no diagnosis, consultation,
   follow-up, prescription, or other clinical controls. Return to **Dashboard**
   to resume appointment and clinical work. The results card fills the page and
   its table expands to use the available vertical space.
3. Select **Calendar** to open the full time-grid view of your assigned
   appointments and explicitly blocked time. Use **Today** or the inclusive
   **From** and **To** date pickers to change the displayed range. The refresh
   icon reloads the current range without changing it.
4. Use the compact **Calendar** / **Agenda** selector to switch views.
   **Calendar** keeps the configurable date-range time grid. **Agenda** starts
   at its selected inclusive Singapore clinic date and loads all later
   appointments in chronological pages as you scroll. It groups rows by date
   and shows the time range, Patient ID/name, and a written appointment status.
   Cancelled rows remain visible but are muted, while a **Past** cue identifies
   elapsed appointments. In Agenda, the compact date picker sits between the
   previous/next arrows; those arrows move one day at a time, while **Today**
   and the refresh icon return to or reload the same anchor date. Empty
   schedules, the end of the stream, and retryable loading failures have their
   own messages. Agenda rows are read-only and never show diagnoses,
   consultation notes, follow-up notes, prescriptions, locations, or invented
   all-day events. At narrow window widths, **Add appointment** and **Block
   time** move together onto a second toolbar row.
5. Calendar greys dates and periods that have elapsed, disabled days, and time
   outside the configured working intervals. A red current-time line appears
   on the current date when that date is in the displayed Calendar range. Appointments
   remain visible even when they fall outside working hours. Purple **Blocked
   time** cards show unavailable intervals at their actual start, end, and
   proportional duration; Receptionists can see these blocks but cannot remove
   them.
6. Select the Calendar **settings** icon to configure each day's working
   intervals. The settings page displays the fixed Singapore timezone and has
   no week-start or work-location setting because Calendar ranges and Agenda
   start dates are selected directly. Disable a day to make it entirely
   non-working, or use **Add interval** to split a day around a break such as
   lunch. Save valid changes or use **Cancel** to discard them. These settings
   affect shading only and never block or change appointments.
7. Select a Dashboard appointment to see actions for its current state. A
   pending visit offers **Accept**, **Decline**, and **Reschedule**; an accepted
   visit offers **Decline**, **Reschedule**, and **Check in** when its start time
   has arrived. **Reschedule** opens the same Calendar-style appointment editor
   used elsewhere in the Doctor workspace. Checked-in visits show the existing
   consultation, prescription, and completion workflow. Completed or checked-
   out visits keep saved clinical history readable but do not show editing
   controls, while declined or cancelled visits are non-actionable.
8. In Calendar, choose **Block time**, then select a date and half-hour start
   and end times. Invalid or conflicting input remains in the dialog for
   correction. Select one of your **Blocked time** cards to view it; choose
   **Remove blocked time** and confirm to make the interval available again.
9. After Reception has checked in the patient, refresh the Dashboard and
   select the appointment. Enter the diagnosis, consultation notes, and
   follow-up notes, then choose **Save consultation**.
10. Complete all prescription fields—medication, dosage, frequency, duration,
   and instructions—and choose **Add prescription**.
11. Select **Mark consultation completed**. This makes the visit available for
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
`DECLINED` and `CANCELLED` records remain available as history but do not reserve
their former intervals. Booking, rescheduling, and blocking time may reuse those
intervals unless another active appointment or explicit blocked-time interval
occupies them.

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

### Showcase database

For local development or a showcase, the repository provides a database
workflow targeting the same per-user database used by `.\gradlew.bat run`.
From the repository root, run:

```powershell
.\scripts\seed-demo-data.ps1 -Reset
```

This replaces the default `%USERPROFILE%\.nusynapxe\nusynapxe.db` with demo
staff, 18 patients (including inactive directory examples), calendar settings,
a lunch break, and two appointments per Doctor for each date from the previous
seven days through the next fourteen days. Statuses span the appointment
lifecycle. Historical checked-in, completed, and checked-out appointments are
linked to clinical records, with prescriptions on completed and checked-out
visits. The script prints these showcase login credentials when it finishes:

```text
Doctor       ada / ada1234!
Doctor       grace / grace123!
Receptionist reception / recept123!
```

The System Admin credential remains `admin.demo / DemoAdmin123!`. These
accounts and passwords are for local demonstrations only. Start the application
normally with:

```powershell
.\gradlew.bat run
```

Use `.\scripts\reset-demo-database.ps1 -Force` when only an empty schema is
needed. Both scripts accept `-DatabasePath` when an alternate database is
required. Close NUSynapxe before resetting or replacing the database. The demo
credentials and data are not suitable for production use.

## Troubleshooting

### Login always reports invalid credentials

Usernames and passwords are case-sensitive, and disabled accounts receive the
same generic message as unknown accounts. Confirm that the correct account was
created by System Admin. For showcase data, reset and reseed only if replacing
the current local demo database is intentional.

### A patient or Doctor does not appear in a booking search

Type part of the name or supported identifying information and select a result
from the suggestions; typed text alone does not select the record. Inactive
patients remain in the directory and history but cannot be chosen for a new
booking. Only accounts with the Doctor role are offered.

### Booking or blocked time is rejected

The end must be after the start, and active appointments or Doctor time off may
not overlap. Pending, accepted, checked-in, completed, and checked-out visits
reserve their intervals. Declined and cancelled visits release them. Adjacent
half-hour slots are allowed.

### Check-in or checkout is unavailable

Check-in requires an accepted appointment whose scheduled start time has
arrived. Checkout requires the assigned Doctor to mark the consultation
completed first. Refresh the relevant tab after another role changes the visit.

### A report or search shows no rows

Choose **All statuses**, **All Doctors**, or **All methods** to clear a previous
selection, verify the inclusive date range, then apply the filters again. Empty
results do not delete or change stored records.

### The installer shows a trust warning

Current installers are not code-signed or notarized. Continue only when the
package was downloaded from this project's GitHub Releases page. Do not install
copies received from an untrusted source.
