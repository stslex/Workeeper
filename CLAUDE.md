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
./gradlew detekt                  # gate: reports, never writes (autoCorrect is off)
./gradlew detekt --auto-correct   # opt in to the formatter role for this run only
./gradlew lintDebug

# Pre-commit hook — installs .githooks (core.hooksPath). Runs detekt on every
# commit; its early exit skips lintDebug only. See lint-rules.md.
./setup-hooks.sh
```

## Canonical project knowledge

- [documentation/architecture.md](documentation/architecture.md) — modules, MVI, DI, data flow.
- [documentation/features.md](documentation/features.md) — what each feature does.
- [documentation/testing.md](documentation/testing.md) — unit + UI test strategy.
- [documentation/ci-cd.md](documentation/ci-cd.md) — workflows, release pipeline.
- [documentation/lint-rules.md](documentation/lint-rules.md) — Detekt MVI rules + Android Lint.
- [documentation/performance.md](documentation/performance.md) — Firebase Performance pipelines (TTID, Screen rendering, AppCreate / ActivityCreate).
- [documentation/feature-specs/backup.md](documentation/feature-specs/backup.md) — Drive backup + restore + auto-backup scheduling: architecture, auth flow, scheduling, Cloud Console setup, error taxonomy, troubleshooting.
- [documentation/feature-specs/app-dialogs.md](documentation/feature-specs/app-dialogs.md) — Cross-feature process-survival dialog catalog (`AppDialog`, `AppDialogStore`, `AppDialogHost`, DataStore-backed `pending_*` flags). Planned alongside backup-recovery.
- [CONTRIBUTING.md](CONTRIBUTING.md) — contributor workflow, commit format.

## Domain layer

- Each feature owns one Interactor injected into the Store. Interactor methods that
  are pure repository pass-through stay in the Interactor implementation, calling the
  repository directly. Methods with non-trivial business logic (multiple repository
  calls, conditional branching, synthesized sealed return types, multi-step
  orchestration) extract into a single-method use case in
  `feature/<name>/domain/usecase/`.
- Public surface of interactors and use cases uses `*Domain` types, never
  `core.data.*` types. Mapping data → domain happens in
  `feature/<X>/domain/mapper/`. Mapping domain → ui happens in
  `feature/<X>/mvi/mapper/`.
- Two Detekt rules guard this boundary: `DomainLayerPurityRule` and
  `DomainLayerNoUiRule`.
- Display strings and resource fallbacks live in UI mappers via
  `stringResource(R.string.*)` or `resourceWrapper.getString(...)`. The domain layer
  never injects `ResourceWrapper` and never imports `R.*`.

## Available skills

Project-specific skills live under [`.claude/skills/`](.claude/skills/). Invoke the matching
skill when the user asks for one of these tasks:

- [`add-feature`](.claude/skills/add-feature.md) — scaffold a new `feature/<name>` module
  (build script, MVI contract, handlers, DI graph, navigation entry, smoke test stub).
- [`write-handler-test`](.claude/skills/write-handler-test.md) — write a JUnit 5 unit test for
  an MVI handler or `*StoreImpl` using the project's mocked `HandlerStore` + `TestScope`
  pattern.
- [`write-repository-test`](.claude/skills/write-repository-test.md) — write a real-DB JUnit
  5 unit test for a `*RepositoryImpl` using the shared `RepositoryTestEnv` in-memory Room
  fixture from the `core/data/database` testFixtures source set.
- [`write-ui-test`](.claude/skills/write-ui-test.md) — write a `@Smoke` Compose UI test using
  `BaseComposeTest`, `ActionCapture`, `MockDataFactory`, and `PagingTestUtils`.
- [`add-database-migration`](.claude/skills/add-database-migration.md) — bump
  `APP_DATABASE_VERSION`, add a `Migration<N>` object, append it to the `MIGRATIONS` array in
  `MigrationsRegistry.kt`, and add a `MigrationTestHelper`-based test.
- [`refactor-with-mvi-rules`](.claude/skills/refactor-with-mvi-rules.md) — resolve a custom
  Detekt MVI / Metro scope / Composable rule violation by applying the conformant fix
  (see also [`compose-state-discipline`](.claude/skills/compose-state-discipline.md), which
  covers Rule 4: dialogs and bottom sheets live in `State`, not `Event`).
- [`mvi-dialog-state`](.claude/skills/mvi-dialog-state.md) — model two-or-more dialogs /
  bottom sheets on one screen as a single sealed `dialogState: DialogState` on `Store.State`,
  with `Hidden` as the default variant. Drill-down of Rule 4 of `compose-state-discipline`.
- [`app-dialogs-pattern`](.claude/skills/app-dialogs-pattern.md) — add a new variant to
  the cross-feature `AppDialog` catalog (sealed variant + DataStore keys + priority slot
  + render branch + dismiss policy + strings + catalog table). Use for process-survival
  / destination-independent dialogs; for screen-scoped modals use `mvi-dialog-state`.

## Current focus

- `master` is the release branch; ongoing work targets `dev`.
- UI tests (`ui_tests.yml`) are `workflow_dispatch`-only and do not gate PRs.
- The pre-commit hook in `.githooks/pre-commit` **runs `./gradlew detekt` on every commit**
  (`core.hooksPath = .githooks`). Its early `exit 0` sits *after* the detekt block, so it skips
  `lintDebug` only — Android Lint is CI-gated, detekt is gated both locally and in CI.
- detekt runs with `autoCorrect = false`: it reports, it never writes to the tree it verifies.
  Formatting is an explicit per-run opt-in (`./gradlew detekt --auto-correct`).
- Privacy policy at `docs/index.md` and `docs/_config.yml` are locked by Play Console; do not
  modify them.
- Set types live in `core/database/.../exercise/model/SetsEntityType.kt`; check the migration
  folder before changing schema.

## Adhoc exercise lifecycle (v2.3+)

`ExerciseEntity.isAdhoc` distinguishes inline-created (Track Now / Quick start picker)
exercises from regular library entries. Three states drive every list query, every
cancel/finish path, and the cascade-delete predicate.

- **Create.** Inline exercise creation in the picker writes
  `exercise_table` with `is_adhoc = 1`. The new row is **not** visible in any user-facing
  list (`pagedActive`, `getAllActive`, `pagedActiveByTags`, `getRecentlyTrainedExercises`
  all filter `is_adhoc = 0`). The only surface that loads it is the active session's
  `TrainingExerciseEntity` join.
- **Graduate.** On session finish, every exercise **performed in the session** flips to
  `is_adhoc = 0` inside the `finishSessionAtomic` transaction
  (`exerciseDao.graduateAdhocForSession`). After graduate the row is indistinguishable
  from a library entry.
- **Delete (defence-in-depth).** Cancel / empty-finish-Discard for an ad-hoc training
  cascades through `SessionRepository.discardAdhocSession` — session + training +
  inline-created exercise rows in one transaction. The DAO cascade-delete query
  filters by **both** `is_adhoc = 1` **AND** join via `performed_exercise_table` for the
  cancelled session, so library exercises picked into the session (their
  `is_adhoc = 0`) are never deleted.

Both predicates join through `performed_exercise_table`, **not** `training_exercise_table`.
That changed in v3 step 5: a one-off (non-plan-attached) exercise has no plan row by
construction, so a plan-table join stranded every inline-created one-off at `is_adhoc = 1` —
permanently invisible to `pagedActive` — and left it behind on cancel. Session membership is
the honest predicate, and it is a superset of the old one for plan-attached exercises.

Rule: every new exercise list query (paged, observable, search) must filter
`is_adhoc = 0`. The only acceptable exception is when a query needs all rows for a
specific defensive reason — document it inline.

## `plan-attached` is a second, independent axis (v3 §6.2)

`is_adhoc` describes the **exercise** ("created inline"). `plan-attached` describes the
**exercise↔training relation** ("is in this training's saved plan"), and is encoded as the
**presence of a `training_exercise_table` row** — no column, no migration. Never conflate
them: a library exercise with `is_adhoc = 0` added mid-session as a one-off is not ad-hoc by
any definition, yet it is not plan-attached.

Read the flag from **key presence** in `TrainingExerciseRepository.getPlans`, never from plan
nullability: a row with `plan_sets IS NULL` is attached-with-no-plan, which is a third state.
`map[uuid] == null` cannot tell it apart from an absent key — use `containsKey`. See
`LiveExerciseDomain.isPlanAttached`.

## Read-path pattern: batch DAO + Kotlin-side groupBy

For one-shot reads that hit the same table N times (one per entity in a list),
prefer a single batched DAO method over a per-entity loop. Established examples:

- `SetDao.getByPerformedExercises(uuids: List<Uuid>)` returns a flat
  `List<SetEntity>`; the repository wraps it in `groupBy { it.performedExerciseUuid }`.
- `TrainingExerciseDao.getPlanSetsBatch(trainingUuid, exerciseUuids)` returns
  `List<TrainingExercisePlanRow>` (a 2-column projection with the exercise uuid
  preserved); the repository associates by `exerciseUuid.toString()` to a `Map`.
- `ExerciseDao.getAdhocPlansBatch(uuids)` returns `List<ExerciseAdhocPlanRow>`
  (a 2-column projection with the exercise uuid preserved); repository associates
  by `uuid.toString()` to a `Map`.

Conventions:

1. **Empty input short-circuit.** Repository methods that batch must check
   `if (uuids.isEmpty()) return@withContext emptyMap()` before the DAO call —
   tests assert no DAO call is made for empty input.
2. **Null vs empty-list distinction.** When the column being read can be `null`
   (e.g. `plan_sets`, `last_adhoc_sets`), preserve the null in the resulting Map
   rather than filtering it out — downstream consumers (e.g. `loadSession`'s
   read-time fallback) rely on the distinction.
3. **Missing pairs.** Pairs that don't have a row in the DB are silently absent
   from the result — they are NOT a Map entry with null value. The DAO query
   uses `IN (:uuids)` semantics; only existing rows surface.

Use this pattern as the first option for any new "fan out per entity" read path.
The existing `LiveWorkoutInteractor.loadSession` is the canonical consumer:
it parallelises three batch reads (`getPlans` / `getAdhocPlans` /
`getByPerformedExercises`) via `async {}` then assembles a pure-Kotlin
snapshot with no further I/O.
