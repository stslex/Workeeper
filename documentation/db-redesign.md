# Database Redesign — v2 to v3

This document specifies the v3 schema of the Room database that
replaces the current v2 schema described in
[data-needs.md](data-needs.md). It covers the concrete entity
declarations, foreign keys and cascade rules, indexes, DAOs, and the
migration strategy.

It is the input to the Claude Code prompt that implements the change.
The prompt is at the bottom of this document.

For the conceptual model, see [product.md](product.md). For why each
entity / index exists, see [data-needs.md](data-needs.md).

## Migration strategy

**Destructive migration.** The application has no production users
yet, so retaining v2 data is not required. The v2 → v3 transition
drops all old tables and creates the new ones from scratch.

Concretely:

- Bump `@Database(version = 3)` in `AppDatabase`.
- Add `.fallbackToDestructiveMigrationFrom(2)` to the database
  builder in `CoreDatabaseModule`. This tells Room: when migrating
  from version 2 specifically, drop and recreate. Other migration
  paths (none currently exist) remain strict.
- Remove `MIGRATION_1_2` (`Migration12.kt`) from the migrations list
  if it is registered there — version 1 is no longer reachable from
  a clean install of the new app, and we are dropping its data
  anyway. Keep the file in source for now (history) but unregister
  it.
- All new entities ship in `version = 3`.

This decision is documented in
[product.md](product.md) Release scope.

## Module layout

The `core/database` module is restructured to match the new domain
shape. Current package layout:

```
core/database/
  AppDatabase.kt
  converters/{StringConverter, SetsTypeConverter, UuidConverter}.kt
  exercise/{ExerciseDao, ExerciseEntity}.kt
  exercise/model/{SetsEntity, SetsEntityType}.kt
  training/{TrainingDao, TrainingEntity}.kt
  trainingLabels/{TrainingLabelDao, TrainingLabelEntity}.kt
  migrations/Migration12.kt
  di/CoreDatabaseModule.kt
```

New layout:

```
core/database/
  AppDatabase.kt
  converters/UuidConverter.kt          (kept; the others are dropped)
  exercise/{ExerciseDao, ExerciseEntity}.kt
  training/{TrainingDao, TrainingEntity}.kt
  training/{TrainingExerciseDao, TrainingExerciseEntity}.kt
  session/{SessionDao, SessionEntity}.kt
  session/{PerformedExerciseDao, PerformedExerciseEntity}.kt
  session/model/{SetEntity, SetTypeEntity}.kt
  tag/{TagDao, TagEntity}.kt
  tag/{ExerciseTagDao, ExerciseTagEntity}.kt
  tag/{TrainingTagDao, TrainingTagEntity}.kt
  di/CoreDatabaseModule.kt
```

Dropped:

- `converters/StringConverter.kt` — was for `List<String>` labels;
  labels are now their own table.
- `converters/SetsTypeConverter.kt` — was for `List<SetsEntity>`;
  sets are now their own table.
- `trainingLabels/` package — replaced by the unified `tag/` package
  with shared pool plus join tables.
- `exercise/model/` package — `SetsEntity` is now `SetEntity` in the
  session package; `SetsEntityType` becomes `SetTypeEntity` colocated.

## Entities

Eight entity classes in v3. Every entity uses `@PrimaryKey
val uuid: Uuid = Uuid.random()` for its primary key — consistent
with the existing `UuidConverter`.

### `TrainingEntity` (rewrite)

```kotlin
@Entity(
    tableName = "training_table",
    indices = [
        Index(value = ["is_adhoc", "archived", "name"]),
        Index(value = ["archived"]),
    ],
)
data class TrainingEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "is_adhoc", defaultValue = "0")
    val isAdhoc: Boolean,
    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
```

Removed vs v2: `exercises: List<Uuid>` (now in `training_exercise`),
`labels: List<String>` (now in `training_tag`), `timestamp` (replaced
by `createdAt` for clarity).

### `TrainingExerciseEntity` (new)

