# Task: introduce domain model layer across all feature modules

## Prerequisite

The use case extraction PR for `ExerciseInteractor` (4 use cases:
`ArchiveExerciseUseCase`, `ResolveTrackNowConflictUseCase`,
`StartTrackNowSessionUseCase`, `DeleteSessionUseCase`) must be merged first.
This task assumes those four classes exist and that
`ExerciseInteractorImpl` already delegates to them for thick methods.

If that PR is still open, do not run this task.

## Goal

The `domain/` package across all features currently leaks `*DataModel` types
in public API, imports `*UiModel` in some places, calls `R.string.*` via
`ResourceWrapper`, and nests sealed result types inside interactor
interfaces. There is effectively no domain model layer — interactor is a
thin pass-through over repositories that return raw data types.

This PR introduces a proper domain model layer for **every feature** in one
pass:

- Each domain-leaking type from `core.data.*` gets a parallel `*Domain`
  type in `feature/<X>/domain/model/`.
- Each interactor / use case method signature changes to accept and return
  `*Domain` types only (no `core.data.*` types in public surface).
- `data → domain` mapping lives in `feature/<X>/domain/mapper/` as
  extension functions on the data type.
- UI mappers update to accept `*Domain` types and produce `*UiModel` —
  data types disappear from `mvi/mapper/`.
- Handlers and stores update to consume `*Domain` types — data types
  disappear from `mvi/handler/` and `mvi/store/`.
- `ResourceWrapper` is removed from all interactors and use cases.
  Display fallback strings (`"Unnamed"`, `"Track Now"`, etc.) move to UI
  mappers via `stringResource(R.string.*)` at call sites.
- Sealed result types nested in interactor interfaces (`ArchiveResult`,
  `TrackNowConflict`, `SaveResult`, etc.) move to standalone files in
  `feature/<X>/domain/model/`.
- Two new Detekt custom rules guard against regressions of this work.
- Architecture documentation, agent docs, and skills get updated to make
  the convention discoverable.

This is a single PR with one commit per feature plus a final commit for
Detekt rules and docs. Atomic, not phased across PRs.

## Convention being established (apply mechanically)

### Naming

- Data types: existing `*DataModel`, `*Entity`, `*Dto` — unchanged.
- **Domain types: `*Domain` suffix.** Examples: `ExerciseDomain`,
  `TagDomain`, `HistoryEntryDomain`, `PersonalRecordDomain`,
  `PlanSetDomain`, `SessionDomain`, `RecentSessionDomain`. The suffix is
  short, parallel to `Data`/`Ui` family, and disambiguates by greppable
  search.
- Sealed result types stay without suffix because the name already
  describes a domain outcome: `ArchiveResult`, `TrackNowConflict`,
  `SaveResult`, `BulkArchiveOutcome` (rename to `BulkArchiveResult`
  for parallel naming — see "Renames" below).
- UI types: existing `*UiModel` — unchanged.

### File layout per feature

```
feature/<name>/src/main/kotlin/io/github/stslex/workeeper/feature/<name>/
├── domain/
│   ├── model/                   ← NEW directory
│   │   ├── <Name>Domain.kt      ← one file per domain model
│   │   ├── ArchiveResult.kt     ← extracted from interactor
│   │   └── ...
│   ├── mapper/                  ← NEW directory
│   │   └── <Name>DomainMapper.kt   ← extension functions
│   ├── usecase/                 ← if feature has use cases
│   │   └── *.kt
│   ├── <Name>Interactor.kt
│   └── <Name>InteractorImpl.kt
├── mvi/
│   ├── handler/                 ← updated, no core.data imports
│   ├── mapper/                  ← updated, accepts domain not data
│   └── store/                   ← updated
└── ui/
    └── ...                      ← unchanged
```

### Mapper convention

```kotlin
// feature/exercise/domain/mapper/ExerciseDomainMapper.kt
package io.github.stslex.workeeper.feature.exercise.domain.mapper

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain

internal fun ExerciseDataModel.toDomain(): ExerciseDomain = ExerciseDomain(
    uuid = uuid,
    name = name,
    type = type.toDomain(),
    // ... 1:1 field copy unless an explicit transformation is documented
)

internal fun List<ExerciseDataModel>.toDomain(): List<ExerciseDomain> =
    map { it.toDomain() }
```

