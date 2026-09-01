# Custom Agents for NuSynapse Clinic

This file defines specialized agents for the NuSynapse clinic reception system project.

## When to Use Each Agent

Pick an agent based on your current task:

- **Default Agent** — General coding, file exploration, quick questions
- **OpenSpec Manager** — Starting features, managing changes, architecture discussions
- **Implementation** — Writing code, resolving test failures, debugging
- **Receptionist Domain** — Receptionist UI features, patient workflows
- **Quality Gate** — Running tests, linting, quality checks, verification
- **Documentation** — Updating guides, specs, OpenSpec artifacts

---

## OpenSpec Manager

**When to pick this:**
- Exploring feature ideas or requirements
- Starting a new OpenSpec change
- Reviewing or continuing change artifacts
- Verifying implementation against specs
- Archiving completed changes

**Tools it prefers:**
- OpenSpec CLI (proposing, creating, verifying, archiving changes)
- Semantic search and file exploration
- Specification and design documents

**Restrictions:**
- Avoids heavy refactoring or code generation
- Does not run full test suites automatically
- Focuses on artifact management and workflow

---

## Implementation

**When to pick this:**
- Writing new code or fixing bugs
- Implementing an OpenSpec task
- Debugging test failures
- Resolving code quality issues
- Small refactoring within a task

**Tools it prefers:**
- Code editing and file operations
- Terminal commands for builds and targeted tests
- Grep and semantic search for code discovery
- Language server tools (rename, list usages, hover)

**Restrictions:**
- Does not start new OpenSpec changes
- Does not replace OpenSpec Manager for design/exploration
- Commits only when explicitly asked

---

## Receptionist Domain

**When to pick this:**
- Building Receptionist UI features
- Patient data capture workflows
- Receptionist-specific validations
- Patient search and details interactions
- Receptionist-only views and controls

**Tools it prefers:**
- JavaFX UI code editing
- UI-specific test frameworks (TestFX)
- Domain validation logic
- Patient and appointment services

**Expertise:**
- Receptionist workflow constraints
- Patient data entry rules (NRIC/FIN, phone, DOB, etc.)
- Tab-based UI organization
- Modal windows and interactions
- Patient activation/deactivation

---

## Quality Gate

**When to pick this:**
- Running the full test suite
- Running Spotless, Checkstyle, PMD, SpotBugs, JaCoCo
- Validating Javadoc
- Strict OpenSpec verification
- Assessing overall code health

**Tools it prefers:**
- Build commands (gradlew, gradle)
- Test runners
- Linting and analysis tools

**Output:**
- Pass/fail status
- Summary of issues
- Next steps for fixes

---

## Documentation

**When to pick this:**
- Updating User Guide or Developer Guide
- Revising OpenSpec artifacts
- Recording completed work in logs
- Updating README or other docs
- Keeping guides and code aligned

**Tools it prefers:**
- Markdown editing
- OpenSpec specification files
- Guide documents
- File search and diff review

---

## Guidelines for All Agents

- **No pushing.** All agents avoid `git push` unless explicitly asked.
- **Separate commits.** Group logically related changes; use descriptive commit messages.
- **Test before documenting.** Quality checks must pass before updating specs.
- **Respect OpenSpec.** Architecture and design decisions come from OpenSpec, not refactored later.
