## Why

NUSynapxe now has the core clinic workflows and a functional Receptionist scheduling dashboard, but the JavaFX presentation still relies on default controls, fixed scene dimensions, dense nested tabs, and long undifferentiated form stacks. Clinic staff need a calmer, more scannable workspace that makes frequent actions and appointment status easy to understand without changing the underlying workflow or confidentiality model.

The redesign is intentionally based on the current master branch, including the completed Receptionist patient-directory and scheduling-dashboard changes. The archived `clinic-appointment-records-system` proposal remains historical and is not reopened or modified.

## What Changes

- Add a shared light clinical visual system using JavaFX CSS, consistent typography, spacing, surfaces, borders, buttons, focus states, status badges, and feedback styles.
- Replace the fixed, cramped application scene with a resizable shell and sensible minimum workspace dimensions.
- Add a shared branded header and left-side role navigation for the Receptionist, Doctor, and System Admin workspaces.
- Reflow the Receptionist patient, appointment, checkout, and revenue areas into focused card-based pages while preserving their existing operations, filters, date/time controls, popups, and automatic refresh behavior.
- Present Receptionist patients and appointments with readable custom cells, clear selection states, status labels, and useful empty states without exposing clinical information.
- Reorganize the Doctor workspace into a schedule and selected-appointment master-detail layout while preserving clinical authorization and existing consultation, prescription, availability, and completion actions.
- Reorganize System Admin account creation and staff accounts into visually distinct sections.
- Redesign first-run setup and login with the same branded form language and clearer validation feedback.
- Preserve stable semantic control IDs where possible; update TestFX coverage for any intentional navigation/container changes without relying on screen coordinates.
- Update the User Guide, Developer Guide, and development evidence to describe the new workspace layout and verification approach.
- Do not change persisted data, database schema, service-layer rules, authorization decisions, role responsibilities, or external dependencies.

## Capabilities

### New Capabilities

- `modernize-clinic-ui`: Shared visual language, resizable application shell, role navigation, and modernized authentication and clinic workspaces while preserving existing workflow behavior and confidentiality boundaries.

### Modified Capabilities

None. Existing patient-directory and receptionist-scheduling requirements remain the behavioral baseline; this change adds their redesigned presentation rather than changing their domain rules.

## Impact

- `src/main/java/nusynapxe/ui/ApplicationRouter.java`: shared scene sizing, stylesheet loading, and workspace shell coordination.
- `src/main/java/nusynapxe/ui/LoginView.java`, `SetupView.java`, `ReceptionistView.java`, `DoctorView.java`, and `SystemAdminView.java`: role-specific layout, reusable visual structure, readable list cells, and semantic styling hooks.
- `src/main/resources/`: new JavaFX stylesheet and any local UI assets required by the design; no external runtime service or dependency.
- `src/test/java/nusynapxe/ui/`: TestFX assertions for navigation, layout markers, readable content, and unchanged workflow actions.
- `docs/UserGuide.md`, `docs/DeveloperGuide.md`, and `logs/`: documentation and assignment evidence for the redesigned interface.
- No persistence, domain, service, authorization, API, or migration changes are expected.
