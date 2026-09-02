## 1. Shared visual foundation

- [x] 1.1 Add the shared JavaFX stylesheet with the approved light clinical palette, typography, spacing, surface, border, focus, button, status, feedback, and empty-state styles, and verify the stylesheet is included by the Gradle resources process.
- [x] 1.2 Add small presentation helpers or factories for repeated headings, cards, field groups, action groups, feedback banners, status badges, and empty states, and verify production sources compile without introducing service or persistence dependencies.
- [x] 1.3 Update routed scenes to load the shared stylesheet, use a resizable window with documented initial and minimum dimensions, and keep essential content reachable at the minimum size; verify login routing, workspace routing, and a resize smoke test.

## 2. Shared application shell and authentication

- [x] 2.1 Add the shared branded header showing NUSynapxe, the current role, signed-in identity, and the existing logout action, and verify `logout-button`, workspace root IDs, and logout-to-login behavior remain available in TestFX.
- [x] 2.2 Present Receptionist top-level feature navigation as a styled left-side rail while preserving `reception-workspace-tabs`, its four destinations, selection-driven refresh behavior, and the independent patient and appointment subflows; verify existing navigation and refresh tests.
- [x] 2.3 Recompose Login and first-run Setup as consistent focused form cards with associated feedback and preserved default-button behavior, and verify the existing setup, login-success, invalid-credentials, and route-transition tests.

## 3. Receptionist workspace redesign

- [x] 3.1 Reflow patient registration into identity, contact, and optional-measurement sections with consistent labels, required-field treatment, and nearby actions, and verify the existing registration, validation, phone, date-of-birth, and confidentiality tests.
- [x] 3.2 Reflow patient search and management into a focused search/results surface with explicit result selection and empty-state presentation while retaining the separate patient registration flow, and verify search, selection, edit, activation, deactivation, duplicate, and no-result TestFX scenarios.
- [x] 3.3 Replace the Receptionist patient result presentation with a concise human-readable cell showing permitted identity context and active status, and verify it does not expose identity-document values beyond the existing authorized detail workflow.
- [x] 3.4 Reflow appointment booking into a focused booking card and keep appointment management as a distinct dashboard surface containing filters, summary counts, readable schedule rows, and selection-based actions, and verify booking, filtering, conflict, rescheduling, cancellation, check-in, and automatic-refresh tests.
- [x] 3.5 Replace raw appointment list rendering with readable patient, Doctor, date/time, and textual lifecycle-status cells plus status styling and an empty state, and verify all appointment statuses remain understandable without color-only communication or raw record formatting.
- [x] 3.6 Reflow Checkout and Daily revenue into focused selection, payment, date, and result cards with clear action proximity and empty states, and verify checkout, payment validation, revenue, and role-confidentiality tests.

## 4. Doctor workspace redesign

- [x] 4.1 Recompose the Doctor workspace as a schedule-plus-selected-appointment master-detail layout with an independently scrollable detail region and explicit no-selection state, and verify selecting, changing, and clearing an appointment keeps the correct consultation context.
- [x] 4.2 Group consultation, prescription, availability, and completion controls into visually distinct sections with readable prescription cells and selection-aware action states, and verify the existing Doctor consultation, prescription, completion, refresh, and authorization tests.
- [x] 4.3 Verify the Doctor layout remains usable at the documented minimum and normal window sizes without exposing another Doctor's schedule or unauthorized Receptionist-only actions, and record the result in the UI verification notes.

## 5. System Admin and operational record presentation

- [x] 5.1 Recompose System Admin account creation and current staff accounts into separate cards with readable role/status information and nearby feedback, and verify account creation, validation, duplicate handling, list refresh, and logout tests.
- [x] 5.2 Apply consistent display cells, selection states, textual status labels, and empty-state messages to patients, appointments, prescriptions, and staff accounts, and verify each role receives only its existing permitted data.

## 6. Accessibility, tests, documentation, and quality verification

- [x] 6.1 Preserve existing semantic IDs and add stable IDs for new shell, navigation, section, status, feedback, and empty-state markers, and verify controls remain keyboard-focusable with visible focus and descriptive text labels.
- [x] 6.2 Update focused TestFX tests for intentional shell, navigation, and layout changes while keeping assertions semantic rather than coordinate- or pixel-dependent, and verify the complete UI test set passes.
- [x] 6.3 Run a visual smoke pass at the minimum and normal window sizes covering first-run Setup, Login, Receptionist, Doctor, System Admin, populated and empty lists, validation feedback, patient details, and rescheduling dialogs, and record observed results without real patient data.
- [x] 6.4 Update `docs/UserGuide.md`, `docs/DeveloperGuide.md`, and the dated `logs/` interaction summary with the redesigned navigation, layout conventions, semantic IDs, accessibility expectations, and verification evidence, and verify all documented labels match the implemented UI.
- [x] 6.5 Run `gradlew.bat spotlessApply check javadoc --no-daemon --console=plain`, strict OpenSpec validation for `modernize-clinic-ui`, `git diff --check`, and a final scope/privacy review; verify no persistence, domain, service, authorization, schema, or external-dependency changes were introduced.
