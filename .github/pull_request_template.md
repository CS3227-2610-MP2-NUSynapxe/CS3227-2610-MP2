## Summary

<!-- Explain what this PR changes and why. Keep the scope focused. -->

## Related issue

<!-- Use a closing keyword so GitHub links and closes the issue when this PR merges, for example: Closes #123. -->

Closes #

## Changes

<!-- List the main implementation, test, documentation, and configuration changes. -->

-

## Verification

<!-- Record commands actually run and their outcomes. Use "Not run" with a reason where appropriate. -->

| Check | Result or evidence |
| --- | --- |
| Targeted automated tests | |
| `./gradlew check javadoc --no-daemon --console=plain` | |
| `npm ci` and `npm run build` from `website/` | Not applicable unless documentation or website dependencies changed. |
| Manual verification | |

## User-facing changes

<!-- Describe affected workflows. Add before/after screenshots for visible UI changes, using sanitized data. Write "None" if not applicable. -->

## Security, privacy, and data impact

<!-- Cover authorization, clinical-data confidentiality, credentials, schema changes, migrations, and compatibility. Write "None" with a brief explanation if not applicable. -->

## Documentation and OpenSpec

<!-- Link the OpenSpec change/spec and documentation updates, or explain why they are not required. -->

## Risks and limitations

<!-- Identify known risks, unverified behaviour, follow-up work, or deployment considerations. Write "None known" only when justified. -->

## Author checklist

- [ ] The PR has a focused scope and is linked to an issue.
- [ ] Tests cover the changed behaviour, or the absence of new tests is explained.
- [ ] Relevant quality checks were run and their results are recorded above.
- [ ] Authorization, privacy, and stored-data effects were reviewed.
- [ ] No patient data, credentials, database files, or other sensitive information is included.
- [ ] User and developer documentation is updated, or no update is required.
- [ ] OpenSpec artifacts are updated for behavioural or architectural changes, or no OpenSpec change is required.
- [ ] The branch contains no unrelated changes or generated build outputs.
