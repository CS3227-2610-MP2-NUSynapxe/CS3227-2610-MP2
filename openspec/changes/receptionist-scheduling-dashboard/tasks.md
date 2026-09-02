## 1. Appointment query and service rules

- [x] 1.1 Add an administrative appointment query model/repository operation supporting optional date, Doctor, patient, and status filters with chronological ordering and summary counts.
- [x] 1.2 Extend appointment service authorization and validation for filtered Receptionist queries and reject booking an inactive patient while preserving existing Doctor ownership and lifecycle rules.
- [x] 1.3 Add repository/service/integration tests for filters, counts, authorization, inactive patients, conflicts, invalid intervals, and clinical-data preservation.

## 2. Receptionist scheduling dashboard

- [x] 2.1 Replace the basic appointment list with a dashboard containing summary counts, filter controls, and an administrative schedule view across all Doctors.
- [x] 2.2 Add calendar date selection and validated start/end time controls for booking and rescheduling, with stable semantic IDs and field-specific feedback.
- [x] 2.3 Add selection-based booking, rescheduling, cancellation, and check-in actions with lifecycle-aware labels/enabled state and automatic refresh after writes or filter changes.
- [x] 2.4 Add TestFX coverage for dashboard rendering, filtering, empty results, booking, conflict feedback, rescheduling, cancellation, check-in, and automatic refresh.

## 3. Documentation and quality

- [x] 3.1 Update User Guide and Developer Guide with the dashboard workflow, filters, statuses, validation, conflict handling, inactive-patient rule, and confidentiality boundary.
- [x] 3.2 Record the implementation interaction summary without real patient data.
- [x] 3.3 Run focused tests, the full Gradle quality gate, strict OpenSpec validation, and privacy/diff review.
- [x] 3.4 Create separate logical local commits and do not push unless explicitly requested.
