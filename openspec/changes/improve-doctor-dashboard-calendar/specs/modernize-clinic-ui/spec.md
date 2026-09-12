## ADDED Requirements

### Requirement: Date-picker fields SHALL use a shared minimal presentation

Every JavaFX date-picker field in an application view or dialog SHALL use the
shared minimal field treatment: compact height and spacing, a consistent rounded
border, an unobtrusive embedded calendar button, and a single outer focus
indicator. The treatment SHALL preserve the field's date value, popup behavior,
keyboard interaction, accessible name, and existing validation semantics.

#### Scenario: Staff views a date-picker field

- **WHEN** a Doctor, Receptionist, or other staff member opens a view or dialog containing a date-picker field
- **THEN** the field uses the shared minimal presentation consistently with other application selectors

#### Scenario: Staff focuses or opens a date picker

- **WHEN** staff focuses the field, opens its calendar popup, or selects a date
- **THEN** focus, popup, keyboard, accessible-label, and date-selection behavior remain usable and unchanged
