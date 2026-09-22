# Code-quality refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the identified performance, time-consistency, JavaFX-threading, god-file, and quality-gate risks while preserving NuSynapxe's workflows, privacy rules, and role boundaries.

**Architecture:** Keep authorization and domain mutations in services, add bounded read projections for list/report screens, and inject one Singapore-zone `Clock` through persistence, services, and UI composition. Use a serialized background task runner for database work, then make the two largest views composition shells around feature-specific panes and shared exporters.

**Tech Stack:** Java 25, JavaFX 25, SQLite JDBC, Gradle, JUnit 6, Mockito, TestFX, ArchUnit, Checkstyle, PMD, SpotBugs, JaCoCo, and Spotless.

**Spec:** `docs/superpowers/specs/2026-09-22-code-quality-refactor-design.md`

## Global Constraints

- Preserve all existing clinic workflows, role boundaries, authorization checks, and patient-data visibility rules.
- The clinic timezone remains `Asia/Singapore`; only implicit "now" behavior consults the injected clock.
- The SQLite database abstraction owns a shared connection, so database tasks execute serially rather than through unconstrained parallel access.
- Blocking database work stays off the JavaFX application thread; UI callbacks run on the JavaFX application thread.
- Existing logs, teammate reflections, unrelated documentation, and unrelated cleanup remain outside the refactor.
- Every behavior change follows red-green-refactor and ends in its own commit.
- The final branch must pass the normal Gradle verification task before issues and the pull request are created.

## Review Focus

- Singapore midnight on a non-Singapore host must produce the Singapore clinic date and timestamp: pinned by `ClinicClockTest` and repository timestamp tests in Task 1.
- Missing patient or doctor rows must render a deliberate read-model fallback without issuing per-cell service calls: pinned by `AppointmentRepositoryTest` and the appointment projection UI test in Task 2.
- A patient with zero prescriptions and a patient with several terminal records must retain history order and prescription grouping with one batched prescription query: pinned by `ClinicalRecordRepositoryTest` in Task 3.
- Revenue ranges must include both endpoints and apply the payment-method filter in SQL rather than after one query per day: pinned by `ReceiptRepositoryTest` and `BillingServiceTest` in Task 4.
- A slow older load must not overwrite a newer load, and closing a view must prevent callbacks from touching controls: pinned by `ClinicTaskRunnerTest` and focused TestFX lifecycle tests in Task 6.

---

## File map

The following files are the planned ownership boundaries. Existing constructors remain as compatibility overloads only when current tests or application wiring require them; those overloads delegate to the injected production defaults.

- `src/main/java/nusynapxe/service/ClinicClock.java`: production Singapore-zone clock factory and date/time helpers.
- `src/main/java/nusynapxe/domain/AppointmentListRow.java`: immutable appointment-table read model containing the appointment and preloaded display names.
- `src/main/java/nusynapxe/ui/ClinicTaskRunner.java` and `src/main/java/nusynapxe/ui/SerializedClinicTaskRunner.java`: checked-exception task contract and serialized executor implementation.
- `src/main/java/nusynapxe/ui/ReportExporter.java`: pure CSV/JSON serialization and file-writing behavior extracted from the receptionist controller.
- `src/main/java/nusynapxe/ui/ReceptionistView.java`: composition shell and shared lifecycle wiring after feature extraction.
- `src/main/java/nusynapxe/ui/ReceptionistAppointmentView.java`, `ReceptionistCheckoutView.java`, and `ReceptionistRevenueView.java`: receptionist feature panes with narrow dependencies.
- `src/main/java/nusynapxe/ui/DoctorView.java`, `DoctorConsultationView.java`, and `DoctorPatientFormView.java`: doctor shell and extracted consultation/patient-form responsibilities.
- `src/main/java/nusynapxe/persistence/*.java`, `src/main/java/nusynapxe/service/*.java`, and `src/main/java/nusynapxe/ui/*.java`: only the specific clock/task/read-model consumers listed by each task.
- `src/test/java/nusynapxe/service/ClinicClockTest.java`, `src/test/java/nusynapxe/persistence/*Test.java`, `src/test/java/nusynapxe/service/*Test.java`, and focused UI tests: red-green-regression coverage for each task.
- `build.gradle`, `config/checkstyle/checkstyle.xml`, `config/pmd/*.xml`, and `src/test/java/nusynapxe/architecture/ArchitectureTest.java`: executable maintainability gates in Task 8.

