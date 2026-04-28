# Agents project context

Project context for OpenAI Codex / Cursor agents (and any other tool that follows the
`AGENTS.md` convention) working in this repository. Treat the documents under
[documentation/](documentation/) as the source of truth; do not duplicate their content here.

## Common Gradle commands

```bash
# Build
./gradlew :app:dev:installDebug
./gradlew :app:store:assembleRelease

# Tests
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest

# Static analysis
./gradlew detekt
./gradlew detekt --auto-correct
./gradlew lintDebug

# Pre-commit hook (currently disabled at the script level — see lint-rules.md)
./setup-hooks.sh
```

## Canonical project knowledge

- [documentation/architecture.md](documentation/architecture.md) — modules, MVI, DI, data flow.
- [documentation/features.md](documentation/features.md) — what each feature does.
- [documentation/testing.md](documentation/testing.md) — unit + UI test strategy.
- [documentation/ci-cd.md](documentation/ci-cd.md) — workflows, release pipeline.
- [documentation/lint-rules.md](documentation/lint-rules.md) — Detekt MVI rules + Android Lint.
- [CONTRIBUTING.md](CONTRIBUTING.md) — contributor workflow, commit format.

## Workflow recipes (`.claude/skills/`)

These are Claude Code-shaped skill files. Other agents can read them as procedural recipes for
the same tasks:

- [`add-feature`](.claude/skills/add-feature.md) — scaffold a new `feature/<name>` module.
- [`write-handler-test`](.claude/skills/write-handler-test.md) — JUnit 5 unit test for an MVI
  handler or `*StoreImpl`.
- [`write-ui-test`](.claude/skills/write-ui-test.md) — `@Smoke` Compose UI test with
  `BaseComposeTest`.
- [`add-database-migration`](.claude/skills/add-database-migration.md) — Room schema migration
  + test.
- [`refactor-with-mvi-rules`](.claude/skills/refactor-with-mvi-rules.md) — resolve a custom
  Detekt rule violation.

## Required skill usage

For any task that matches a workflow in `.claude/skills/`, agents must read and follow the
relevant skill file before making changes.

Use this mapping:

- New feature or `feature/<name>` module work: `.claude/skills/add-feature.md`
- JUnit 5 tests for MVI handlers or `*StoreImpl`: `.claude/skills/write-handler-test.md`
- Compose UI smoke tests: `.claude/skills/write-ui-test.md`
- Room schema migrations and migration tests: `.claude/skills/add-database-migration.md`
- Refactors driven by custom MVI/Detekt rules: `.claude/skills/refactor-with-mvi-rules.md`

If multiple skills apply, use the most specific one first, then combine the others as needed.
If no listed skill applies, continue with the normal repository instructions.

## Current focus

- `master` is the release branch; ongoing work targets `dev`.
- UI tests (`ui_tests.yml`) are `workflow_dispatch`-only and do not gate PRs.
- The pre-commit hook in `.githooks/pre-commit` returns early — CI is the lint gate.
- Privacy policy at `docs/index.md` and `docs/_config.yml` are locked by Play Console; do not
  modify them.
- The custom Detekt rules in `lint-rules/` enforce naming and structural rules around the MVI
  contract — read them before introducing new naming patterns.
