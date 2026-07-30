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

## Gate discipline (a green gate must be an EXECUTED gate)

When a change must be proven green, `--rerun-tasks` alone is **not sufficient**. Gradle has two
independent output-reuse mechanisms and this rule must defeat both:

- **`UP-TO-DATE`** — the task's inputs are unchanged since its last run in *this* build dir.
  `--rerun-tasks` defeats this.
- **`FROM-CACHE`** — the task's output is loaded from the **build cache** (keyed by input hashes),
  *without executing the task*, even after `./gradlew clean`. `--rerun-tasks` does **not** defeat this;
  only `--no-build-cache` does.

A run that reports `FROM-CACHE` proves nothing about execution, exactly like `UP-TO-DATE`. This was
observed live: a gate reporting `698 from cache` in ~3 min was replaced by a true `1498 executed` run
taking 35 min — same green, but only the second is evidence.

**The gate command is therefore:**

```bash
./gradlew clean
./gradlew assembleDebug detekt lintDebug testDebugUnitTest --rerun-tasks --no-build-cache --continue   # per-commit
./gradlew assembleDebugAndroidTest --rerun-tasks --no-build-cache --continue                          # phase-exit (repo-wide)
```

**Verify the commit LANDED before you mutate.** Proving a detector fires means editing the tree and
reverting it, and `git checkout -- <file>` reverts to HEAD — taking any *uncommitted* work in that
file with it. "Commit first" is not the rule, because issuing the commit is not the same as landing
it: a `git commit` that the pre-commit hook rejects still returns to the prompt, and with `-q` and an
unchecked exit code it looks exactly like success. The next revert then destroys the very work the
commit existed to protect. **Check `git log -1` — or the exit code — and only then mutate.** Four
rounds of work have been lost to this, the last one *after* the habit was written down.

**`detekt --auto-correct` needs `--no-configuration-cache` or it reports without rewriting.** With the
configuration cache on, the run reports the same findings and changes not one byte, so the fix looks
like it failed to work rather than like it never ran. Same family as the `FROM-CACHE` note above and
§27's: a Gradle cache making a task's *evidence* answer for a task that did not execute. Note also
that `MaxLineLength` is not auto-correctable — those are yours to wrap by hand.

**"Green locally" is not "green". The pre-commit hook runs detekt and skips `lintDebug`; CI gates
both.** So a branch can pass every commit, pass the gate command as it was written above, and fail
the moment its PR opens — on errors that were never once shown to the person who wrote them. It is
not hypothetical: `feature/v3-all-trainings` carried a `DuplicateStrings` pair and three
`UnusedResources` orphaned by its own rebuild, and they surfaced only when a *stacked* branch ran
lint for an unrelated reason. Until the hook runs lint — which is Ilya's call, it is his tooling and
it costs about a minute a commit — **`lintDebug` belongs in the per-commit gate above**, and it is
now in it. Note especially that `UnusedResources` is a *deletion* check: it fires on rebuilds that
drop a call site, which is exactly what every screen in this arc does.

The line this replaces said `lintDebug` was excluded because of a pre-existing `[Registered]` error
on `Dev/StoreMobileApp`, tracked separately, and not to "fix" it here. That error is gone —
`lint-rules/lint-baseline.xml` is empty and a full `lintDebug --rerun-tasks --no-build-cache` is
clean across 1083 tasks — so the exclusion outlived its reason and became the thing keeping lint
unrun. A stale exemption is worse than no exemption: it reads as a decision.

**Quote the Gradle summary line as the gate evidence.** It must read `N actionable tasks: N executed`.
Any `from cache` OR `up-to-date` count in that line **voids** the gate result — re-run before claiming
green.

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
- `mockup_gate.yml` runs `documentation/mockups/shell_gate.py` on every PR except those into
  `master`, plus its `--target f52462c7` known negative, which must go red. Editing
  `documentation/mockups/pass2d.html` **or** `AppColors.kt` can red it; reproduce with
  `python3 documentation/mockups/shell_gate.py --base "$(git merge-base origin/$PR_BASE HEAD)" -v`,
  where `$PR_BASE` is the branch the PR targets — `dev` for most work, but the branch below
  you in a stack, which is what CI uses and is not the same baseline.
  A `:root` token change must be declared with an `Allow-root-change: <names>` commit trailer —
  the workflow reads the declaration out of the commits in the range, never from a flag.
- The pre-commit hook in `.githooks/pre-commit` returns early — CI is the lint gate.
- Privacy policy at `docs/index.md` and `docs/_config.yml` are locked by Play Console; do not
  modify them.
- The custom Detekt rules in `lint-rules/` enforce naming and structural rules around the MVI
  contract — read them before introducing new naming patterns.
