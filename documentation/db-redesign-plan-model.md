# Database Redesign — v4 to v5 (plan-first model)

This is a follow-up to [db-redesign.md](db-redesign.md). It introduces
plan-first semantics on top of the v4 schema established earlier (v3 redesign + Stage 5.1 archive timestamps).

For the conceptual rationale, see [product.md](product.md) section
"Plan-first model" (added in this stage).

It is the input to a Claude Code prompt at the bottom. The prompt
runs after this spec is approved and merged to dev.

## Why this exists

The v3/v4 schema modeled exercises and trainings as **history-first**:
sets are recorded only as session history, and the user reads "what
to do today" from the most recent finished session via a SQL lookup.

Product feedback during Stage 5.2 surfaced a different mental model:
**plan-first**. The user wants to write down what they intend to do
("3 × 5 × 100 kg"), see it pre-filled when they start the live
workout, and have it auto-update with what they actually performed
so it becomes the suggestion for next time.

This document specifies the schema and data layer changes to support
plan-first.

## Domain semantics

Two new pieces of state:

1. **Per-training plan.** For each (training, exercise) pair, the
   user has a current plan: an ordered list of sets, each with
   weight (nullable for weightless), reps, and set type. The plan
   is stored on the `training_exercise` join row. It is mutable —
   the Edit training UI lets the user set it, and the Live workout
   finish action updates it automatically.

2. **Per-exercise ad-hoc plan.** For ad-hoc tracking from Exercise
   detail (no training context), the plan is stored on the
   `exercise` row itself. Same shape, different storage location.

Plan vs history are **different concepts** stored in **different
tables**:

- Plan = current state ("what to do next") — `training_exercise.plan_sets`
  / `exercise.last_adhoc_sets`. Single row, overwritten on session
  finish.
- History = immutable record ("what was done") — `set_table` rows
  attached to `performed_exercise` rows attached to `session` rows.
  Never overwritten; new rows on every finish.

Both are populated from the same event (session finish), but they
diverge over time: history accumulates, plan stays at the latest
value.

## Plan-update rule on session finish

When a session finishes, the plan is updated based on what was
performed. The rule is **hybrid grow-but-not-shrink**:

- If actual performed set count >= plan set count: plan is replaced
  by the performed sets (1:1).
- If actual performed set count < plan set count: only the first
  N positions are replaced (where N = performed count); positions
  beyond N keep their existing plan values.

Worked examples:

```
Plan was: [100×5, 100×5, 100×5]   (3 sets)

Performed: [100×5, 100×5, 102.5×5]      → New plan: [100×5, 100×5, 102.5×5]
Performed: [100×5, 100×5, 102.5×5, 90×8] → New plan: [100×5, 100×5, 102.5×5, 90×8]   (grew)
Performed: [100×5, 100×5]                → New plan: [100×5, 100×5, 100×5]            (untouched at pos 3)
Performed: []         (skipped exercise) → New plan: [100×5, 100×5, 100×5]            (untouched)
```

Skipped exercise = zero performed sets = plan untouched. The
"hybrid" rule preserves the user's intent when they couldn't
finish the planned volume.

## Schema changes

Two new columns and one new constraint. Migration is destructive
(no production users).

### `training_exercise_table` — add `plan_sets`

```kotlin
@Entity(
    tableName = "training_exercise_table",
    primaryKeys = ["training_uuid", "exercise_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["training_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["exercise_uuid"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["exercise_uuid"]),
        Index(value = ["training_uuid", "position"]),
    ],
)
data class TrainingExerciseEntity(
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "exercise_uuid") val exerciseUuid: Uuid,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "plan_sets") val planSets: String?,  // NEW: nullable JSON array
)
```

`plan_sets` is `nullable TEXT` storing a JSON array of objects with
`weight: Double?`, `reps: Int`, `type: String` (set type enum name).

`null` means "no plan yet — first time".

### `exercise_table` — add `last_adhoc_sets` and unique name constraint

```kotlin
@Entity(
    tableName = "exercise_table",
    indices = [
        Index(value = ["archived", "name"]),
        Index(value = ["archived"]),
        Index(value = ["name"], unique = true),  // NEW
    ],
)
data class ExerciseEntity(
    @PrimaryKey @ColumnInfo(name = "uuid") val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE) val name: String,  // NOCASE collation NEW
    @ColumnInfo(name = "type") val type: ExerciseTypeEntity,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_adhoc_sets") val lastAdhocSets: String?,  // NEW: nullable JSON array
)
```

Two changes:

- `name` gains `collate = ColumnInfo.NOCASE` and a unique index.
  Combined: case-insensitive uniqueness on exercise name. "Bench
  Press" and "bench press" collide.
- `last_adhoc_sets` is the same JSON shape as `training_exercise.plan_sets`,
  but for ad-hoc tracking from Exercise detail.

