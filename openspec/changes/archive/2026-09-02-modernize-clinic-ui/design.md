## Context

See `proposal.md` for the motivation and scope, and `specs/modernize-clinic-ui/spec.md` for the observable behavior contract. The current master branch already contains the Receptionist patient-directory and scheduling-dashboard behavior, including filters, calendar/time controls, status summaries, automatic refresh, and patient-context dialogs.

The application uses programmatic JavaFX views. The router currently recreates a fixed-size scene for each route, the views assemble their own layout primitives, and the project has no shared JavaFX stylesheet or UI component layer. Existing TestFX tests depend on semantic node IDs and exercise the service-backed event handlers. Service authorization and the separation between Receptionist administrative data and Doctor clinical data are already established boundaries.

## Goals / Non-Goals

**Goals:**

- Establish one maintainable visual language and layout vocabulary for all screens.
- Make the Receptionist workspace efficient for scanning patients, appointments, statuses, and action targets.
- Make the Doctor's selected appointment and clinical editing context visible at the same time as the schedule.
- Give authentication and System Admin screens the same visual quality as the role workspaces.
- Keep the redesign incremental enough that existing business handlers and semantic UI tests remain useful.

**Non-Goals:**

- Changing service APIs, persistence projections, schema versions, authorization rules, role responsibilities, or workflow transitions.
- Adding dashboard metrics, reminders, drag-and-drop scheduling, patient self-service, or new clinic features merely to fill the redesigned layout.
- Adding an icon library, remote font, web view, network service, or other runtime dependency.
- Making a responsive web-style interface; the target remains a resizable desktop application with scrollable content where required.

## Decisions

### Use a shared JavaFX stylesheet and small presentation helpers

Add one application stylesheet at `src/main/resources/nusynapxe/ui.css` and load it for every routed scene. Use a small set of presentation helpers for headings, cards, field groups, buttons, status badges, feedback banners, and empty states. Views remain programmatic JavaFX views, but repeated visual structure is centralized instead of being recreated with ad hoc padding and gaps.

The initial visual tokens are a light clinical palette: a cool near-white application background, white content surfaces, dark navy text, teal primary actions, slate secondary text, and high-contrast semantic status colors. Use a system sans-serif fallback such as Segoe UI, an 8/12/16/24 spacing scale, restrained 10-12px corner radii, and subtle borders rather than gradients or heavy shadows. Status badges always include readable text; color is supplementary.

This approach is preferred over inline styles because it keeps the visual system inspectable and adjustable, and over adding a third-party control theme because the project already has a pinned JavaFX-only desktop stack and no need for a new dependency.

### Introduce a shared workspace shell without changing service ownership

Create a common shell pattern with a branded header, current role, signed-in identity, logout action, optional left navigation, and a central content region. The router will apply the stylesheet, allow the stage to resize, provide a compact restored workspace size, and enforce a documented minimum size. It will reuse one scene and replace its root during route changes so the stage does not resize or lose its maximized state. The application entry point opens the stage maximized so the workspace fits the available monitor work area while retaining normal window controls. Authentication views use the same shell language but center a focused form card rather than showing the workspace rail.

The Receptionist's existing top-level `TabPane` will be presented as the left navigation rail, preserving `reception-workspace-tabs` and its selection-driven refresh behavior. Patient and appointment subflows remain separate inside their existing containers, but their secondary tabs will be styled as compact section navigation. Doctor and System Admin have fewer destinations, so their shell presents only meaningful role navigation and does not create empty or misleading pages.

Keeping the existing top-level navigation container where practical reduces behavioral risk: current tab selection and refresh logic can remain intact while the visual presentation changes. New shell nodes receive stable IDs, and existing action/control IDs remain unchanged unless a structural replacement makes an equivalent ID impossible.

### Recompose each role around its actual operating task

