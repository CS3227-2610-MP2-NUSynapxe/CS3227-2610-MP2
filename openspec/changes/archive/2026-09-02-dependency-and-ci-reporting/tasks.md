## 1. Dependabot configuration

- [x] 1.1 Add `.github/dependabot.yml` with independent weekly `gradle` updates at `/`, `npm` updates at `/website`, and `github-actions` updates at `/`; verify the configuration covers the root build and wrapper, `website/package.json` and lockfile, and every workflow action reference.
- [x] 1.2 Review the first generated dependency-update proposals and verify each proposal passes the existing Java and documentation CI checks before merging; verify no automatic merge behavior is enabled.

## 2. Test-source PMD quality gate

- [x] 2.1 Add `config/pmd/test-ruleset.xml` with meaningful error-prone and selected best-practice checks, then document only evidence-backed JUnit, fixture, and TestFX exceptions; verify the XML ruleset is accepted by PMD and does not modify the production ruleset.
- [x] 2.2 Update the Gradle PMD configuration so production and test tasks use separate rulesets, enable test analysis, retain XML and HTML reports, and keep test violations blocking; verify `pmdMain` and `pmdTest` both execute and `build/reports/pmd/test.xml` is generated.
- [x] 2.3 Resolve or narrowly suppress any test-source findings identified by the new ruleset, preserving the relaxed policy only for framework-specific patterns; verify `pmdTest` and the complete `check` task pass without disabling PMD failure handling.

## 3. CI report artifacts and combined PR comment

- [x] 3.1 Update the read-only verification job to upload available JUnit XML and JaCoCo XML inputs with failure-safe conditions, while retaining the existing quality reports and failed workflow status; verify artifacts are uploaded after both successful and intentionally failing verification runs where files exist.
- [x] 3.2 Add a reporting job that downloads only the report artifact, is limited to same-repository non-Dependabot pull requests, and has only the permissions required for checks and pull-request comments; verify push, manual, fork, and Dependabot events do not enter the comment path.
- [x] 3.3 Configure `madrapps/jacoco-report@v1.8.0` in summary-only mode for `build/reports/jacoco/test/jacocoTestReport.xml`, then pass its coverage outputs into `mikepenz/action-junit-report@v6` with detailed summaries and sticky comment updates; verify the workflow defines one combined comment path rather than separate coverage and test comments.
- [x] 3.4 Make report steps handle missing or partial report files without converting unavailable coverage into a passing result or masking the verification failure; verify JUnit totals, coverage values, and unavailable-report states are represented accurately in generated workflow output.
- [x] 3.5 Validate the reporting behavior on a same-repository pull request by checking that the combined comment is created and updated on a rerun; verify fork and Dependabot paths skip comment writes while retaining any available artifacts.

## 4. Documentation and integration validation

- [x] 4.1 Update the Developer Guide and high-level README quality-gate text with the test PMD command and policy, report paths, weekly dependency sources, combined-comment behavior, and fork/Dependabot limitation; verify links and statements match the committed configuration.
- [x] 4.2 Run the complete Java quality gate, documentation build, and report-generation checks; verify tests, Checkstyle, production and test PMD, SpotBugs, JaCoCo, Javadoc, Spotless, and Docusaurus validation pass and generated reports exist at the documented paths.
- [x] 4.3 Run `openspec validate --strict` and inspect the final diff for whitespace and unintended runtime or persisted-data changes; verify all change requirements have corresponding implementation evidence.