### Domain model for plan sets

Both columns store the same shape. New domain type lives in
`core/exercise/.../sets/`:

```kotlin
@Serializable
data class PlanSetDataModel(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeDataModel,
)

@Serializable
enum class SetTypeDataModel { WARMUP, WORK, FAILURE, DROP }
```

`PlanSetDataModel` does NOT have a uuid or position field. Position
is implicit (list order), and there's no need for a separate
identifier — plan sets are not entities.

Difference from existing `SetEntity` (which is an entity with uuid /
position / FK to performed_exercise): `PlanSetDataModel` is a value
object stored in JSON. They serve different purposes:

- `SetEntity` = immutable history record, one row per actually
  performed set.
- `PlanSetDataModel` = mutable plan, list of "what to do next"
  values stored as JSON.

### TypeConverter

```kotlin
package io.github.stslex.workeeper.core.database.converters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import io.github.stslex.workeeper.core.exercise.sets.PlanSetDataModel

class PlanSetsConverter {
    @TypeConverter
    fun toJson(value: List<PlanSetDataModel>?): String? =
        value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun fromJson(value: String?): List<PlanSetDataModel>? =
        value?.let { Json.decodeFromString(it) }
}
```

Register in `AppDatabase`:

```kotlin
@TypeConverters(UuidConverter::class, PlanSetsConverter::class)
abstract class AppDatabase : RoomDatabase() { ... }
```

## Migration v4 → v5

Destructive again. Bump `@Database(version = 5)`.

```kotlin
.fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, 4)
```

`Migration12.kt` and any other migration files stay deleted from the
build (already done in earlier redesigns). No new migration class needed.

Re-export `core/database/schemas/.../AppDatabase/5.json`.

## DAO changes

### `TrainingExerciseDao` — extend

Add a method to fetch plan_sets for a specific (training, exercise)
pair:

```kotlin
@Query("""
    SELECT plan_sets FROM training_exercise_table
    WHERE training_uuid = :trainingUuid AND exercise_uuid = :exerciseUuid
""")
suspend fun getPlanSets(
    trainingUuid: Uuid,
    exerciseUuid: Uuid,
): String?  // null if no row OR plan is null
```

Add a method to update plan_sets:

```kotlin
@Query("""
    UPDATE training_exercise_table
    SET plan_sets = :planSets
    WHERE training_uuid = :trainingUuid AND exercise_uuid = :exerciseUuid
""")
suspend fun updatePlanSets(
    trainingUuid: Uuid,
    exerciseUuid: Uuid,
    planSets: String?,
)
```

`Insert` for `TrainingExerciseEntity` (already exists) needs to
accept the new field — Room will pick it up automatically since
the entity has the column.

### `ExerciseDao` — extend

Add update for `last_adhoc_sets`:

```kotlin
@Query("UPDATE exercise_table SET last_adhoc_sets = :lastAdhocSets WHERE uuid = :uuid")
suspend fun updateLastAdhocSets(uuid: Uuid, lastAdhocSets: String?)
```

Also: when inserting a new exercise, the unique constraint on `name`
(case-insensitive) will throw `SQLiteConstraintException` on conflict.
Repository must catch this and translate to a domain error.

### `SetDao` — remove obsolete prev-set query

Remove (or deprecate):

```kotlin
suspend fun getLastFinishedSet(exerciseUuid: Uuid): SetEntity?
```

This query was the SQL prev-set lookup. With plan-first, the prev-set
hint comes from `training_exercise.plan_sets` (or
`exercise.last_adhoc_sets` for ad-hoc), not from the history table.
The history table remains for sessions list / past session detail
only.

If something else still references `getLastFinishedSet` — leave it
deprecated rather than fail compilation. Mark with `@Deprecated`.

## Repository changes

### `TrainingExerciseRepository` (new or extended)

Currently handles join rows. Extend with plan operations:

```kotlin
interface TrainingExerciseRepository {
    // existing CRUD on training_exercise rows

    suspend fun getPlan(trainingUuid: String, exerciseUuid: String): List<PlanSetDataModel>?
    suspend fun setPlan(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    )
}
```

`getPlan` returns parsed list (or null). `setPlan` writes serialized
JSON.

### `ExerciseRepository` — extend

```kotlin
suspend fun getAdhocPlan(exerciseUuid: String): List<PlanSetDataModel>?
suspend fun setAdhocPlan(exerciseUuid: String, planSets: List<PlanSetDataModel>?)

// New error type for unique name conflict:
sealed interface SaveResult {
    data object Success : SaveResult
    data object DuplicateName : SaveResult
}
suspend fun saveExercise(model: ExerciseChangeDataModel): SaveResult
```

`saveExercise` catches `SQLiteConstraintException` from the unique
index and returns `DuplicateName`. Other DB exceptions propagate.

