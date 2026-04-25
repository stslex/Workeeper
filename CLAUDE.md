# Claude Code project context

Project context for Claude Code (`claude.ai/code`) when working in this repository. Treat the
documents under [documentation/](documentation/) as the source of truth; do not duplicate their
content in this file.

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

## Available skills

Project-specific skills live under [`.claude/skills/`](.claude/skills/). Invoke the matching
skill when the user asks for one of these tasks:

- [`add-feature`](.claude/skills/add-feature.md) — scaffold a new `feature/<name>` module
  (build script, MVI contract, handlers, Hilt module, navigation entry, smoke test stub).
- [`write-handler-test`](.claude/skills/write-handler-test.md) — write a JUnit 5 unit test for
  an MVI handler or `*StoreImpl` using the project's mocked `HandlerStore` + `TestScope`
  pattern.
- [`write-ui-test`](.claude/skills/write-ui-test.md) — write a `@Smoke` Compose UI test using
  `BaseComposeTest`, `ActionCapture`, `MockDataFactory`, and `PagingTestUtils`.
- [`add-database-migration`](.claude/skills/add-database-migration.md) — bump the Room schema
  version, add a `MIGRATION_X_Y` object, register it in `CoreDatabaseModule`, and add a
  `MigrationTestHelper`-based test.
- [`refactor-with-mvi-rules`](.claude/skills/refactor-with-mvi-rules.md) — resolve a custom
  Detekt MVI / Hilt scope / Composable rule violation by applying the conformant fix.

## Current focus

- `master` is the release branch; ongoing work targets `dev`.
- UI tests (`ui_tests.yml`) are `workflow_dispatch`-only and do not gate PRs.
- The pre-commit hook in `.githooks/pre-commit` returns early — CI is the lint gate.
- Privacy policy at `docs/index.md` and `docs/_config.yml` are locked by Play Console; do not
  modify them.
- Set types live in `core/database/.../exercise/model/SetsEntityType.kt`; check the migration
  folder before changing schema.
