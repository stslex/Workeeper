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
- [documentation/performance.md](documentation/performance.md) — Firebase Performance pipelines (TTID, Screen rendering, AppCreate / ActivityCreate).
- [CONTRIBUTING.md](CONTRIBUTING.md) — contributor workflow, commit format.

### Navigation lifecycle (post PR #143)

Navigation is a **lifecycle-safe command bus**. Decisions live in Store/Handler layer
(depend on `Navigator`); execution lives in the App/UI bridge under composition.

- `Navigator` is implemented by the `@Singleton NavigatorEventBus`
  (`app/app/.../navigation/NavigatorEventBus.kt`). It stores only a
  `SharedFlow<NavigationCommand>` — no `NavController`.
- `App.kt` owns `rememberNavController()`. `NavigatorExt.NavigationEventBusSetup`
  collects commands on the current `NavController` via `LaunchedEffect(navController)`
  and is the ONLY place AndroidX Navigation operations execute.
- Feature `NavigationHandler`s are `@ViewModelScoped @Inject Navigator`. Route
  arguments enter the Store via Dagger assisted injection
  (`@Assisted screen: Screen.<X>`); there is no `Component<Screen>` subclass any more.
- `NavHostController`, `NavController`, `NavBackStackEntry`, `SavedStateHandle`,
  `Activity`, and `Context` MUST NOT be retained by any ViewModel / Store / Handler /
  Interactor / Mapper / Hilt singleton.
- `SavedStateHandle` is composable-graph scoped only — use
  `navComponentScreenWithState(<Feature>) { stateHandle, processor -> ... }` and
  reset consumed flags via `stateHandle.setAttrDefaultValue(<SaveHandlerAttr>)`.

