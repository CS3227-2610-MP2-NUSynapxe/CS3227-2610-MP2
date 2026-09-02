## 1. Queue data and authorization

- [x] 1.1 Add an administrative queue query path for selected date, Doctor, patient query, and queue status, ordered by scheduled start time.
- [x] 1.2 Reuse or extend Receptionist authorization for queue reads and check-in while preserving Doctor ownership and clinical confidentiality rules.
- [x] 1.3 Ensure check-in rejects pending, cancelled, completed, checked-out, and early appointments without changing persisted state.

## 2. Receptionist queue UI

- [x] 2.1 Add a dedicated Check-in Queue tab with Singapore-date selection, searchable Doctor and patient filters, queue-status filter, list, and waiting/checked-in summary counts.
- [x] 2.2 Display administrative appointment rows with stable semantic IDs and empty-state feedback when no appointments match.
- [x] 2.3 Open an administrative details popup when a queue appointment is selected, including permitted patient contact information and lifecycle status.
- [x] 2.4 Add an eligibility-aware Check in patient action in the popup and refresh the queue automatically after success or filter changes.
- [x] 2.5 Keep clinical fields out of all queue rows, popup content, and Receptionist service calls.

## 3. Automated verification

- [x] 3.1 Add repository tests for queue date, Doctor, patient, and status filtering and chronological ordering.
- [x] 3.2 Add service and integration tests for Receptionist authorization, early/invalid check-in rejection, successful status transition, and clinical-data preservation.
- [x] 3.3 Add TestFX coverage for queue rendering, filters, summary counts, details popup, enabled/disabled check-in, feedback, and automatic refresh.

## 4. Documentation and quality

- [x] 4.1 Update User Guide and Developer Guide with the Check-in Queue workflow, eligibility rules, filters, and confidentiality boundary.
- [x] 4.2 Add `logs/reception-check-in-queue.md` with an implementation summary that contains no real patient data.
- [x] 4.3 Run Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Javadoc, the full test suite, and strict OpenSpec validation.
- [x] 4.4 Commit implementation, tests, and documentation separately; do not push unless explicitly requested.