### Plan-update rule helper (in domain)

The hybrid grow-but-not-shrink rule lives in domain layer, not in
DB. New utility:

```kotlin
package io.github.stslex.workeeper.core.exercise.sets

object PlanUpdateRule {
    /** Apply the hybrid grow-but-not-shrink rule. */
    fun update(
        existingPlan: List<PlanSetDataModel>?,
        performed: List<PlanSetDataModel>,
    ): List<PlanSetDataModel>? {
        if (performed.isEmpty()) return existingPlan        // skip case
        val existing = existingPlan ?: emptyList()
        return if (performed.size >= existing.size) {
            performed                                        // grow or replace
        } else {
            performed + existing.drop(performed.size)        // partial — keep tail
        }
    }
}
```

This is called by Live workout's finish handler (Stage 5.4) for each
performed exercise:

```kotlin
val newPlan = PlanUpdateRule.update(currentPlan, performedSets)
trainingExerciseRepo.setPlan(trainingUuid, exerciseUuid, newPlan)
```

Or for ad-hoc:

```kotlin
val newPlan = PlanUpdateRule.update(currentPlan, performedSets)
exerciseRepo.setAdhocPlan(exerciseUuid, newPlan)
```

## What this changes for previously-spec'd features

### Stage 5.1 (Settings + Archive) — no changes

Settings + Archive doesn't touch plan model.

### Stage 5.2 (Exercises) — significant changes

Edit exercise screen no longer has a Sets section in v1 (correct).
**However:**
- Save validation must catch `SaveResult.DuplicateName` and show error
  on the Name field with a localized message.
- Strings to add: `feature_exercise_edit_error_name_duplicate` (EN
  + RU).

Exercise detail screen no longer relies on SQL prev-set lookup for
"recent sets" preview. Instead — its "Recent" history list still
comes from `getRecentSessionsForExercise` (history-based), which is
fine. The plan is consumed in Live workout, not in detail.

### Stage 5.3 (Trainings) — fundamental change

Edit training adds **inline plan editor** for each exercise in the
exercise list. When the user adds an exercise to a training, they
optionally specify the plan (sets). When editing an existing
training_exercise, the plan is shown and editable.

UI sketch (will be elaborated in updated Stage 5.3 spec):

```
[1] [Bench Press]                    [≡]
    [100×5 · 100×5 · 100×5]          [edit plan]
[2] [Overhead Press]                 [≡]
    [no plan yet]                    [add plan]
```

Tapping "edit plan" / "add plan" opens a modal sheet with set
entries (PlanSet rows: weight input, reps input, set type chip
selector) + add/remove buttons.

This is a meaningful additional UI surface to design. Stage 5.3 spec
will be updated after this Stage 4.6 lands.

### Stage 5.4 (Live workout) — depends on this

Live workout pre-fills set entry rows with plan values:

- Read plan for each performed_exercise (via repository).
- Initialize set entry rows with plan values.
- User can edit any value or add/remove rows.
- On finish: write actual values to set_table (history) AND apply
  PlanUpdateRule to recompute plan, then write back to plan storage.

## Test additions

Unit tests:

- `PlanUpdateRuleTest` — exhaustive cases for the hybrid rule.
- `PlanSetsConverterTest` — round-trip JSON.
- `TrainingExerciseDaoTest` — plan getter/setter.
- `ExerciseDaoTest` — adhoc plan getter/setter, unique name
  constraint enforcement.
- `ExerciseRepositoryImplTest` — `SaveResult.DuplicateName` returned
  on conflict.

## Closed and remaining open questions

Closed by this document:

- Plan storage: JSON blob on `training_exercise.plan_sets` and
  `exercise.last_adhoc_sets`.
- Plan-update rule: hybrid grow-but-not-shrink.
- Unique name on exercise: case-insensitive, enforced at DB level
  via UNIQUE INDEX with COLLATE NOCASE.

Remaining open:

- **Plan editor UX in Edit training.** Inline vs modal sheet vs
  expandable row. Decided in updated Stage 5.3 spec.
- **Per-set set type default.** When user adds a new plan set, what
  is the default type? Likely WORK. Decided in spec.
- **Plan validation.** Should plan_sets be empty list vs null?
  Distinction matters: empty list = "I deliberately have no sets
  planned", null = "no plan yet". Default: treat empty list and
  null as the same in UI; store null in DB when user clears.

---

## Claude Code prompt

Run after this spec is approved and merged to dev.