## Task 1: Centralize the clinic clock

**Files:**

- Create: `src/main/java/nusynapxe/service/ClinicClock.java`
- Modify: `src/main/java/nusynapxe/service/ClinicServices.java`, `AppointmentService.java`, and `BillingService.java`
- Modify: `src/main/java/nusynapxe/persistence/AccountRepository.java`, `AppointmentRepository.java`, `PatientRepository.java`, `ClinicalRecordRepository.java`, and `PaymentRepository.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistView.java` and any remaining production UI file found by the `LocalDate.now()`/`LocalDateTime.now()` search
- Test: `src/test/java/nusynapxe/service/ClinicClockTest.java` and the affected repository/service tests

**Interfaces:**

- Produce `ClinicClock.ZONE`, `ClinicClock.system()`, and `ClinicClock.today(Clock)`; `system()` returns `Clock.system(ClinicClock.ZONE)`.
- Produce `ClinicServices.forDatabase(SqliteDatabase, Clock)` while retaining `forDatabase(SqliteDatabase)` as a delegating production overload.
- Produce clock-aware constructors for every component that persists an implicit timestamp; all default constructors delegate to `ClinicClock.system()`.
- Consume `Clock` in `LocalDateTime.now(clock)` and `LocalDate.now(clock)` calls only; explicit business dates remain method parameters.

- [ ] **Step 1: Write the failing clock-boundary tests.**

  Add a fixed-clock test with an instant that is `2026-09-21T16:30:00Z`, whose Singapore date is `2026-09-22`, and assert that `ClinicClock.today(fixedClock)` returns `2026-09-22` even when the fixed clock is created with `ZoneOffset.UTC`. Add a repository test that creates a payment or prescription with that clock and asserts its persisted local timestamp is `2026-09-22 00:30` rather than the machine's default-zone date.

  ```java
  @Test
  void clinicDateUsesSingaporeZoneAtUtcMidnightBoundary() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-21T16:30:00Z"), ZoneOffset.UTC);

    assertEquals(LocalDate.of(2026, 9, 22), ClinicClock.today(clock));
  }
  ```

- [ ] **Step 2: Run the focused tests and verify the new API fails.**

  Run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.service.ClinicClockTest --tests nusynapxe.persistence.ClinicRepositoryTest --offline --no-daemon --console=plain
  ```

  Expected: compilation or test failure because the clock factory and clock-aware persistence wiring do not yet exist.

- [ ] **Step 3: Implement the clock and wire every implicit-now call.**

  Add `ClinicClock`, pass one clock from `ClinicServices.forDatabase(database, clock)` into the appointment service, billing service, and timestamp-writing repositories, and replace every production `LocalDateTime.now()`/`LocalDate.now()` that represents clinic time. Keep the existing public default constructors by delegating to `ClinicClock.system()`. Use `Clock` to derive checkout receipt dates instead of a second literal `ZoneId`.

  ```java
  public static ClinicServices forDatabase(SqliteDatabase database, Clock clock) {
    Objects.requireNonNull(database, "database");
    Clock clinicClock = Objects.requireNonNull(clock, "clock");
    AccountRepository accounts = new AccountRepository(database, clinicClock);
    PatientRepository patients = new PatientRepository(database, clinicClock);
    AppointmentRepository appointments = new AppointmentRepository(database, clinicClock);
    ClinicalRecordRepository records = new ClinicalRecordRepository(database, clinicClock);
    AppointmentService appointmentService =
        new AppointmentService(appointments, accounts, patients, clinicClock);
    return new ClinicServices(
        new AccountService(accounts),
        new AuthenticationService(accounts),
        new PatientService(patients, appointments, records),
        appointmentService,
        new ClinicalService(appointments, records),
        new BillingService(new PaymentRepository(database, clinicClock), appointmentService, clinicClock),
        new CalendarService(accounts, appointments, new CalendarSettingsRepository(database)));
  }
  ```

- [ ] **Step 4: Run clock and persistence tests to verify the implementation passes.**

  Run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.service.ClinicClockTest --tests nusynapxe.persistence.*Test --tests nusynapxe.service.BillingServiceTest --offline --no-daemon --console=plain
  ```

  Expected: PASS, with existing authorization and persistence tests unchanged.