Full reference:
[documentation/architecture.md → Navigation](documentation/architecture.md#navigation),
[.claude/skills/refactor-with-mvi-rules.md → Lifecycle-safe navigation refactor](.claude/skills/refactor-with-mvi-rules.md),
and the `add-feature` skill for the new Feature / FeatureAssisted scaffolding.

### Domain layer: interactors and use cases

- Each feature has one Interactor injected into the Store. The Interactor
  is the only domain-layer dependency the store sees.
- Interactor methods that are pure repository pass-through stay in the
  Interactor implementation, calling the repository directly. A method
  qualifies as pass-through if it is a single repository call optionally
  wrapped in `withContext`/`flowOn`, with no business branching.
- Methods with non-trivial business logic — multiple repository calls,
  conditional branching, synthesized sealed return types, multi-step
  orchestration — extract into a single-method use case in
  `feature/<name>/domain/usecase/`.
- A use case is a class with one `suspend operator fun invoke(...)` (or
  non-suspend `fun invoke` for `Flow`-returning use cases). It injects only
  the repositories and helpers it actually uses, plus its own
  `@DefaultDispatcher` if it does suspend work.
- The Interactor delegates thick methods to use cases with a single line.
  Threading (`withContext`) lives in the use case.
- Reference implementation: `feature/exercise/.../domain/`. See
  `ArchiveExerciseUseCase`, `ResolveTrackNowConflictUseCase`,
  `StartTrackNowSessionUseCase`, `DeleteSessionUseCase`.
- Public surface of interactors and use cases uses `*Domain` types,
  never `core.data.*` types. Mapping data → domain happens in
  `feature/<X>/domain/mapper/`. Mapping domain → ui happens in
  `feature/<X>/mvi/mapper/`.
- Two Detekt rules guard this boundary: `DomainLayerPurityRule` and
  `DomainLayerNoUiRule`.
- Display strings and resource fallbacks live in UI mappers via
  `stringResource(R.string.*)` or `resourceWrapper.getString(...)`.
  The domain layer never injects `ResourceWrapper` and never imports
  `R.*`.

### Read-path pattern: batch DAO + Kotlin-side groupBy

For one-shot reads that hit the same table N times, prefer a single batched DAO
method over a per-entity loop. Convention: empty input short-circuits before the
DAO call, the DAO returns a flat `List<*Row>` projection, and the repository
maps it via `groupBy` / `associate` to a `Map<keyAsString, value>`. Null vs
empty-list distinction is load-bearing — preserve nulls in the result so
downstream consumers can decide whether to apply a fallback. Canonical
implementations: `SetDao.getByPerformedExercises`,
`TrainingExerciseDao.getPlanSetsBatch`, `ExerciseDao.getAdhocPlansBatch`. The
canonical consumer is `LiveWorkoutInteractor.loadSession`. See
[CLAUDE.md → Read-path pattern](CLAUDE.md) for the full convention.

## Workflow recipes (`.claude/skills/`)

These are Claude Code-shaped skill files. Other agents can read them as procedural recipes for
the same tasks:

- [`add-feature`](.claude/skills/add-feature.md) — scaffold a new `feature/<name>` module.
- [`write-handler-test`](.claude/skills/write-handler-test.md) — JUnit 5 unit test for an MVI
  handler or `*StoreImpl`.
- [`write-repository-test`](.claude/skills/write-repository-test.md) — real-DB JUnit 5 unit
  test for a `*RepositoryImpl` using the in-memory `RepositoryTestEnv` test fixture.
- [`write-ui-test`](.claude/skills/write-ui-test.md) — `@Smoke` Compose UI test with
  `BaseComposeTest`.
- [`add-database-migration`](.claude/skills/add-database-migration.md) — Room schema migration
  + test.
- [`refactor-with-mvi-rules`](.claude/skills/refactor-with-mvi-rules.md) — resolve a custom
  Detekt rule violation (see also
  [`compose-state-discipline`](.claude/skills/compose-state-discipline.md), which covers
  Rule 4: dialogs and bottom sheets live in `State`, not `Event`).
- [`mvi-dialog-state`](.claude/skills/mvi-dialog-state.md) — model two-or-more modals on one
  screen as a single sealed `dialogState: DialogState` on State (drill-down of Rule 4).

## Required skill usage

For any task that matches a workflow in `.claude/skills/`, agents must read and follow the
relevant skill file before making changes.

Use this mapping:

- New feature or `feature/<name>` module work: `.claude/skills/add-feature.md`
- JUnit 5 tests for MVI handlers or `*StoreImpl`: `.claude/skills/write-handler-test.md`
- Repository / data-layer unit tests against a real in-memory Room database:
  `.claude/skills/write-repository-test.md`
- Compose UI smoke tests: `.claude/skills/write-ui-test.md`
- Room schema migrations and migration tests: `.claude/skills/add-database-migration.md`
- Refactors driven by custom MVI/Detekt rules: `.claude/skills/refactor-with-mvi-rules.md`
- Adding a second dialog/bottom sheet to a screen, or any screen with two-or-more
  modals: `.claude/skills/mvi-dialog-state.md`
- Firebase Performance / TTID / cold-start / screen-rendering trace work, and any change
  that touches `core/ui/mvi/.../performance/` or the `Modifier.reportScreenPlace<>` wiring
  in `AppNavigationHost`: read [documentation/performance.md](documentation/performance.md)
  before changes.
- Drive backup / restore / auto-backup scheduling / Drive authentication work, and any
  change that touches `core/data/backup/*` or the backup section of `feature/settings`:
  read [documentation/feature-specs/backup.md](documentation/feature-specs/backup.md)
  first, then [`.claude/skills/mvi-dialog-state.md`](.claude/skills/mvi-dialog-state.md)
  for state-shape work on the new `FrequencyPicker` variant.
- Cross-feature dialog work — anything that touches `feature/app-dialogs/*`,
  `AppDialogStore`, `AppDialogHost`, `AppDialog` catalog, the `pending_*` DataStore
  keys, or the `AppConfirmationDialog` generic Composable in `core/ui/kit`:
  read [documentation/feature-specs/app-dialogs.md](documentation/feature-specs/app-dialogs.md)
  first, then [`.claude/skills/app-dialogs-pattern.md`](.claude/skills/app-dialogs-pattern.md)
  for the seven-step recipe when adding a new `AppDialog` variant.
- Backup recovery work — anything that touches the restore-time migration
  rollback, the user-initiated undo of last restore, the `RecoveryActivity`,
  the `MIGRATIONS` introspection helper (`hasMigrationPath`), the pre-restore
  compatibility checks, or the removal of `fallbackToDestructiveMigration*`
  from Room: read
  [documentation/feature-specs/backup-recovery.md](documentation/feature-specs/backup-recovery.md)
  first, then [documentation/feature-specs/backup.md](documentation/feature-specs/backup.md)
  for the v1 flow it extends and
  [documentation/feature-specs/app-dialogs.md](documentation/feature-specs/app-dialogs.md)
  for the cross-feature dialog catalog the recovery flows publish into.

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