Join between training and exercise with order preservation.

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
)
```

`onDelete = RESTRICT` on `exercise_uuid` enforces the product rule:
archiving an exercise referenced by an active training is blocked at
the application level (see `ExerciseDao.canArchive`); permanent
deletion of an exercise that still has join rows fails at the SQL
level. Defence in depth.

### `ExerciseEntity` (rewrite)

```kotlin
@Entity(
    tableName = "exercise_table",
    indices = [
        Index(value = ["archived", "name"]),
        Index(value = ["archived"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "type")
    val type: ExerciseTypeEntity,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
```

`ExerciseTypeEntity`:

```kotlin
enum class ExerciseTypeEntity { WEIGHTED, WEIGHTLESS }
```

Stored as the enum name string by Room's default. No converter
needed.

`image_path` is reserved for v1.5 — column exists in v3 but is
always null until the image feature ships. Cheaper to add the column
once than to migrate again later.

### `SessionEntity` (new)

```kotlin
@Entity(
    tableName = "session_table",
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["training_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["training_uuid", "finished_at"]),
        Index(value = ["state"]),
        Index(value = ["finished_at"]),
    ],
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "training_uuid")
    val trainingUuid: Uuid,
    @ColumnInfo(name = "state")
    val state: SessionStateEntity,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
)
```

`SessionStateEntity`:

```kotlin
enum class SessionStateEntity { IN_PROGRESS, FINISHED }
```

Note on `at-most-one-active-session`: SQLite supports partial
indexes (`CREATE UNIQUE INDEX ... WHERE state = 'IN_PROGRESS'`), but
Room's `@Index` annotation doesn't expose the `WHERE` clause. Two
options:

- Plain index on `state`, enforce uniqueness in
  `SessionRepository.startSession`.
- Add a manual `CREATE UNIQUE INDEX` via `RoomDatabase.Callback.onCreate`
  if the redundancy is wanted.

Default for v1: repository-level invariant only. Cheap, sufficient
for a single-process Android app.

### `PerformedExerciseEntity` (new)

```kotlin
@Entity(
    tableName = "performed_exercise_table",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["session_uuid"],
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
        Index(value = ["session_uuid", "position"]),
        Index(value = ["exercise_uuid"]),
    ],
)
data class PerformedExerciseEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "session_uuid")
    val sessionUuid: Uuid,
    @ColumnInfo(name = "exercise_uuid")
    val exerciseUuid: Uuid,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "skipped", defaultValue = "0")
    val skipped: Boolean,
)
```

`onDelete = RESTRICT` on `exercise_uuid` enforces "permanent delete of
an exercise with history fails at SQL level"; the user must
permanently delete each session that references the exercise first
(or the application can cascade explicitly in
`ExerciseRepository.permanentDelete`).

### `SetEntity` (new — replaces in-blob `SetsEntity`)

```kotlin
@Entity(
    tableName = "set_table",
    foreignKeys = [
        ForeignKey(
            entity = PerformedExerciseEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["performed_exercise_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["performed_exercise_uuid", "position"]),
    ],
)
data class SetEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "performed_exercise_uuid")
    val performedExerciseUuid: Uuid,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "reps")
    val reps: Int,
    @ColumnInfo(name = "weight")
    val weight: Double?,
    @ColumnInfo(name = "type")
    val type: SetTypeEntity,
)
```

`weight` is nullable for weightless exercises. The application
enforces "non-null for weighted, null for weightless" — there is no
DB constraint expressing this because it depends on a join.

`SetTypeEntity` replaces `SetsEntityType`, same shape:

```kotlin
enum class SetTypeEntity { WARM, WORK, FAIL, DROP }
```

### `TagEntity` (new — replaces `TrainingLabelEntity`)

```kotlin
@Entity(
    tableName = "tag_table",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: Uuid = Uuid.random(),
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,
)
```

`COLLATE NOCASE` on the indexed column gives case-insensitive
uniqueness — typing "Lower body" twice produces one tag. This is the
SQLite-native answer to the open question on tag case sensitivity
from data-needs.

### `ExerciseTagEntity` (new)

```kotlin
@Entity(
    tableName = "exercise_tag_table",
    primaryKeys = ["exercise_uuid", "tag_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["exercise_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["tag_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_uuid", "exercise_uuid"])],
)
data class ExerciseTagEntity(
    @ColumnInfo(name = "exercise_uuid") val exerciseUuid: Uuid,
    @ColumnInfo(name = "tag_uuid") val tagUuid: Uuid,
)
```

### `TrainingTagEntity` (new)

Symmetric to `ExerciseTagEntity`:

```kotlin
@Entity(
    tableName = "training_tag_table",
    primaryKeys = ["training_uuid", "tag_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = TrainingEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["training_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["tag_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_uuid", "training_uuid"])],
)
data class TrainingTagEntity(
    @ColumnInfo(name = "training_uuid") val trainingUuid: Uuid,
    @ColumnInfo(name = "tag_uuid") val tagUuid: Uuid,
)
```

## Cascade rule summary

| Parent → Child | onDelete | Rationale |
|---|---|---|
| training → training_exercise | CASCADE | Removing a training removes its membership rows. |
| exercise → training_exercise | RESTRICT | Cannot delete an exercise referenced by any training. |
| training → session | CASCADE | Permanently deleting a training removes its sessions. |
| session → performed_exercise | CASCADE | Sessions own their performed exercises. |
| exercise → performed_exercise | RESTRICT | Cannot delete an exercise that has history. |
| performed_exercise → set | CASCADE | Performed exercises own their sets. |
| exercise → exercise_tag | CASCADE | Cleanup tag links when exercise is gone. |
| tag → exercise_tag | CASCADE | Cleanup links when tag is deleted. |
| training → training_tag | CASCADE | Same as above. |
| tag → training_tag | CASCADE | Same as above. |

The two `RESTRICT` rules are the teeth behind the product invariant
"archive system, not hard delete from library". An attempt to
`DELETE FROM exercise_table WHERE uuid = ?` while history exists
will throw `SQLiteConstraintException` at the SQL layer. The
repository catches this and either translates it to a domain error
or cascades manually after deleting children (the latter is what
permanent-delete-from-archive does).

## DAOs

One DAO per entity for v1, with the queries listed in the data-needs
catalog. Each DAO interface stub below shows the full set of queries
required; the prompt at the end asks Claude Code to implement them
with bodies.

### `TrainingDao`

```kotlin
@Dao
interface TrainingDao {
    @Query("""
        SELECT * FROM training_table
        WHERE is_adhoc = 0 AND archived = 0
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun pagedTemplates(): PagingSource<Int, TrainingEntity>

    @Query("""
        SELECT t.* FROM training_table t
        JOIN training_tag_table tt ON tt.training_uuid = t.uuid
        WHERE t.is_adhoc = 0 AND t.archived = 0 AND tt.tag_uuid IN (:tagUuids)
        GROUP BY t.uuid
        ORDER BY t.name COLLATE NOCASE ASC
    """)
    fun pagedTemplatesByTags(tagUuids: List<Uuid>): PagingSource<Int, TrainingEntity>

    @Query("SELECT * FROM training_table WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun pagedArchived(): PagingSource<Int, TrainingEntity>

    @Query("SELECT * FROM training_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): TrainingEntity?

    @Insert
    suspend fun insert(training: TrainingEntity)

    @Update
    suspend fun update(training: TrainingEntity)

    @Query("UPDATE training_table SET archived = 1 WHERE uuid = :uuid")
    suspend fun archive(uuid: Uuid)

    @Query("UPDATE training_table SET archived = 0 WHERE uuid = :uuid")
    suspend fun restore(uuid: Uuid)

    @Query("DELETE FROM training_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)
}
```

### `TrainingExerciseDao`

```kotlin
@Dao
interface TrainingExerciseDao {
    @Query("""
        SELECT * FROM training_exercise_table
        WHERE training_uuid = :trainingUuid
        ORDER BY position ASC
    """)
    suspend fun getByTraining(trainingUuid: Uuid): List<TrainingExerciseEntity>

    @Query("""
        SELECT COUNT(*) FROM training_exercise_table te
        JOIN training_table t ON t.uuid = te.training_uuid
        WHERE te.exercise_uuid = :exerciseUuid
          AND t.archived = 0
          AND t.is_adhoc = 0
    """)
    suspend fun countActiveTemplatesUsing(exerciseUuid: Uuid): Int

    @Insert
    suspend fun insert(rows: List<TrainingExerciseEntity>)

    @Query("DELETE FROM training_exercise_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)
}
```

### `ExerciseDao`

```kotlin
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise_table WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun pagedActive(): PagingSource<Int, ExerciseEntity>

    @Query("""
        SELECT e.* FROM exercise_table e
        JOIN exercise_tag_table et ON et.exercise_uuid = e.uuid
        WHERE e.archived = 0 AND et.tag_uuid IN (:tagUuids)
        GROUP BY e.uuid
        ORDER BY e.name COLLATE NOCASE ASC
    """)
    fun pagedActiveByTags(tagUuids: List<Uuid>): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercise_table WHERE archived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun pagedArchived(): PagingSource<Int, ExerciseEntity>

    @Query("SELECT * FROM exercise_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): ExerciseEntity?

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("UPDATE exercise_table SET archived = 1 WHERE uuid = :uuid")
    suspend fun archive(uuid: Uuid)

    @Query("UPDATE exercise_table SET archived = 0 WHERE uuid = :uuid")
    suspend fun restore(uuid: Uuid)

    @Query("DELETE FROM exercise_table WHERE uuid = :uuid")
    suspend fun permanentDelete(uuid: Uuid)
}
```

### `SessionDao`

```kotlin
@Dao
interface SessionDao {
    @Query("SELECT * FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1")
    suspend fun getActive(): SessionEntity?

    @Query("""
        SELECT * FROM session_table
        WHERE state = 'FINISHED'
        ORDER BY finished_at DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("""
        SELECT * FROM session_table
        WHERE state = 'FINISHED'
        ORDER BY finished_at DESC
    """)
    fun pagedFinished(): PagingSource<Int, SessionEntity>

    @Query("""
        SELECT * FROM session_table
        WHERE training_uuid = :trainingUuid AND state = 'FINISHED'
        ORDER BY finished_at DESC
    """)
    fun pagedFinishedByTraining(trainingUuid: Uuid): PagingSource<Int, SessionEntity>

    @Query("SELECT * FROM session_table WHERE uuid = :uuid")
    suspend fun getById(uuid: Uuid): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM session_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)
}
```

### `PerformedExerciseDao`

```kotlin
@Dao
interface PerformedExerciseDao {
    @Query("""
        SELECT * FROM performed_exercise_table
        WHERE session_uuid = :sessionUuid
        ORDER BY position ASC
    """)
    suspend fun getBySession(sessionUuid: Uuid): List<PerformedExerciseEntity>