Rules for mappers:

- Always extension functions on the source type, never standalone classes
  or objects.
- Always `internal` visibility (feature-scoped).
- One file per concept group, named `<Concept>DomainMapper.kt`.
- Naming: `toDomain()` for `Data → Domain`, `toData()` for the reverse
  when a write-side mapping is needed (rare, only for repository inputs
  that need a domain-shaped DTO).
- If a mapping needs a non-trivial transformation (e.g. a default fallback
  that previously came from `ResourceWrapper`), the transformation goes in
  the UI mapper, not in the domain mapper. The domain mapper is a pure
  field-shape conversion.

### Domain model class shape

```kotlin
// feature/exercise/domain/model/ExerciseDomain.kt
package io.github.stslex.workeeper.feature.exercise.domain.model

internal data class ExerciseDomain(
    val uuid: String,
    val name: String,
    val type: ExerciseTypeDomain,
    val description: String?,
    val tags: List<String>,
    // ... shape mirrors ExerciseDataModel
)
```

- `internal` visibility. A domain model is feature-scoped; if two features
  share a domain concept, each declares its own (deliberately denormalized).
- `data class` for value-objects, `sealed interface` for ADTs/results.
- No annotations from `androidx.compose.*`. No `@Stable`, no `@Immutable`
  on domain classes (these are UI-layer hints).
- Use Kotlin `kotlinx.collections.immutable` types only when the field
  reaches a Compose state (and even then, prefer `List` in domain and
  convert in UI mapper).
- Fields can be primitives, other `*Domain` types, or
  `kotlin.uuid.Uuid` / `Instant` / standard library types.