- [ ] **Step 5: Refactor duplicated constructor setup and run the formatting gate.**

  Remove duplicate zone literals and constructor-specific default-clock logic, then run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat spotlessApply compileJava compileTestJava --offline --no-daemon --console=plain
  ```

- [ ] **Step 6: Commit the clock change.**

  ```powershell
  git add src/main/java src/test/java
  git commit -m "refactor: centralize clinic clock"
  ```

## Task 2: Add the appointment-table read projection

**Files:**

- Create: `src/main/java/nusynapxe/domain/AppointmentListRow.java`
- Modify: `src/main/java/nusynapxe/persistence/AppointmentRepository.java` and `src/main/java/nusynapxe/service/AppointmentService.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistView.java` and its extracted appointment-pane code if Task 6 has already started
- Test: `src/test/java/nusynapxe/persistence/AppointmentRepositoryScheduleTest.java`, `src/test/java/nusynapxe/service/AppointmentServiceTest.java`, and `src/test/java/nusynapxe/ui/ReceptionistViewTest.java`

**Interfaces:**

- `AppointmentListRow` is a record with `Appointment appointment`, `String patientDisplayName`, and `String doctorDisplayName`.
- `AppointmentRepository.searchListRows(LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)` returns an immutable list from one joined SQL query.
- `AppointmentService.searchAppointmentRows(Session actor, LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)` performs the existing Receptionist authorization check before delegating.
- Appointment table columns read only row fields; no cell factory calls a service or repository.

- [ ] **Step 1: Write the failing projection and query-count tests.**

  Insert one appointment with a patient and doctor, call the new service method as a Receptionist, and assert both display names are present. Add a second fixture with a missing related display row only if the schema permits it; otherwise assert the projection's explicit fallback mapping for a blank name. Add a source-level UI test that searches `ReceptionistView.java` and fails if `patientDisplayName(` or `doctorDisplayName(` occurs inside the appointment table method.

  ```java
  @Test
  void appointmentListRowsContainNamesWithoutPerCellLookup() throws SQLException {
    List<AppointmentListRow> rows = appointments.searchListRows(null, null, "", null);

    assertThat(rows).hasSize(1);
    assertEquals("Ada Lovelace", rows.getFirst().patientDisplayName());
    assertEquals("Dr. Turing", rows.getFirst().doctorDisplayName());
  }
  ```

- [ ] **Step 2: Run the focused test and verify the projection API is absent.**

  Run `.\gradlew.bat test --tests nusynapxe.persistence.AppointmentRepositoryScheduleTest --tests nusynapxe.service.AppointmentServiceTest --offline --no-daemon --console=plain` with the clinic JBR. Expected: compilation failure for `AppointmentListRow`/`searchListRows`.

- [ ] **Step 3: Implement the joined read query and service boundary.**

  Reuse the existing appointment filters, join `patients` and doctor `users`, select all fields needed by the table, map one `AppointmentListRow` per appointment, and preserve the existing date/doctor/patient/status semantics. Keep mutation methods returning `Appointment` and keep their authorization paths unchanged.

  ```java
  public List<AppointmentListRow> searchAppointmentRows(
      Session actor, LocalDate date, Long doctorId, String patientQuery, AppointmentStatus status)
      throws SQLException {
    Authorization.requireRole(actor, Role.RECEPTIONIST);
    return appointments.searchListRows(date, doctorId, patientQuery, status);
  }
  ```

- [ ] **Step 4: Replace appointment cell-factory lookups with projection bindings.**

  Change the appointment, queue, and checkout tables to `TableView<AppointmentListRow>`, bind patient/doctor columns to the preloaded strings, and unwrap `row.appointment()` only for selection and mutation actions. Delete the cell-factory fallback methods after all callers are removed.

- [ ] **Step 5: Run repository, service, and receptionist regression tests.**

  Run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test --tests nusynapxe.persistence.AppointmentRepositoryScheduleTest --tests nusynapxe.service.AppointmentServiceTest --tests nusynapxe.ui.ReceptionistViewTest --offline --no-daemon --console=plain
  ```

  Expected: PASS, including existing booking, check-in, checkout-selection, and role tests.

- [ ] **Step 6: Commit the appointment projection.**

  ```powershell
  git add src/main/java src/test/java
  git commit -m "refactor: add appointment list projection"
  ```

## Task 3: Harden batched clinical-history prescription loading

**Files:**

- Modify: `src/main/java/nusynapxe/persistence/ClinicalRecordRepository.java`
- Test: `src/test/java/nusynapxe/persistence/ClinicRepositoryTest.java` or a new `ClinicalRecordRepositoryTest.java`, plus `src/test/java/nusynapxe/service/ClinicalServiceTest.java`

**Interfaces:**

- Keep `ClinicalRecordRepository.findHistoryByPatient(long)` as the Doctor history API.
- Replace patient-wide prescription enrichment with a single `findPrescriptionsByRecordIds(Set<Long>)` helper that returns `Map<Long, List<Prescription>>` and returns an empty immutable map for an empty ID set.
- Preserve newest-appointment-first ordering, empty prescription lists, and the existing Doctor-only service authorization.

The current repository already contains a batched patient prescription query, so this task is a regression-hardening change: the new package-level helper test must prove that one enrichment query can be scoped to an explicit record-ID set, and the public history method must use that helper rather than re-scanning unrelated patient prescriptions.

- [ ] **Step 1: Write the failing scope/order regression test.**

  In the `nusynapxe.persistence` test package, call the new package-level helper with `Set.of(newestRecordId)` after seeding two terminal records and prescriptions for both. Assert that only the requested record is present, then exercise the public history method through `ClinicalService` and assert newest-first order and correct grouping. Add a query-observer fixture that records prepared SQL and asserts one prescription enrichment statement.

  ```java
  @Test
  void historyBatchLoadsOnlyRequestedRecordsOnce() throws SQLException {
    Map<Long, List<Prescription>> selected =
        repository.findPrescriptionsByRecordIds(Set.of(newestRecordId));

    assertEquals(Set.of(newestRecordId), selected.keySet());
    assertEquals(List.of("new medicine"), selected.get(newestRecordId).stream()
        .map(Prescription::medication).toList());
    assertTrue(preparedSql.stream().filter(sql -> sql.contains("FROM prescriptions")).count() <= 1);
  }
  ```

- [ ] **Step 2: Run the clinical-history tests and verify the new helper is absent.**

  Run the focused repository and service tests with Gradle. Expected: compilation failure because `findPrescriptionsByRecordIds(Set<Long>)` does not yet exist.

- [ ] **Step 3: Implement the record-ID batch query.**

  Collect `projection.record().id()` values, construct one parameterized `IN` clause with one question-mark placeholder per ID, bind only those IDs, order by `clinical_record_id, id`, group by record ID, and use `getOrDefault(recordId, List.of())` when building entries. Do not call `findPrescriptions` from a loop.

- [ ] **Step 4: Run the focused history tests and verify authorization remains intact.**

  Run `ClinicalRecordRepositoryTest`, `ClinicalServiceTest`, and the Doctor history TestFX test. Expected: PASS for zero-prescription records, multiple prescriptions, terminal-status filtering, and non-Doctor rejection.

- [ ] **Step 5: Commit the clinical-history change.**

  ```powershell
  git add src/main/java/nusynapxe/persistence/ClinicalRecordRepository.java src/test/java/nusynapxe/persistence src/test/java/nusynapxe/service/ClinicalServiceTest.java
  git commit -m "perf: batch clinical history prescriptions"
  ```

## Task 4: Query revenue by date range and method

**Files:**

- Modify: `src/main/java/nusynapxe/persistence/ReceiptRepository.java` and `src/main/java/nusynapxe/service/BillingService.java`
- Test: `src/test/java/nusynapxe/persistence/ClinicRepositoryTest.java` or a new `ReceiptRepositoryTest.java`, and `src/test/java/nusynapxe/service/BillingServiceTest.java`

**Interfaces:**

- Add `ReceiptRepository.findRevenue(LocalDate from, LocalDate to, String patientQuery, Long doctorId, PaymentMethod method)` with inclusive dates and a method predicate in SQL.
- Make `BillingService.revenueReport(Session actor, LocalDate from, LocalDate to, String patientQuery, Long doctorId, PaymentMethod method)` call `findRevenue(LocalDate from, LocalDate to, String patientQuery, Long doctorId, PaymentMethod method)` exactly once and preserve the existing `RevenueReport` ordering and authorization.
- Keep `findAll(String patientQuery, Long doctorId, LocalDate date)` for receipt-history screens.

- [ ] **Step 1: Write the failing range/filter test.**

  Seed successful receipts on the start date, end date, and one day outside the range, with at least two payment methods. Assert that both endpoints are returned, the outside row is absent, and a method filter returns only the requested method. Add a spy or SQL-observer assertion that the service executes one range query rather than one query per date.

  ```java
  @Test
  void revenueReportUsesOneInclusiveRangeQueryAndMethodFilter() throws SQLException {
    RevenueReport report = billing.revenueReport(receptionist, from, to, "", null, PaymentMethod.CARD);

    assertThat(report.receipts()).extracting(Receipt::receiptDate)
        .containsExactly(from, to);
    assertTrue(preparedSql.stream().filter(sql -> sql.contains("FROM receipts")).count() == 1);
  }
  ```

- [ ] **Step 2: Run the focused tests and verify the new repository method is absent.**

  Run the repository and billing test classes. Expected: compilation failure for `findRevenue` or a failing query-count assertion against the date loop.

- [ ] **Step 3: Implement the SQL range query.**

  Extend the receipt projection with `r.receipt_date >= ? AND r.receipt_date <= ?` and `(? IS NULL OR r.method = ?)` predicates, bind the trimmed patient search and optional doctor/method values, and order by receipt date and sequence number exactly as the current report expects.

- [ ] **Step 4: Replace the date loop in `BillingService`.**

  Validate the existing null/reversed-date errors, call `receipts.findRevenue(from, to, patientQuery, doctorId, method)` once, and construct `new RevenueReport(result)` without in-memory method filtering.

- [ ] **Step 5: Run billing and UI revenue regressions.**

  Run `BillingServiceTest`, repository tests, and `ReceptionistViewTest` with the clinic JBR. Expected: PASS for date boundaries, patient/doctor filters, all-method reports, method-specific reports, and reversed ranges.

- [ ] **Step 6: Commit the revenue query.**

  ```powershell
  git add src/main/java/nusynapxe/persistence/ReceiptRepository.java src/main/java/nusynapxe/service/BillingService.java src/test/java
  git commit -m "perf: query revenue by range"
  ```

## Task 5: Return checkout receipts through a direct lookup

**Files:**

- Modify: `src/main/java/nusynapxe/persistence/ReceiptRepository.java`, `src/main/java/nusynapxe/service/BillingService.java`, and the checkout dialog portion of `src/main/java/nusynapxe/ui/ReceptionistView.java`
- Test: `src/test/java/nusynapxe/persistence/ClinicRepositoryTest.java`, `src/test/java/nusynapxe/service/BillingServiceTest.java`, and `src/test/java/nusynapxe/ui/ReceptionistViewTest.java`

**Interfaces:**

- Add `ReceiptRepository.findByAppointment(long appointmentId)` returning `Optional<Receipt>`.
- Add `BillingService.receiptForAppointment(Session actor, long appointmentId)` with the existing Receptionist authorization check.
- Keep `BillingService.checkout(Session actor, long appointmentId, long amountMinor, PaymentMethod method)` returning `Payment` for existing callers; the UI obtains the created receipt through the direct appointment lookup rather than loading receipt history.

- [ ] **Step 1: Write the failing direct-lookup test.**

  Complete checkout in a fixture, call `receiptForAppointment`, and assert the receipt ID, appointment ID, amount, and method. Add an absent-receipt case returning `Optional.empty()` and a non-Receptionist case throwing `AuthorizationException`. Add a source assertion that the checkout handler does not call `receiptHistory`.

  ```java
  @Test
  void receiptForAppointmentReturnsOnlyTheCheckoutReceipt() throws SQLException {
    Optional<Receipt> receipt = billing.receiptForAppointment(receptionist, appointmentId);

    assertTrue(receipt.isPresent());
    assertEquals(appointmentId, receipt.orElseThrow().appointmentId());
  }
  ```

- [ ] **Step 2: Run focused tests and verify the direct API is absent.**

  Expected: compilation failure for `findByAppointment`/`receiptForAppointment`.

- [ ] **Step 3: Implement the direct receipt query and service method.**

  Query the existing receipt projection with `WHERE r.appointment_id = ?`, map the row with the same `read` method as `findById`, return an empty optional for no receipt, and enforce Receptionist authorization in the service.

- [ ] **Step 4: Update checkout display and verify UI behavior.**

  Replace the full `receiptHistory(session, "", null, null).stream().filter(receipt -> receipt.appointmentId() == appointmentId)` path with `receiptForAppointment(session, appointmentId).ifPresent(receipt -> receiptPreview.setText(formatReceipt(receipt)))`; leave receipt-history search behavior unchanged. Run the checkout TestFX flow and billing tests.

- [ ] **Step 5: Commit the checkout receipt change.**

  ```powershell
  git add src/main/java/nusynapxe/persistence/ReceiptRepository.java src/main/java/nusynapxe/service/BillingService.java src/main/java/nusynapxe/ui/ReceptionistView.java src/test/java
  git commit -m "refactor: return checkout receipt directly"
  ```

## Task 6: Move clinic database work behind a serialized task runner

**Files:**

- Create: `src/main/java/nusynapxe/ui/ClinicTaskRunner.java` and `SerializedClinicTaskRunner.java`
- Modify: `src/main/java/nusynapxe/NUSynapxeApp.java`, `src/main/java/nusynapxe/ui/ApplicationRouter.java`, `ReceptionistView.java`, `ReceptionistCalendarView.java`, `CalendarScheduleList.java`, `ClinicalHistoryView.java`, and `DoctorView.java`
- Test: `src/test/java/nusynapxe/ui/ClinicTaskRunnerTest.java`, `CalendarScheduleListTest.java`, and focused TestFX tests

**Interfaces:**

- `ClinicTaskRunner.submit(ClinicTask<T>, Consumer<T>, Consumer<Throwable>)` runs checked database work off the FX thread and schedules both callbacks on the FX thread.
- `ClinicTaskRunner.close()` stops accepting work and shuts down the executor before `SqliteDatabase.close()`.
- `ReceptionistView.create(ClinicServices, Session, Runnable, ClinicTaskRunner)` and the other views that load database data accept a runner overload; existing factory methods delegate to a production runner supplied by application composition.
- Tests use an immediate deterministic runner implementing the same interface, so service assertions do not require sleeps.

- [ ] **Step 1: Write failing runner and stale-result tests.**

  Test that two submitted tasks execute in submission order on one worker, that a thrown exception reaches the failure callback, and that a generation guard ignores an older result after a newer load starts. Test that a closed runner rejects new work and that a view's close handler prevents callback application.

  ```java
  @Test
  void serializedRunnerExecutesTasksInSubmissionOrder() throws Exception {
    List<Integer> order = new CopyOnWriteArrayList<>();
    try (ClinicTaskRunner runner = new SerializedClinicTaskRunner()) {
      runner.submit(() -> { order.add(1); return null; }, ignored -> {}, failures::add);
      runner.submit(() -> { order.add(2); return null; }, ignored -> {}, failures::add);
    }
    assertEquals(List.of(1, 2), order);
  }
  ```

- [ ] **Step 2: Run the new tests and verify the runner is absent.**

  Expected: compilation failure for `ClinicTaskRunner` and `SerializedClinicTaskRunner`.

- [ ] **Step 3: Implement the serialized runner.**

  Use a single-thread `ExecutorService`, reject submissions after close, catch `Exception` from `ClinicTask`, and call `Platform.runLater` only for success/failure callbacks. Add a package-private immediate runner for tests or a test-local implementation without sleeps. Keep the runner independent of `ClinicServices` so it can be reused by receptionist and doctor views.

- [ ] **Step 4: Wire application lifecycle and move blocking loads.**

  Create one production runner alongside the opened database, pass it through `ApplicationRouter` into feature views, submit receptionist table/report loads, calendar page loads, patient/history loads, and doctor loads through it, and close it before the database. Replace `Platform.runLater` calls that currently defer JDBC work with task submission; retain `Platform.runLater` for control updates only.

- [ ] **Step 5: Add generation guards and deterministic UI tests.**

  Give each reloadable pane a monotonically increasing request number, capture it in the task callback, and apply results only when it is still current and the pane is attached. Test rapid filter changes, task failure messages, empty results, and view shutdown in `CalendarScheduleListTest`, `ReceptionistViewTest`, and `DoctorViewTest`.

- [ ] **Step 6: Run the threading and application regression suite.**

  Run the task-runner unit test, all affected UI tests, and `ClinicWorkflowIntegrationTest`. Expected: PASS without sleeps added to production code and without JDBC calls from the FX thread.

- [ ] **Step 7: Commit the threading change.**

  ```powershell
  git add src/main/java src/test/java
  git commit -m "refactor: move clinic database work off the fx thread"
  ```

## Task 7: Split the large clinic views and exporter

**Files:**

- Create: `src/main/java/nusynapxe/ui/ReportExporter.java`, `ReceptionistAppointmentView.java`, `ReceptionistCheckoutView.java`, `ReceptionistRevenueView.java`, `DoctorConsultationView.java`, and `DoctorPatientFormView.java`
- Modify: `src/main/java/nusynapxe/ui/ReceptionistView.java`, `DoctorView.java`, `PatientDirectoryView.java`, and the relevant router/factory wiring
- Test: `src/test/java/nusynapxe/ui/ReportExporterTest.java`, `ReceptionistViewTest.java`, and `DoctorViewTest.java`

**Interfaces:**

- `ReportExporter.toCsv(RevenueReport)` and `ReportExporter.toJson(RevenueReport)` are pure deterministic serializers; `writeCsv(Path, RevenueReport)` and `writeJson(Path, RevenueReport)` own file I/O.
- `ReceptionistView` owns composition, shared session/feedback state, and pane lifecycle; feature views own their controls, handlers, refresh functions, and service dependencies.
- `DoctorView` owns shell/tab composition; consultation and patient-form views own their controls and event handlers. Authorization remains in services.
- Existing JavaFX control IDs and user-visible labels remain unchanged unless a test demonstrates that an ID is unused.

- [ ] **Step 1: Write failing exporter and composition tests.**

  Add pure serializer tests for CSV escaping, JSON control-character escaping, empty reports, and stable row order. Add TestFX assertions that the receptionist and doctor shells still expose existing tab/control IDs. Add source-size assertions that the composition shells remain at or below 500 physical lines for `ReceptionistView.java` and 450 physical lines for `DoctorView.java`.

  ```java
  @Test
  void csvEscapesCommaQuoteAndNewline() {
    RevenueReport report = new RevenueReport(List.of(receipt("Ada, \"A\"\nLovelace")));

    assertEquals("patient,doctor,amount\n\"Ada, \"\"A\"\"\nLovelace\",Dr. Turing,1000\n",
        ReportExporter.toCsv(report));
  }
  ```

- [ ] **Step 2: Run exporter and UI tests to establish the missing extraction API.**

  Expected: compilation failure for `ReportExporter` and, for composition tests, failures identifying the control ownership still embedded in the god files.

- [ ] **Step 3: Extract pure report serialization first.**

  Move CSV/JSON escaping and formatting into `ReportExporter`, keep file chooser selection in `ReceptionistRevenueView`, and verify that export buttons still write the same bytes for the same report.

- [ ] **Step 4: Extract receptionist appointment, checkout, and revenue panes.**

  Move each control group and its handlers as a cohesive unit, inject only the services/session/feedback/runner it uses, and return a root `Parent` plus explicit refresh/select methods used by the shell. Keep patient-directory composition separate from appointment mutation logic.

- [ ] **Step 5: Extract doctor consultation and patient-form panes.**

  Move consultation notes/prescription controls and patient detail/edit controls into their respective classes, preserve the existing Doctor ownership checks by calling `ClinicalService`/`PatientService`, and let `DoctorView` retain only routing, tabs, and shared feedback.

- [ ] **Step 6: Run the full focused UI suite and inspect class responsibilities.**

  Run `ReceptionistViewTest`, `DoctorViewTest`, `DoctorCalendarViewTest`, `ReportExporterTest`, and `ApplicationRouterTest`. Review the diff for duplicate service construction, moved authorization logic, changed control IDs, and callbacks retaining references to closed stages.

- [ ] **Step 7: Commit the UI decomposition.**

  ```powershell
  git add src/main/java/nusynapxe/ui src/test/java/nusynapxe/ui
  git commit -m "refactor: split large clinic views"
  ```

## Task 8: Enforce maintainability quality gates

**Files:**

- Modify: `build.gradle`
- Modify: `config/checkstyle/checkstyle.xml`, `config/pmd/ruleset.xml`, and `config/pmd/test-ruleset.xml` only where the baseline requires a justified rule configuration
- Modify: `config/spotbugs/exclude-filter.xml` only for a documented false positive
- Modify: `src/test/java/nusynapxe/architecture/ArchitectureTest.java`
- Test: `src/test/java/nusynapxe/architecture/ArchitectureTest.java` and the generated JaCoCo/quality reports

**Interfaces:**

- `check` depends on `spotlessCheck`, `checkstyleMain`, `checkstyleTest`, `pmdMain`, `pmdTest`, `spotbugsMain`, enabled `spotbugsTest`, `test`, `jacocoTestReport`, `jacocoTestCoverageVerification`, and `javadoc` as supported by the existing Gradle configuration.
- JaCoCo verification starts at measured safe floors of 85% instruction coverage, 65% branch coverage, and 85% line coverage; the implementation must record the baseline report before setting or raising any floor.
- Architecture tests reject UI-to-persistence dependencies, database calls in table cell-factory code, `ReceptionistView.java` above 500 physical lines, and `DoctorView.java` above 450 physical lines.

- [ ] **Step 1: Write failing architecture and coverage-gate assertions.**

  Add ArchUnit rules for `nusynapxe.ui..` not depending on `nusynapxe.persistence..`, and a source scan that rejects `getAdministrative`, `listDoctors`, `receiptHistory`, or repository calls inside methods named `appointmentTable`, `textColumn`, or JavaFX cell-factory overrides. Add a test that reports the line counts of `ReceptionistView.java` and `DoctorView.java`.

  ```java
  @ArchTest
  static final ArchRule uiDoesNotDependOnPersistence =
      noClasses().that().resideInAnyPackage("nusynapxe.ui..")
          .should().dependOnClassesThat().resideInAnyPackage("nusynapxe.persistence..");
  ```

- [ ] **Step 2: Run the quality tests and record current report values.**

  Run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat test jacocoTestReport pmdMain pmdTest checkstyleMain checkstyleTest spotbugsMain --offline --no-daemon --console=plain
  ```

  Expected: the new architectural assertions identify any remaining forbidden dependency or cell-factory call, and the report provides the measured baseline used for the verification floors.

- [ ] **Step 3: Add only targeted build rules.**

  Enable `spotbugsTest`, add the JaCoCo verification task with the measured floors, wire it into `check`, and add maintainability rules that the refactored code satisfies. Keep false-positive exclusions narrow and comment each one with the exact class and reason. Do not add a blanket `ignoreFailures` setting.

- [ ] **Step 4: Run the complete verification gate and repair only in-scope violations.**

  Run:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat spotlessApply check jacocoTestReport javadoc --offline --no-daemon --console=plain
  git diff --check
  ```

  Expected: PASS with test SpotBugs enabled, coverage verification enforced, architecture rules green, and no whitespace errors. If a rule reports an existing in-scope defect, fix the source and add its focused test before rerunning the gate.

- [ ] **Step 5: Commit the quality-gate change.**

  ```powershell
  git add build.gradle config src/test/java/nusynapxe/architecture
  git commit -m "build: enforce maintainability quality gates"
  ```

## Final verification, GitHub issues, and pull request

- [ ] **Step 1: Review every commit and verify clean separation.**

  Run `git log --oneline master..HEAD`, `git diff --check master..HEAD`, and `git status --short`. Confirm that each behavioral issue has exactly one implementation commit and that the design/plan documentation is not mixed into those commits.

- [ ] **Step 2: Run the final full gate.**

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
  .\gradlew.bat spotlessCheck check jacocoTestReport javadoc --offline --no-daemon --console=plain
  ```

  Expected: PASS; capture the test count, coverage values, and any platform-specific limitations for the pull request description.

- [ ] **Step 3: Create one GitHub issue per completed refactor.**

  Use `gh issue create` with these titles and body requirements, recording each returned issue number:

  ```text
  refactor: centralize clinic clock
  refactor: add appointment list projection
  perf: batch clinical history prescriptions
  perf: query revenue by range
  refactor: return checkout receipt directly
  refactor: move clinic database work off the fx thread
  refactor: split large clinic views
  build: enforce maintainability quality gates
  ```

  Each body must state the observed problem, the commit that fixes it, the focused tests, and the final verification result. Do not create an issue for the design or plan documents.

- [ ] **Step 4: Push the completed branch after verification.**

  ```powershell
  git push --set-upstream origin refactor/improve-code-quality
  ```

- [ ] **Step 5: Create the pull request linking all eight issues.**

  Use `gh pr create` with a concise summary, test/quality-gate evidence, the eight `Closes #<issue>` references, and a note that the commits are intentionally separated for review. Use `gh pr view --web` only if a browser handoff is useful; do not merge the pull request.
