## Context

See `proposal.md` and the two delta specifications for the motivation and
observable behavior. The completed patient-directory implementation already
uses one shared administrative view for Doctors and Receptionists. That view
currently builds a `TabPane` containing the registration form and the search
and manage content, while `DoctorView` builds a navigation `VBox` whose
buttons inherit rounded, padded action-button styling.

The existing patient service, details window, guarded deletion flow, active
status behavior, dependent-selector callback, and administrative privacy
boundary are correct for this refinement and remain unchanged. The
Receptionist top-level feature tabs also remain separate; only the tabs inside
the patient area are removed.

## Goals / Non-Goals

**Goals:**

- Make Doctor navigation destinations read as contiguous sections of the
  navigation rail while retaining active, hover, focus, and resize behavior.
- Give both authorized roles one default directory view and an explicit
  in-page transition to registration.
- Preserve independent registration and patient-details form state, existing
  administrative controls, and dependent-data refresh behavior.
- Make registration success, cancellation, and failure states deterministic and
  straightforward to verify with semantic UI tests.

**Non-Goals:**

- Change patient authorization, validation, persistence, deletion, status, or
  clinical ownership rules.
- Remove the separate patient-details modal or change its actions.
- Merge the Receptionist's top-level Patients, Appointments, Checkout, or
  Revenue feature tabs.
- Redesign navigation for System Admin or change unrelated workspace styling.

## Decisions

### Use explicit directory and registration view states instead of a TabPane

The shared patient view will keep the existing registration form controls and
directory controls but place them in two named content states under one page
title. The directory state is shown by default and contains the search bar,
results list, and a bottom `Register new patient` action. The registration
state contains the existing form, a `Register patient` action, and `Cancel`.
State transitions replace the content area rather than adding another modal or
nested tab strip.

This keeps the user's mental model aligned with the requested page while
avoiding duplicated forms. A nested `TabPane` was rejected because it retains
the visual and interaction complexity the refinement is intended to remove. A
separate registration window was rejected because registration is still part
of the same patient-directory workflow and should return to the same page
context.

The existing leaf-control IDs, including role-prefixed registration, search,
and patient-result IDs, will remain stable where possible. The tab-specific
container IDs will be replaced by explicit directory/register view IDs so
tests and future UI code can identify the current state without relying on a
misleading `-tab` suffix.

### Return to a refreshed directory after registration

Successful registration will clear the registration form, clear the current
directory query, refresh the complete patient result list, invoke the existing
dependent-selector callback, and show the directory state with success
feedback. Clearing the query ensures the newly generated patient is visible
when the user returns instead of being silently hidden by a previous filter.

Validation or persistence failure will leave the registration state visible,
retain the entered values where possible, show the existing actionable
feedback, and avoid invoking a successful-mutation refresh callback. Cancel
will discard the draft, clear the form before its next use, and return to the
directory without changing persisted data.

### Make Doctor navigation buttons fill the rail

The Doctor navigation container will use zero spacing between its destination
buttons and retain only vertical panel padding. The navigation label will keep
its own horizontal inset. Destination buttons will be explicitly resizable to
the rail width, left-aligned, and styled without individual rounded-card
boundaries; the active destination will continue to use the teal active color.
The margin between the rail and the page content remains, because it separates
workspace regions rather than navigation items.

This uses the existing JavaFX layout and CSS hooks. A new navigation component
or dependency is unnecessary. A single full-width button style was preferred
to individually sizing each button so the layout remains correct when the
window is resized.

### Keep the refinement shared across roles

The same directory and registration state machine will be used for Doctor and
Receptionist instances, with their existing control-ID prefixes and service
session. This avoids divergent workflows and ensures that the Receptionist
directory receives the same removal of sub-tabs. Role authorization remains at
the service boundary and is not moved into the UI state transition code.

## Risks / Trade-offs

- [Shared component regression] A change to the common patient view can affect
  both workspaces. -> Preserve role-prefixed leaf IDs and run both Doctor and
  Receptionist TestFX suites through the relevant registration, search, and
  details flows.
- [Draft loss on cancel] A user who cancels registration loses unsaved form
  values. -> Make the action explicitly `Cancel`, leave persisted data
  untouched, and provide the registration action again from the directory.
- [New patient hidden by a filter] Returning with the previous query could hide
  the newly created patient. -> Clear the query on successful registration and
  verify that the returned directory contains refreshed results.
- [Layout clipping at smaller sizes] Full-width buttons or a longer directory
  form could reduce available content space. -> Keep the existing scrollable
  page containers, use resizable buttons, and test the state transitions after
  layout calculation at the supported window size.
- [Selector/test coupling] Removing tabs invalidates tests that select tab
  headers. -> Replace tab-selection helpers with state-view selectors while
  retaining stable field and action IDs.

## Migration Plan

No database, service, or deployment migration is required. The change is
limited to JavaFX view composition, CSS, tests, and documentation. Rollback is
the normal code rollback: restore the previous `TabPane` composition and
navigation spacing without changing persisted data.
