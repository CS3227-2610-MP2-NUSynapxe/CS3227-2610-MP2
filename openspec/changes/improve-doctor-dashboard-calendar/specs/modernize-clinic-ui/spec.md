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

### Requirement: Shared date-picker fields SHALL use a compact width

Every JavaFX date-picker field using the shared minimal treatment SHALL use a
short, consistent preferred width that remains large enough for its formatted
date and calendar button. Context-specific filter layouts SHALL NOT expand the
control back to the previous wide field width.

#### Scenario: Staff views date pickers in different application areas

- **WHEN** staff opens a Dashboard, Calendar, appointment dialog, time-off dialog, or Receptionist filter containing a date picker
- **THEN** the date picker uses the same compact width treatment while remaining usable and readable

### Requirement: Patient Directory SHALL use one full-height results surface

The shared Doctor and Receptionist Patient Directory SHALL place its dynamic page
title inside the same white page card that contains the patient search and
results. The standalone directory description and `Patient results` heading
SHALL not be rendered. The page card SHALL fit the available page height, and
the results table SHALL expand to consume the remaining card height while its
own scrollbar handles overflow. The search field SHALL expand across the
available search row while its actions remain visible.

#### Scenario: Staff opens the Patient Directory

- **WHEN** a Doctor or Receptionist navigates to the Patient Directory
- **THEN** the title appears inside the white results card, no duplicate description or results heading is shown, the search field is wide, and the card fills the page viewport

#### Scenario: Staff resizes the Patient Directory page
- **WHEN** the directory page has more vertical space than its minimum table layout requires
- **THEN** the results table grows into that space without changing its rows or search/action controls

#### Scenario: Staff switches directory states

- **WHEN** staff opens registration, patient details, or editing from the directory
- **THEN** the same in-card title slot remains visible with the appropriate state title and the page surface retains its full-height layout
