## MODIFIED Requirements

### Requirement: Workspaces SHALL provide clear navigation and adapt to available space

Authenticated workspaces SHALL provide role-appropriate navigation for their
available features, keep the active destination visually identifiable, and
remain usable when the application window is resized within its supported
range. Content SHALL not rely on horizontal clipping to expose essential labels
or actions. The Doctor navigation rail SHALL present its `Dashboard` and
`Patients` destinations as full-width, contiguous items within the rail rather
than padded sub-buttons.

#### Scenario: Receptionist changes work areas

- **WHEN** a Receptionist chooses Patients, Appointments, Checkout, or Revenue
- **THEN** the selected destination is clearly indicated and its existing
  controls and data are shown without exposing controls from another work area
  as if they belonged to the selected destination

#### Scenario: Doctor changes workspace destinations

- **WHEN** a Doctor chooses `Dashboard` or `Patients`
- **THEN** the selected destination is clearly indicated and its corresponding
  content and actions are shown
- **AND** the two navigation items span the complete navigation-rail width with
  no gap between them and do not appear as nested sub-buttons

#### Scenario: Staff resizes a workspace

- **WHEN** an authenticated staff member enlarges or reduces the application
  window within the supported range
- **THEN** the header, navigation, forms, lists, and actions remain reachable,
  with scrollable content used where the available height is insufficient
