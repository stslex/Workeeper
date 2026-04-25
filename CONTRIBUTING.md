# Contributing to Workeeper

Thanks for your interest in contributing. This document covers what you need to know to land a
change: the development setup, the architecture and lint rules a change must respect, the test
expectations, the commit and PR conventions, and the release workflow.

## Before you start

- Read the [Code of Conduct](CODE_OF_CONDUCT.md). Participation in this project is governed by it.
- Skim [documentation/architecture.md](documentation/architecture.md) so you know the MVI
  contract and module boundaries you'll be working with.
- If you're touching a specific feature, check
  [documentation/features.md](documentation/features.md) for the State / Action / Event surface
  it already exposes.

## Local setup

Tools required:

- Android Studio (latest stable).
- JDK 21 (set both for the IDE and the Gradle JVM).
- Android SDK with Build Tools matching the pinned `compileSdk` in
  `gradle/libs.versions.toml`.

Per-variant Firebase configs at `app/dev/google-services.json` and
`app/store/google-services.json` and a `keystore.properties` at the repo root are required for
release builds. None of these are committed; obtain them from the project owner if you need to
build a signed artifact. Debug builds of `:app:dev` do not need a real keystore for most
day-to-day work.

A debug install on a connected device:

```bash
./gradlew :app:dev:installDebug
```

## Branches and where to open PRs

- `master` is the long-lived release branch. Pushes to it retrigger the unified build and are
  the source for release tagging.
- `dev` is the integration branch for ongoing work. Open PRs against `dev` unless you are
  cherry-picking a hotfix straight to `master`.

## Required checks before pushing

Run these locally before you push. They are also enforced in CI by
`.github/workflows/android_build_unified.yml`:

```bash
./gradlew detekt
./gradlew lintDebug
./gradlew testDebugUnitTest
```

If detekt produces formatting findings, run `./gradlew detekt --auto-correct` to fix them
in-place. The pre-commit hook at `.githooks/pre-commit` is currently disabled (returns early
without running checks) — see
[documentation/lint-rules.md](documentation/lint-rules.md#pre-commit-hook). Until that
changes, treat the local Gradle commands as the gate.

UI tests are not required for every PR. Run them locally when you touch Compose code:

```bash
# Smoke tests only (fast)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  --continue
```

See [documentation/testing.md](documentation/testing.md) for the full testing strategy.

## Code style

- Kotlin official style. 4-space indentation, no tabs.
- Naming: UpperCamelCase classes/objects, lowerCamelCase functions and properties,
  UPPER_SNAKE_CASE constants. Packages are lowercase, dot-separated, and respect the
  `core/*` / `feature/*` / `app/*` layout.
- Android resources are snake_case (e.g. `ic_bottom_app_bar_chart_icon_24`,
  `bottom_bar_label_charts`).
- Compose previews live in the same file as the composable they preview, suffixed `Preview`.

The custom Detekt rules in `lint-rules/` enforce most of the MVI-specific naming and
structural rules — see
[documentation/lint-rules.md](documentation/lint-rules.md#custom-detekt-mvi-rules) for the
full list and good/bad examples.

## Architecture rules to respect

When adding to a feature module:

- The Store contract (`State`, `Action`, `Event`) must conform to the MVI Detekt rules:
  immutable data-class State, sealed Action / Event, and the Store interface.
- New handlers belong in `feature/<name>/.../mvi/handler/<Category>Handler.kt`. Use
  constructor injection with `@Inject` and annotate the class `@ViewModelScoped`.
- Repositories, DataStores, the database, and `StoreDispatchers` are `@Singleton`. Handlers,
  Stores, Interactors, and Mappers are `@ViewModelScoped`.
- New routes live in `core/ui/navigation/Screen.kt`. Each route maps to a `Component<Screen>`
  in the consuming feature.
- Surface user feedback through Events: emit `Event.Snackbar*` or `Event.Haptic*` and let the
  Compose layer translate them into `SnackbarManager.showSnackbar(...)` or
  `LocalHapticFeedback.current.performHapticFeedback(...)` calls.

If the feature does not already expose what you need, model the new behavior into the existing
`Action` and `Event` surface rather than reaching around it.

## Commits

Workeeper uses **Conventional Commits**. The canonical specification — including the type list
GitHub Copilot uses to draft commit messages — lives at
[.github/git-commit-instructions.md](.github/git-commit-instructions.md). Read it once and
follow it.

Quick reference (full rules and examples in the canonical file):

```
<type>(<optional scope>): <short description>

<optional body>
```

Common types: `feat`, `fix`, `perf`, `refactor`, `security`, `deps`, `docs`, `build`, `ci`,
`chore`, `style`, `test`. Multi-prefix subject lines (one type per line) plus a `Summary:`
section in the body are accepted for changes that span concerns.

Examples:

- `feat: add label filtering to all-exercises`
- `fix(charts): handle empty date range`
- `refactor: extract sets-dialog state into a value class`

## Pull requests

- Keep PRs focused. One PR per concern is easier to review than a sweeping change.
- Write a short description that says **what** changed and **why**. Reference the issue if one
  exists.
- Attach screenshots or recordings for UI-visible changes.
- Note any breaking changes (new minimum Android version, schema migration, public-API changes
  in `core/`).
- Confirm `detekt`, `lintDebug`, and `testDebugUnitTest` pass locally. If a UI-affecting change
  needs UI tests, run the smoke suite locally before requesting review.

The unified GitHub Actions workflow runs detekt, Android Lint, the build, and unit tests on
every PR; UI tests are opt-in via `ui_tests.yml`. See
[documentation/ci-cd.md](documentation/ci-cd.md) for full pipeline details.

## Schema and data migrations

Room is set to `exportSchema = true` and writes to
`core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/`. When you change
an entity:

1. Bump the `version` in `AppDatabase`.
2. Add a new `Migration<n>_<n+1>` under `core/database/.../migrations/` and register it in
   `core/database/.../di/CoreDatabaseModule.kt`.
3. Commit the freshly generated `<version>.json` schema file.

## Releases

Contributors do not cut releases — the maintainer triggers the deploy workflows manually. For
context, the release pipeline is documented in
[documentation/ci-cd.md](documentation/ci-cd.md#release-pipeline). Tags follow `beta-v<version>`
and `release-v<version>`; the latter triggers the GitHub APK release automatically.

## Reporting issues

- **Bugs:** [`.github/ISSUE_TEMPLATE/bug_report.md`](.github/ISSUE_TEMPLATE/bug_report.md).
- **Feature requests:** [`.github/ISSUE_TEMPLATE/feature_request.md`](.github/ISSUE_TEMPLATE/feature_request.md).

If you need to flag a security concern, please contact the maintainer privately rather than
filing a public issue.
