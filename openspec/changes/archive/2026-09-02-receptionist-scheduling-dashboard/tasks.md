## 1. Appointment query and service rules

- [x] 1.1 Add an administrative appointment query model/repository operation supporting optional date, Doctor, patient, and status filters with chronological ordering and summary counts.
- [x] 1.2 Extend appointment service authorization and validation for filtered Receptionist queries and reject booking an inactive patient while preserving existing Doctor ownership and lifecycle rules.
- [x] 1.3 Add repository/service/integration tests for filters, counts, authorization, inactive patients, conflicts, invalid intervals, and clinical-data preservation.

## 2. Receptionist scheduling dashboard

- [x] 2.1 Replace the basic appointment list with a dashboard containing summary counts, filter controls, and an administrative schedule view across all Doctors.
- [x] 2.2 Add calendar date selection and validated start/end time controls for booking and rescheduling, with stable semantic IDs and field-specific feedback.
- [x] 2.3 Add selection-based booking, rescheduling, cancellation, and check-in actions with lifecycle-aware labels/enabled state and automatic refresh after writes or filter changes.
- [x] 2.4 Add TestFX coverage for dashboard rendering, filtering, empty results, booking, conflict feedback, rescheduling, cancellation, check-in, and automatic refresh.

## 4. Scheduling input refinement

- [x] 4.1 Make patient and Doctor appointment selectors searchable editable dropdowns.
- [x] 4.2 Replace appointment start/end entry with separate hour (`00`–`23`) and minute (`00`/`30`) dropdowns.
- [x] 4.3 Move rescheduling and appointment cancellation into a patient-context popup with administrative details and automatic dashboard refresh.
- [x] 4.4 Update tests and guides, run quality gates, and commit the refinement locally without pushing.
- [x] 4.5 Separate booking from appointment search/management into dedicated tabs and remove the standalone check-in button from the dashboard.

## 3. Documentation and quality

- [x] 3.1 Update User Guide and Developer Guide with the dashboard workflow, filters, statuses, validation, conflict handling, inactive-patient rule, and confidentiality boundary.
- [x] 3.2 Record the implementation interaction summary without real patient data.
- [x] 3.3 Run focused tests, the full Gradle quality gate, strict OpenSpec validation, and privacy/diff review.
- [x] 3.4 Create separate logical local commits and do not push unless explicitly requested.