```
Implement Workeeper database redesign v4 → v5 for plan-first model per documentation/db-redesign-plan-model.md.

CONTEXT
This is a follow-up to db-redesign.md (v2 → v3) and Stage 5.1 (which bumped to v4 for archive timestamps). The v4 schema is currently in dev. v5 adds plan-first semantics: plan_sets on training_exercise (per-training plan) and last_adhoc_sets on exercise (ad-hoc plan), plus case-insensitive unique name constraint on exercise.

Read in order:
- documentation/product.md
- documentation/data-needs.md
- documentation/db-redesign.md (the v2 → v3 redesign + v4 archive timestamps from Stage 5.1)
- documentation/db-redesign-plan-model.md (this stage — primary input)
- documentation/architecture.md (especially Localization section if you touch strings)

THIS PROMPT IS A SINGLE PASS. Implementation, verification, draft PR opened. No STOP gate.

PROCESS

1. Bump database version: @Database(version = 5) in AppDatabase.kt.

2. Update CoreDatabaseModule.kt: change fallbackToDestructiveMigrationFrom signature to include 2, 3, and 4 as drop-from versions:
       .fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, 4)

3. Add new domain type PlanSetDataModel (data class) and SetTypeDataModel (enum) per the spec, in core/exercise/.../sets/ package. These are @Serializable. SetTypeDataModel maps to SetTypeEntity in the database layer (already exists).

4. Add TypeConverter PlanSetsConverter in core/database/.../converters/. Register in AppDatabase @TypeConverters annotation alongside UuidConverter.

5. Update entities:
   - TrainingExerciseEntity: add planSets: String? column.
   - ExerciseEntity: add lastAdhocSets: String? column. Add collate = ColumnInfo.NOCASE on name. Add Index(value = ["name"], unique = true) to indices array.

6. Update DAOs per the spec:
   - TrainingExerciseDao: add getPlanSets(trainingUuid, exerciseUuid): String? and updatePlanSets(...).
   - ExerciseDao: add updateLastAdhocSets(uuid, lastAdhocSets).
   - SetDao: deprecate getLastFinishedSet (mark @Deprecated, do not delete yet — anything that still calls it stays compiling).

7. Re-export schema JSON. Verify core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/5.json is created on build.

8. Repository changes:
   - core/exercise/.../training/TrainingExerciseRepository (or wherever the join is currently handled): add getPlan(trainingUuid, exerciseUuid): List<PlanSetDataModel>? and setPlan(...) methods.
   - ExerciseRepository: add getAdhocPlan(uuid) / setAdhocPlan(uuid, planSets). Also add SaveResult sealed interface (Success, DuplicateName) and update saveExercise(...) to return SaveResult, catching SQLiteConstraintException from the unique name index.

9. Add PlanUpdateRule object in core/exercise/.../sets/ with the update(existingPlan, performed) function per the spec. Pure function, no DB access. Heavily unit-tested.

10. Update existing usages:
    - feature/exercise's saveExercise call site needs to handle SaveResult.DuplicateName. Add string feature_exercise_edit_error_name_duplicate to values/strings.xml ("Exercise with this name already exists") and values-ru/strings.xml ("Упражнение с таким именем уже есть"). Show the error on the Name field via existing nameError state mechanism.
    - No UI surface for plan editor yet — Stage 5.3 spec will be updated separately.

11. Tests:
    - PlanUpdateRuleTest: every case from the spec table (grow, shrink, replace, skip = empty performed). Use parameterized JUnit 5 if convenient.
    - PlanSetsConverterTest: round-trip a list with various types and weights including null weight (weightless).
    - TrainingExerciseDaoTest: insert + getPlanSets + updatePlanSets cycle.
    - ExerciseDaoTest: unique name constraint test (case-insensitive — "Bench Press" and "bench press" collide).
    - ExerciseRepositoryImplTest: saveExercise on duplicate name returns SaveResult.DuplicateName.

VERIFICATION

- ./gradlew :core:database:assembleDebug passes.
- ./gradlew :core:exercise:assembleDebug passes.
- ./gradlew assembleDebug passes (whole project, dev + store).
- ./gradlew detekt lintDebug testDebugUnitTest passes.
- core/database/schemas/.../AppDatabase/5.json exists with the new columns and unique name index.
- App launches; can save existing exercises and trainings; trying to create exercise with a duplicate name (case-insensitive) shows the error on the Name field.

CONSTRAINTS

- No UI surface for the plan editor in this PR. Stage 5.3 will be respec'd to include it. The plan storage exists, but no screen lets the user edit it yet.
- No deletion of getLastFinishedSet in this PR. Mark deprecated only.
- Phantom shims, AppDimension legacy nested objects, etc. — leave them alone unless directly affected by the spec changes.
- LICENSE / SPDX headers: add SPDX-License-Identifier: GPL-3.0-only to every new file.
- Every user-facing string goes through stringResource. The one new string (duplicate name error) must be added to both values/strings.xml and values-ru/strings.xml.

PR

Open a draft PR titled `feat(db): add plan-first schema v4 → v5`. Body lists changed files grouped by module and a brief summary of the new plan model. Mark ready for review after the verification gate passes.
```