    @Insert
    suspend fun insert(rows: List<PerformedExerciseEntity>)

    @Query("UPDATE performed_exercise_table SET skipped = :skipped WHERE uuid = :uuid")
    suspend fun setSkipped(uuid: Uuid, skipped: Boolean)
}
```

### `SetDao`

```kotlin
@Dao
interface SetDao {
    @Query("""
        SELECT * FROM set_table
        WHERE performed_exercise_uuid = :performedExerciseUuid
        ORDER BY position ASC
    """)
    suspend fun getByPerformedExercise(performedExerciseUuid: Uuid): List<SetEntity>

    @Query("""
        SELECT s.* FROM set_table s
        JOIN performed_exercise_table pe ON pe.uuid = s.performed_exercise_uuid
        JOIN session_table sn ON sn.uuid = pe.session_uuid
        WHERE pe.exercise_uuid = :exerciseUuid
          AND sn.state = 'FINISHED'
        ORDER BY sn.finished_at DESC, s.position DESC
        LIMIT 1
    """)
    suspend fun getLastFinishedSet(exerciseUuid: Uuid): SetEntity?

    @Insert
    suspend fun insert(set: SetEntity)

    @Update
    suspend fun update(set: SetEntity)

    @Query("DELETE FROM set_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)
}
```

### `TagDao`

```kotlin
@Dao
interface TagDao {
    @Query("SELECT * FROM tag_table ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("""
        SELECT * FROM tag_table
        WHERE name LIKE :prefix || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE ASC
    """)
    suspend fun searchByPrefix(prefix: String): List<TagEntity>

    @Query("SELECT * FROM tag_table WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("DELETE FROM tag_table WHERE uuid = :uuid")
    suspend fun delete(uuid: Uuid)
}
```

### `ExerciseTagDao`, `TrainingTagDao`

Symmetric pair, only the column names differ.

```kotlin
@Dao
interface ExerciseTagDao {
    @Query("SELECT tag_uuid FROM exercise_tag_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun getTagUuids(exerciseUuid: Uuid): List<Uuid>

    @Insert
    suspend fun insert(rows: List<ExerciseTagEntity>)

    @Query("DELETE FROM exercise_tag_table WHERE exercise_uuid = :exerciseUuid")
    suspend fun deleteByExercise(exerciseUuid: Uuid)
}

@Dao
interface TrainingTagDao {
    @Query("SELECT tag_uuid FROM training_tag_table WHERE training_uuid = :trainingUuid")
    suspend fun getTagUuids(trainingUuid: Uuid): List<Uuid>

    @Insert
    suspend fun insert(rows: List<TrainingTagEntity>)

    @Query("DELETE FROM training_tag_table WHERE training_uuid = :trainingUuid")
    suspend fun deleteByTraining(trainingUuid: Uuid)
}
```

## `AppDatabase` shape

```kotlin
@Database(
    entities = [
        TrainingEntity::class,
        TrainingExerciseEntity::class,
        ExerciseEntity::class,
        SessionEntity::class,
        PerformedExerciseEntity::class,
        SetEntity::class,
        TagEntity::class,
        ExerciseTagEntity::class,
        TrainingTagEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val trainingDao: TrainingDao
    abstract val trainingExerciseDao: TrainingExerciseDao
    abstract val exerciseDao: ExerciseDao
    abstract val sessionDao: SessionDao
    abstract val performedExerciseDao: PerformedExerciseDao
    abstract val setDao: SetDao
    abstract val tagDao: TagDao
    abstract val exerciseTagDao: ExerciseTagDao
    abstract val trainingTagDao: TrainingTagDao

    companion object {
        const val NAME = "app.db"
    }
}
```

## Repository layer

This document only commits to the database layer. The `core/exercise`
module currently exposes `ExerciseRepository`, `TrainingRepository`,
`LabelRepository` interfaces. After v3 these need to evolve too —
specifically:

- `ExerciseRepository` adds `archive`, `restore`, `permanentDelete`,
  `canArchive` (the latter wraps `TrainingExerciseDao.countActiveTemplatesUsing`).
- `TrainingRepository` similarly.
- `LabelRepository` is renamed `TagRepository` and merges with the
  no-longer-needed `TrainingLabelRepository`.
- New `SessionRepository` exposing start / resume / finish / get-active /
  list-recent.

Actual repository signatures and Hilt wiring are in the
implementation prompt, not in this design doc.

## Tests

The redesign requires a fresh test pass:

- Drop / replace existing `core/database` tests (`ExerciseDaoTest`,
  `TrainingDaoTest`, `TrainingLabelDaoTest`, `AppDatabaseTest`,
  `Migration1To2Test`).
- Write new DAO tests per DAO covering CRUD + the indexed query
  paths.
- No migration test required (destructive migration; nothing to
  preserve).

Existing `BaseDatabaseTest` may be reusable.

## Closed and remaining open questions

Closed by this document:

- Cascade rules — explicit per FK in the table above.
- Tag case-insensitivity — `COLLATE NOCASE` on indexed column.
- Schema migration strategy — destructive, no data preserved.

Remaining open (deferred to feature specs / implementation):

- `at-most-one-active-session` enforcement — repository invariant
  only for v1; reconsider if races appear.

---

## Claude Code prompt

The following is the prompt for Claude Code to perform the migration.
Run after this design doc is approved.

```
Implement Workeeper database redesign v2 → v3 per documentation/db-redesign.md.

GOAL
Replace the current Room v2 schema with the v3 schema described in db-redesign.md. v2 data is dropped (no users in production). Refactor core/database module structure to match the new domain layout.

PROCESS — TWO PASSES

PASS 1 — SCAFFOLD AND COMPILE
Goal: get the new schema compiling, schema JSON exported, and the database module green. No repository or feature work yet.

1. Read documentation/db-redesign.md cover to cover before writing code.
2. Restructure core/database packages per the "New layout" section. Delete dropped files: converters/StringConverter.kt, converters/SetsTypeConverter.kt, the trainingLabels/ package, exercise/model/SetsEntity.kt, exercise/model/SetsEntityType.kt. Keep migrations/Migration12.kt as-is in source (do not register in builder).
3. Create the 9 entity classes exactly as specified in the Entities section, with the indexes, foreign keys, cascade rules, and column types from the doc. Type converters: only UuidConverter remains.
4. Create the 9 DAO interfaces with all queries from the DAOs section. Use Flow / suspend / PagingSource as shown.
5. Update AppDatabase.kt to version=3, list all 9 entities, expose all 9 DAOs.
6. Update CoreDatabaseModule.kt: provide all 9 DAOs; add `.fallbackToDestructiveMigrationFrom(2)` to the database builder. Remove migration registration if any.
7. Verify `./gradlew :core:database:assembleDebug` succeeds. Verify schema JSON is exported (look in core/database/schemas/3.json).
8. STOP and report. List all created / deleted / modified files. Do not proceed to PASS 2 without explicit approval.

PASS 2 — REPOSITORY AND FEATURE ADAPTATION
Goal: refit the core/exercise module and any feature module that imports the old entity types.

1. Update core/exercise repository interfaces and implementations per the "Repository layer" section. Specifically:
   - ExerciseRepository: add archive, restore, permanentDelete, canArchive.
   - TrainingRepository: add archive, restore, permanentDelete.
   - Rename LabelRepository → TagRepository; merge with TrainingLabelRepository if still separate.
   - Create new SessionRepository (interface + impl) with: startSession, resumeSession, finishSession, getActive, observeActive, observeRecent, getById, deleteSession.
   - Create new PerformedExerciseRepository if not folded into SessionRepository.
   - Create new SetRepository.
2. Update Hilt wiring in core/exercise/di/CoreExerciseModule.kt.
3. Run `./gradlew :core:exercise:assembleDebug`. Fix compilation in any feature module that imported old types (ExerciseEntity, TrainingEntity, SetsEntity, TrainingLabelEntity). Many feature stores will not compile — adjust their data models to use the new entity shapes.
4. Re-run `./gradlew assembleDebug` for the whole project.
5. Update unit tests:
   - Delete or rewrite core/database tests (ExerciseDaoTest, TrainingDaoTest, TrainingLabelDaoTest, AppDatabaseTest, Migration1To2Test).
   - Delete repository tests in core/exercise that target old shapes; add new tests for new repositories.
   - Feature-level handler / store tests touched by data model changes — update minimally to keep the suite passing.
6. Run `./gradlew detekt lintDebug testDebugUnitTest`. Fix all violations.

CONSTRAINTS
- All entity, DAO, and repository names match the doc exactly.
- Do not invent fields or queries beyond what's in the doc. If something seems missing, STOP and ask.
- Do not write migration logic for v2 → v3 data preservation. The migration is destructive (fallbackToDestructiveMigrationFrom).
- Do not touch documentation/*.md files.
- Do not start UI feature rewrites in this PR. Scope is data layer only.
- If a custom Detekt MVI rule fires on touched code that wasn't violating before, fix the code, not the rule.

VERIFICATION CHECKLIST
- [ ] `./gradlew :core:database:assembleDebug` passes.
- [ ] `./gradlew :core:exercise:assembleDebug` passes.
- [ ] `./gradlew assembleDebug` passes (whole project).
- [ ] `./gradlew detekt lintDebug` passes.
- [ ] `./gradlew testDebugUnitTest` passes.
- [ ] core/database/schemas/3.json exists and matches the entity declarations.
- [ ] No reference to `ExerciseEntity.labels` (List<String>), `ExerciseEntity.sets` (JSON), `TrainingEntity.exercises` (List<Uuid>), or `TrainingLabelEntity` anywhere in the codebase.

PR
Open one PR titled `feat(db): redesign schema v2 → v3`. Body lists changes per file. Mark as draft until both PASS 1 and PASS 2 are complete. After approval merges, the data layer is ready for the v1 feature rewrites.
```