- **Receptionist:** Use a page heading and short description, then place the active form or search/filter toolbar in a card above or beside the affected list. Registration fields are grouped into identity, contact, and optional measurements. Appointment booking remains distinct from appointment management; management combines filters, summary counts, a readable schedule list, and selection-based actions. Checkout keeps the selected appointment list adjacent to the payment form, and revenue gives the result its own prominent summary surface.
- **Doctor:** Use a horizontal master-detail composition. The left pane contains the assigned appointment schedule and refresh control; the right pane contains the selected appointment context, consultation form, prescription form/list, availability controls, and completion action in visually separated sections. The detail pane shows a clear no-selection state until an appointment is selected and is scrollable independently when needed.
- **System Admin:** Use separate account-creation and current-staff cards, with role information and account rows formatted for scanning. Keep the account form action near its fields and feedback directly below it.
- **Login and Setup:** Use a branded identity area beside or above a compact form card. Preserve default-button behavior, existing field IDs, non-sensitive messages, and the current route transitions.

The layout will use `BorderPane`, `SplitPane`, `FlowPane`, and scrollable card containers deliberately: fixed header/navigation regions should not scroll away, while forms and long lists may scroll within the central content area. At smaller supported widths, cards may stack vertically; essential labels and actions must remain reachable.

### Use dedicated display cells for operational records

Replace raw list rendering with dedicated display cells or equivalent presentation nodes. Appointment rows will show the permitted patient context, Doctor, date/time, and a textual lifecycle status. Patient, prescription, and account rows will use concise labels and supporting metadata appropriate to the current role. Empty lists receive explicit placeholders that explain what is absent and how to proceed.

Display models or precomputed labels may be created at the UI boundary so cell refreshes do not repeat avoidable lookups. They must use only data already authorized for the view; Receptionist appointment and patient displays must never include clinical records or prescriptions. Business validation and lifecycle decisions remain in the existing services.

### Preserve semantic testability and verify behavior independently of appearance

Retain the documented TestFX IDs on inputs, actions, lists, feedback, modal stages, and major navigation containers. Add IDs only for new shell and empty-state markers that need stable assertions. Tests should verify visible labels, navigation selection, record summaries, status text, empty states, keyboard-reachable actions, and existing workflow outcomes rather than exact coordinates or fragile pixel measurements.

The focused UI tests will be updated alongside each structural change. A final visual smoke pass will cover login/setup, each role workspace, the minimum supported size, a normal workspace size, populated lists, empty lists, validation feedback, and the patient-details/reschedule dialogs. The full Gradle quality gate remains the final automated check.

## Risks / Trade-offs

- [Risk] JavaFX CSS and default control skins can render slightly differently across Windows, Linux, and macOS. -> Mitigation: use standard JavaFX CSS properties, system-font fallbacks, semantic assertions, and visual checks at representative supported sizes instead of pixel-perfect tests.
- [Risk] Refactoring the large programmatic views can accidentally detach an event handler or refresh listener. -> Mitigation: preserve existing controls and IDs where possible, refactor one workspace section at a time, and run focused TestFX workflows after each role migration.
- [Risk] A reusable display component could accidentally receive confidential clinical data. -> Mitigation: keep role-specific display construction explicit, pass administrative projections to Receptionist components, and retain service-layer authorization as the independent enforcement boundary.
- [Risk] A left rail and larger cards may consume more horizontal space than the current tabs. -> Mitigation: set a documented minimum width, allow the central region to scroll or stack cards, and keep essential action labels visible at the minimum supported size.
- [Risk] More readable custom cells increase UI code in an already large view. -> Mitigation: extract only repeated presentation concerns into small helpers or focused cell factories; do not create thin wrappers around business logic.

## Migration Plan

1. Add the shared stylesheet and presentation helpers, then apply the shell sizing and stylesheet from the router without changing service calls.
2. Recompose the Receptionist workspace while preserving existing top-level and subflow navigation containers, IDs, refresh listeners, and action handlers.
3. Recompose Doctor, System Admin, Login, and Setup, adding custom cells, selection states, feedback surfaces, and empty states as each view is migrated.
4. Update TestFX coverage, guides, and the interaction log after the layout is stable; run formatting, focused tests, the full quality gate, and strict OpenSpec validation.
5. Roll back by reverting the UI source, stylesheet, tests, and documentation changes. No database or persisted-data rollback is required because the design does not alter storage or service semantics.