- **No `core.data.*` types** in any field type (recursive — if `Foo.bar`
  is type `BarData`, that's a violation).

### Interactor / use case signature rules

After the migration, every public method on every interactor and use case
must satisfy:

- No parameter type contains a `core.data.*` type, recursively.
- No return type contains a `core.data.*` type, recursively.
- Sealed result types are imported from `domain.model`, not nested in the
  interactor.

`ResourceWrapper` is removed from every interactor and use case
constructor across all features. Where a fallback string was applied in
domain (`exercise.name?.takeIf { it.isNotBlank() } ?: resourceWrapper.getString(...)`),
the domain layer now passes the raw nullable/blank value through, and the
UI mapper substitutes the fallback via `stringResource(R.string.*)`.

The only exception: `feature/exercise/.../usecase/StartTrackNowSessionUseCase`
currently uses `ResourceWrapper` to compute a name that is **persisted to
the database** (adhoc training name). After migration: pass the raw
`exercise.name` (even if blank) to `SessionRepository.createAdhocSession`.
Empty/blank training name in DB is acceptable; every UI mapper that
displays a training name must already (or now) handle the blank case via
`stringResource(R.string.*)`. Audit `feature/all-trainings`,
`feature/single-training`, `feature/home`, `feature/live-workout`, and
`feature/past-session` UI mappers — wherever they display a training name,
add the blank-fallback.

### Handler / store rules

- Handlers and stores import only from `domain.model.*`, never from
  `core.data.*`.
- If a handler currently calls `interactor.foo()` and uses the
  `ExerciseDataModel` it returns, after migration the handler uses
  `ExerciseDomain` — the call site doesn't change shape, only types.

## Per-feature inventory and actions

The following per-feature lists derive from the
`/tmp/domain-boundary-audit-codex.md` report. Open the audit report and
verify counts before starting each feature commit.

### `feature/exercise`

V1 leaks (8) — each method signature gets a domain type:

| Method | Old type | New type |
|---|---|---|
| `getExercise()` return | `ExerciseDataModel?` | `ExerciseDomain?` |
| `getRecentHistory()` return | `List<HistoryEntry>` | `List<HistoryEntryDomain>` |
| `observeAvailableTags()` return | `Flow<List<TagDataModel>>` | `Flow<List<TagDomain>>` |
| `observePersonalRecord()` return | `Flow<PersonalRecordDataModel?>` | `Flow<PersonalRecordDomain?>` |
| `observePersonalRecord()` param `type` | `ExerciseTypeDataModel` | `ExerciseTypeDomain` |
| `saveExercise()` param `snapshot` | `ExerciseChangeDataModel` | `ExerciseChangeDomain` |
| `createTag()` return | `TagDataModel` | `TagDomain` |
| `getAdhocPlan()` return | `List<PlanSetDataModel>?` | `List<PlanSetDomain>?` |
| `setAdhocPlan()` param `plan` | `List<PlanSetDataModel>?` | `List<PlanSetDomain>?` |

V2 fixes:

- `ExerciseInteractorImpl` no longer imports `feature.exercise.R`. Remove
  `ResourceWrapper` from constructor. Affected use cases:
  `ResolveTrackNowConflictUseCase` and `StartTrackNowSessionUseCase` —
  remove `ResourceWrapper` injection; pass raw values through to
  `SessionRepository.createAdhocSession`. Update the return type of
  `ResolveTrackNowConflictUseCase` so `TrackNowConflict.NeedsUserChoice`
  carries `trainingName: String?` instead of pre-substituted `sessionLabel`.
  The UI mapper for the conflict modal substitutes the fallback via
  `stringResource(R.string.feature_exercise_track_now_conflict_unnamed)`.

V3 fixes (UI mapper updates):

- `ExerciseUiMapper` removes imports of `HistoryEntry`, `SetSummary`,
  `PersonalRecordDataModel`, `TagDataModel`. Replace with imports from
  `feature.exercise.domain.model`. Mapper signatures change to accept
  `*Domain` types.

V5 fixes:

- Move `ArchiveResult`, `TrackNowConflict`, `SaveResult` from nested in
  `ExerciseInteractor` to standalone files under
  `feature/exercise/domain/model/`. Update all imports across the feature
  (handlers, store, mappers).

V6 fixes:

- `feature/exercise/.../mvi/handler/ClickHandler.kt` removes imports of
  `ExerciseDataModel`, `ExerciseTypeDataModel`, `PersonalRecordDataModel`.
  Replace with `*Domain` equivalents.
- `feature/exercise/.../mvi/handler/CommonHandler.kt` removes import of
  `TagDataModel`. Replace with `TagDomain`.

### `feature/single-training`

V1 leaks (8):

| Method/property | Old | New |
|---|---|---|
| `getTraining()` return | `TrainingDataModel?` | `TrainingDomain?` |
| `getRecentSessions()` return | `List<SessionDataModel>` | `List<SessionDomain>` |
| `observeAvailableTags()` return | `Flow<List<TagDataModel>>` | `Flow<List<TagDomain>>` |
| `saveTraining()` param `snapshot` | `TrainingChangeDataModel` | `TrainingChangeDomain` |
| `createTag()` return | `TagDataModel` | `TagDomain` |
| `observeAnyActiveSession()` return | `Flow<ActiveSessionInfo?>` | `Flow<ActiveSessionDomain?>` |
| `TrainingExerciseDetail.exercise` | `ExerciseDataModel` | `ExerciseDomain` |
| `TrainingExerciseDetail.planSets` | `List<PlanSetDataModel>?` | `List<PlanSetDomain>?` |
| `PickerExercise.exercise` | `ExerciseDataModel` | `ExerciseDomain` |
| `setPlanForExercise()` param `plan` | `List<PlanSetDataModel>?` | `List<PlanSetDomain>?` |
| `resolveStartSessionConflict()` return | `SessionConflictResolver.Resolution` | `StartSessionConflict` |

V5: move `ArchiveResult` out of interactor to `domain/model/`. Move
`PickerExercise` and `TrainingExerciseDetail` (data classes nested in
interactor) out to `domain/model/`.

V3 + V6 fixes per audit:

- `TagUiMapper`: replace `TagDataModel` import with `TagDomain`.
- `ClickHandler`: replace `ExerciseDataModel`, `TrainingDataModel` imports
  with domain equivalents.
- `CommonHandler`: replace `TagDataModel` import with `TagDomain`.
- `SingleTrainingStore`: replace `ExerciseDataModel` import with
  `ExerciseDomain`.

### `feature/live-workout`

V1 leaks (6) — biggest is `loadSession()` which returns a `SessionSnapshot`
that wraps multiple `core.data.*` types. Create `SessionSnapshotDomain` in
`domain/model/` with these fields:

```kotlin
internal data class SessionSnapshotDomain(
    val session: SessionDomain,
    val exercises: List<LiveExerciseDomain>,
    val preSessionPrSnapshot: Map<String, PersonalRecordDomain>,
)

internal data class LiveExerciseDomain(
    val performed: PerformedExerciseDomain,
    val exerciseType: ExerciseTypeDomain,
    val planSets: List<PlanSetDomain>,
    val performedSets: List<SetDomain>,
)
```

Other V1 entries follow the same `Data → Domain` rename pattern.

V3:

- `LiveWorkoutMapper` removes imports of `PlanSetDataModel`,
  `ExerciseTypeDataModel`, `PersonalRecordDataModel`. Receives
  `SessionSnapshotDomain` and produces `LiveSessionStateUiModel`.

V6:

- `ClickHandler` removes `PlanSetDataModel`, `SetTypeDataModel`,
  `ExerciseTypeDataModel`. Replace with domain.
- `ExercisePickerHandler` removes `ExerciseTypeDataModel`. Replace with
  `ExerciseTypeDomain`.

### `feature/past-session`

V1 leaks (2):

- `observeDetailWithPrs()` returns `DetailWithPrs.detail: SessionDetailDataModel`
  → wrap in `SessionDetailDomain`. The `DetailWithPrs` itself stays as a
  domain wrapper, just with `detail: SessionDetailDomain`.
- `updateSet()` param `SetsDataModel` → `SetDomain`.

V3 (worst V3 in the audit — 5 entries):

- `PastSessionUiMapper` removes imports of `ExerciseTypeDataModel`,
  `SetsDataModel`, `SetsDataType`, `PerformedExerciseDetailDataModel`,
  `SessionDetailDataModel`. All five become domain types. Mapper accepts
  domain inputs.

V6:

- `ClickHandler` and `InputHandler` remove `SetsDataModel`,
  `SetsDataType` imports.

### `feature/home`

V1 leaks (4):

- `observeActiveSession()` returns `Flow<SessionRepository.ActiveSessionWithStats?>`
  → `Flow<ActiveSessionWithStatsDomain?>`.
- `observeRecent()` returns `Flow<List<RecentSessionDataModel>>` →
  `Flow<List<RecentSessionDomain>>`.
- `observeRecentTrainings()` returns `Flow<List<TrainingListItem>>` →
  `Flow<List<TrainingListItemDomain>>`.
- `resolveStartConflict()` return `SessionConflictResolver.Resolution` →
  `StartSessionConflict` (the same domain type used in `feature/single-training`
  but redeclared locally per "feature-scoped" rule). Or extract to a shared
  spot if consolidation makes sense — see "Shared domain types" below.

V3:

- `HomeUiMapper` removes imports of `ActiveSessionWithStats`,
  `RecentSessionDataModel`, `TrainingListItem`. Replace with domain.

V6:

- `ClickHandler` removes `SessionRepository.ActiveSessionWithStats`. Replace
  with domain.

### `feature/all-exercises`

V1 (3):

- `observeExercises()` returns `Flow<PagingData<ExerciseDataModel>>` →
  `Flow<PagingData<ExerciseDomain>>`. (Paging mapping needs care — use
  `PagingData.map { it.toDomain() }` in the impl.)
- `observeAvailableTags()` return → `Flow<List<TagDomain>>`.
- `getExercise()` return → `ExerciseDomain?`.

V3:

- `AllExercisesUiMapper` accepts domain types now.

V5:

- Move `ArchiveResult` out of interactor to `domain/model/`.

V6:

- Audit handler imports — codex listed 1 V6.

### `feature/all-trainings`

V1 (3):

- `observeTrainings()` returns `Flow<PagingData<TrainingListItem>>` →
  `Flow<PagingData<TrainingListItemDomain>>`.
- `observeAvailableTags()` return → `Flow<List<TagDomain>>`.
- `archiveTrainings()` return type `BulkArchiveOutcome` (nested in
  TrainingRepository) → rename to `BulkArchiveResult` and place in
  `feature/all-trainings/domain/model/`. Map from
  `TrainingRepository.BulkArchiveOutcome` in the impl.

V3:

- `TrainingListItemMapper` and `TagUiMapper` accept domain types now.

### `feature/exercise-chart`

V1 (1):

- `getRecentlyTrainedExercises()` return → `List<RecentExerciseDomain>`.

V2 (4) — this feature is the **worst** for V2. The interactor imports UI
types directly:

- `ExerciseTypeUiModel` — imported in the interactor interface. This is a
  V2 violation — UI types must not appear in domain. Replace with
  `ExerciseTypeDomain` (already created via the `feature/exercise` work,
  or declared local to chart). The chart's UI mapper now maps
  `ExerciseTypeDomain` → `ExerciseTypeUiModel` itself.
- `ExerciseChartUiMapper.FoldResult` — V4 generic naming AND V2 (mapper
  type imported by domain). Resolution: rename `FoldResult` to
  `ChartFoldDomain` (or `ChartDataDomain` — `Fold` is a fp-style name that
  doesn't carry domain meaning). Move to `feature/exercise-chart/domain/model/`.
  The fold logic stays in the UI mapper, but the **return type** moves to
  domain. The interactor signature uses the domain type; the mapper
  receives a domain `ChartFoldDomain` and produces UI from it.
  Yes, this means the data → fold → domain conversion happens in the
  interactor, and domain → UI in the mapper.
- `ChartMetricUiModel`, `ChartPresetUiModel` — same pattern. These are
  parameters/return types in the interactor today. Replace with
  `ChartMetricDomain`, `ChartPresetDomain`. UI mapper handles
  `ChartMetricDomain` ↔ `ChartMetricUiModel` conversion.

V3 (1) and V6 (1) follow.

### `feature/settings`

V2 (1):

- `feature/settings/.../domain/model/ArchivedItem.kt` has an
  `androidx.compose.runtime.Stable` annotation. Remove the annotation.
  `@Stable` belongs on UI types only. If `ArchivedItem` is consumed by
  Compose state, the UI mapper can wrap it in a separate `ArchivedItemUi`
  type that IS `@Stable`. Inspect the call sites — decide based on whether
  this type already has a UI counterpart.

No V1, V3, V5, V6 in this feature.

### `feature/image-viewer`

No violations per audit. No changes.

## Renames and consolidation

- `BulkArchiveOutcome` (in `core.data.exercise.training.TrainingRepository`)
  → leave the data type as-is, but the domain wrapper is named
  `BulkArchiveResult` for parallel naming with `ArchiveResult`,
  `SaveResult`, etc.
- `SessionConflictResolver.Resolution` → wrap as `StartSessionConflict`
  domain sealed type. `Resolution` is generic per V4 criteria.
- `ExerciseChartUiMapper.FoldResult` → `ChartFoldDomain` (not the previous
  fp-flavored name).

## Shared domain types — DO NOT introduce a shared domain module

Each feature redeclares its own domain types even when two features have
nearly identical concepts (e.g. `TagDomain` exists separately in
`feature/exercise`, `feature/single-training`, `feature/all-exercises`,
`feature/all-trainings`). This is **deliberate**:

- Each feature's `TagDomain` may evolve independently as features mature.
- Cross-feature shared domain modules create coupling that's hard to break
  later.
- The mapping cost is one extension function per feature — trivial.

If duplication becomes painful in the future, extract to a shared module
in a separate refactor with explicit cross-feature contract review. Not in
this PR.

## Detekt rules — added in the final commit

Two new rules, mirroring the patterns in
`lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/`:

### `DomainLayerPurityRule`

Flags any import in a file under `feature/*/.../domain/` that matches
`io.github.stslex.workeeper.core.data.*`. Exception: files inside
`feature/*/.../domain/mapper/` (mappers must import data types — that is
their entire job).

```kotlin
class DomainLayerPurityRule(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Defect,
        description = "Domain layer must not import core.data.* types except in /mapper/.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        // Implementation: check the file path of the containing file.
        // If it matches feature/*/domain/* but not feature/*/domain/mapper/*,
        // and the import path starts with io.github.stslex.workeeper.core.data,
        // report an issue.
    }
}
```

### `DomainLayerNoUiRule`

Flags imports in `feature/*/.../domain/` that match:

- Any name ending in `UiModel`
- `androidx.compose.*`
- `*.R` (the Android R class — match by checking the import ends in `.R`
  or `.R.string`/`.R.drawable`/etc.)
- Any package containing `/ui/` or `/mvi/` segments

No exceptions.

### Wiring

- Register both rules in `MviArchitectureRules.kt`.
- Add documentation entries to `documentation/lint-rules.md` with good /
  bad examples (mirror the format of existing entries).
- Update `lint-rules/detekt.yml` to enable both rules at `error` severity.

### Sequencing

These rules go in the final commit after all 8 features are migrated. If
they were enabled mid-PR they would fail the build and block subsequent
feature commits. The PR's last commit is the only one that adds the rules
and enables them.

## Documentation updates

### `documentation/architecture.md`

Find the section that describes layers (likely titled "Module map",
"Architecture", or similar). Add a subsection titled exactly
`### Domain model layer` with content roughly:

```
- Each feature owns its domain model layer. Public surface of interactors
  and use cases takes and returns `*Domain` types only.
- `*DataModel` types from `core.data.*` are visible only inside
  `feature/<X>/domain/mapper/<Name>DomainMapper.kt`, where they get
  converted to `*Domain` via `toDomain()` extension functions.
- `*UiModel` types are visible only in `feature/<X>/mvi/mapper/`, which
  consumes `*Domain` and produces `*UiModel`.
- Sealed result types live in `feature/<X>/domain/model/`, never nested
  inside the interactor interface.
- The domain layer never imports `androidx.compose.*`, never references
  `R.*`, never injects `ResourceWrapper`. Display fallbacks live in UI
  mappers, not in domain.
- Reference: `feature/exercise/domain/`. See the audit report at
  `documentation/research/domain-boundary-audit-codex.md` for the
  pre-migration baseline.
```

### `documentation/tech-debt.md`

Find the "Domain model boundary" entry that the use-case PR added. Mark it
as resolved (or remove and replace with a note pointing at this PR).

### `AGENTS.md`

Update the existing "Domain layer: interactors and use cases" subsection
(added in the use-case PR) — append:

```
- Public surface of interactors and use cases uses `*Domain` types, never
  `core.data.*` types. Mapping data → domain happens in
  `feature/<X>/domain/mapper/`. Mapping domain → ui happens in
  `feature/<X>/mvi/mapper/`.
- Two Detekt rules guard this boundary: `DomainLayerPurityRule` and
  `DomainLayerNoUiRule`.
- Display strings and resource fallbacks live in UI mappers via
  `stringResource(R.string.*)`. The domain layer never injects
  `ResourceWrapper` and never imports `R.*`.
```

### `CLAUDE.md`

Same content as the AGENTS.md addition. Keep the two files synchronized.

## Skill updates

### `.claude/skills/add-feature.md`

Step 1 currently reads "Decide whether the feature needs a domain layer".
This is now obsolete — every feature has a domain layer (even if thin).
Replace step 1 with:

```
1. Every feature has a `domain/` package containing:
   - `domain/<Name>Interactor.kt` and `<Name>InteractorImpl.kt`
   - `domain/model/` with at least one `*Domain` type per concept the
     feature surfaces
   - `domain/mapper/<Name>DomainMapper.kt` with extension functions
     `toDomain()` for every data type the interactor consumes
   - `domain/usecase/` (only when the interactor has thick methods —
     see the use case extraction convention in AGENTS.md)
   The interactor's public surface uses `*Domain` types only — never
   `core.data.*` types. See `documentation/architecture.md → Domain
   model layer` for the contract.
```

Add a new step (after the existing module-skeleton step) for domain layer
scaffolding:

```
N. Scaffold the domain layer:
   - For each `core.data.*` type the feature reads, create a parallel
     `*Domain` data class in `domain/model/`.
   - Add a `toDomain()` extension function in `domain/mapper/` for each.
   - The interactor signature uses `*Domain` types in returns and parameters.
   - If the feature has UI display fallbacks (e.g. "Unnamed"), these go
     in the UI mapper via `stringResource()`, not in domain.
```

Reference example in the skill: replace any older feature reference with
`feature/exercise/` as the canonical post-migration example.

### `.claude/skills/refactor-with-mvi-rules.md`

Add two new rules to the "Identify the rule" list:

```
- `DomainLayerPurityRule.kt`
- `DomainLayerNoUiRule.kt`
```

Add a fix-pattern subsection at the end:

```
- **`DomainLayerPurityRule`** — replace the `core.data.*` import with the
  feature-local `*Domain` type. If the type doesn't exist yet, create it
  in `feature/<X>/domain/model/` and add a `toDomain()` mapper.
- **`DomainLayerNoUiRule`** — move display string lookups (`stringResource`,
  `R.*`) out of domain into the UI mapper. Move `*UiModel` imports to UI
  mapper inputs/outputs only.
```

## Per-step plan

This PR has many parts. Execute in this order — each step is one commit
unless noted.

### Phase 1 — pilot

1. **`feature/exercise`** — full migration: model files, mappers,
   interactor signatures, use case signatures, UI mapper, handlers, store.
   Resource removal in use cases. Sealed type extraction. This is the
   reference; verify it builds and tests pass before continuing.

### Phase 2 — small features

2. **`feature/settings`** — remove `@Stable` annotation. Inspect call
   sites; if needed, introduce `ArchivedItemUi` as the UI-side `@Stable`
   wrapper.
3. **`feature/all-trainings`** — V1, V3 fixes. `BulkArchiveOutcome` →
   `BulkArchiveResult` rename.
4. **`feature/all-exercises`** — V1, V3, V5 fixes.

### Phase 3 — medium features

5. **`feature/home`** — V1, V3, V6 fixes.
6. **`feature/exercise-chart`** — V1, V2 (UI types in domain — heaviest
   work here), V3, V4 (`FoldResult` → `ChartFoldDomain`), V6 fixes.
7. **`feature/past-session`** — V1, V3 (5 entries — most V3 in the audit),
   V6 fixes.

### Phase 4 — large features

8. **`feature/live-workout`** — V1 (`SessionSnapshot` complex wrapper),
   V3, V6 fixes.
9. **`feature/single-training`** — V1, V3, V5 (`PickerExercise`,
   `TrainingExerciseDetail`, `ArchiveResult` extraction), V6 fixes.

### Phase 5 — guard rails and docs

10. **Detekt rules** — add `DomainLayerPurityRule` and
    `DomainLayerNoUiRule`. Wire in `MviArchitectureRules.kt`. Enable in
    `lint-rules/detekt.yml`.
11. **Documentation** — update `architecture.md`, `tech-debt.md`,
    `AGENTS.md`, `CLAUDE.md`, `.claude/skills/add-feature.md`,
    `.claude/skills/refactor-with-mvi-rules.md`.
12. **Final verification** — full clean build, test, detekt across all
    modules.

## Verification per commit

After each feature commit:

```bash
./gradlew :feature:<name>:assembleDebug
./gradlew :feature:<name>:test
./gradlew :feature:<name>:detekt
```

After phase 5:

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew test
./gradlew detekt
```

Final sanity:

```bash
# No core.data imports outside mapper/ in any feature domain
for f in $(find feature/*/src/main -path "*/domain/*" -name "*.kt" -not -path "*/mapper/*"); do
  if grep -qE 'import\s+io\.github\.stslex\.workeeper\.core\.data\.' "$f"; then
    echo "VIOLATION: $f"
  fi
done
# expected: empty

# No UiModel / Compose / R.* imports in any feature domain
for f in $(find feature/*/src/main -path "*/domain/*" -name "*.kt"); do
  if grep -qE 'import\s+(androidx\.compose\.|.*\.R$|.*\.R\.|.*UiModel\b)' "$f"; then
    echo "VIOLATION: $f"
  fi
done
# expected: empty

# No core.data imports in any feature handler/store/UI mapper
for f in $(find feature/*/src/main -path "*/mvi/*" -name "*.kt"); do
  if grep -qE 'import\s+io\.github\.stslex\.workeeper\.core\.data\.' "$f"; then
    echo "VIOLATION: $f"
  fi
done
# expected: empty

# ResourceWrapper not imported by any interactor or use case
grep -rE 'import.*ResourceWrapper' feature/*/src/main/kotlin/.../domain/ \
  | grep -vE '/mapper/'
# expected: empty
```

## Acceptance criteria

- Every feature has a `domain/model/` directory with at least one `*Domain`
  file (except `feature/image-viewer` which has no domain).
- Every feature has a `domain/mapper/` directory.
- No interactor or use case method signature anywhere in the codebase
  contains a `core.data.*` type in its public surface.
- No file under `feature/*/domain/` (excluding `mapper/`) imports from
  `core.data.*`.
- No file under `feature/*/domain/` imports `androidx.compose.*`, `*UiModel`,
  or `R.*`.
- No interactor or use case constructor injects `ResourceWrapper`.
- Sealed result types (`ArchiveResult`, `TrackNowConflict`, `SaveResult`,
  `BulkArchiveResult`, `StartSessionConflict`) live in `domain/model/`,
  not nested in interactor interfaces.
- `DomainLayerPurityRule` and `DomainLayerNoUiRule` exist, are registered,
  and pass against the migrated codebase at `error` severity.
- `documentation/architecture.md` has the new "Domain model layer"
  subsection.
- `AGENTS.md`, `CLAUDE.md`, `.claude/skills/add-feature.md`,
  `.claude/skills/refactor-with-mvi-rules.md` updated per the
  "Documentation" and "Skill updates" sections above.
- `./gradlew clean assembleDebug test detekt` succeeds.

## Out of scope

- Extracting use cases for thick methods in interactors other than
  `ExerciseInteractor`. That work happens feature-by-feature in separate
  PRs after this one.
- Splitting `core/data/exercise` into `core/data/training` and
  `core/data/session` (Task 1B — separate PR).
- Adding tests for new domain mappers. Existing tests must keep passing
  with mock substitution if needed; new test coverage is a follow-up.
- Renaming any `core.data.*` types.
- Touching `core/*` code at all, except for adding test fixtures if
  strictly required (avoid this if possible).
- Cross-feature shared domain modules.
- Updating CHANGELOG, RELEASE_NOTES, README.

## When in doubt

If a domain mapper would need a non-trivial transformation that does not
fit "1:1 field copy with `toDomain()` on each nested type" — stop and ask.
Common cases:

- A field needs a default fallback that previously came from
  `ResourceWrapper`. **Default answer:** keep the field nullable / blank in
  the domain model; the UI mapper substitutes the fallback. Do NOT inject
  resources into the domain mapper.
- A `core.data.*` type contains a property typed as a primitive enum
  defined under `core.data.*`. **Default answer:** create a parallel
  domain enum and map. Do NOT use the data enum in the domain model.
- Two features need the "same" domain concept (e.g. `TagDomain`).
  **Default answer:** declare it locally in each feature. Do not
  consolidate.

If the existing audit (`/tmp/domain-boundary-audit-codex.md`) listed a
violation that turns out to be a false positive on inspection, skip it
and note in the PR description. Do not silently expand scope to include
violations the audit missed (the audit is your contract; if there's
extra debt, file follow-ups).

If a Detekt rule cannot be implemented cleanly (e.g. the file path
inspection API is harder than expected), implement the rule with a more
conservative trigger (e.g. visit only specific element types) and note
the limitation in the rule's KDoc. Do not skip the rule.
