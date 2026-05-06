package io.github.stslex.workeeper.core.data.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseListItem
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.RecentExerciseDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getUniqueExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    suspend fun getExercisesByUuid(uuids: List<String>): List<ExerciseDataModel>

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun saveItem(item: ExerciseChangeDataModel): SaveResult

    /**
     * Inserts an `is_adhoc = 1` exercise row from a single user-typed name. Used by the
     * inline-create flow inside the Quick start / Track Now exercise picker. Type defaults
     * to `WEIGHTED`; description, image, and tags are not captured at create time — the
     * user can enrich the exercise from Exercise detail after the session graduates it.
     */
    suspend fun createInlineAdhocExercise(name: String): InlineAdhocResult

    suspend fun getAdhocPlan(exerciseUuid: String): List<PlanSetDataModel>?

    suspend fun setAdhocPlan(exerciseUuid: String, planSets: List<PlanSetDataModel>?)

    /**
     * Wipes the `weight` column from `exercise.last_adhoc_sets` and from every
     * `training_exercise.plan_sets` row that references this exercise. Used when the user
     * confirms a WEIGHTED → WEIGHTLESS type change so weighted plan values do not survive
     * the type flip and confuse Live workout pre-fill later.
     *
     * Runs as a single repository transaction; either every plan-set row referencing the
     * exercise has its weights cleared, or none do.
     */
    suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String)

    suspend fun deleteItem(uuid: String)

    /**
     * One-shot list of active exercises filtered by [query] (case-insensitive prefix on
     * name) with [excludeUuids] removed. Used by the Training Edit "Add exercises" picker
     * sheet. The list is small enough that filtering in memory is acceptable.
     */
    suspend fun searchActiveExercises(
        query: String,
        excludeUuids: Set<String>,
    ): List<ExerciseDataModel>

    suspend fun deleteAllItems(uuids: List<Uuid>)

    suspend fun archive(uuid: String)

    suspend fun restore(uuid: String)

    suspend fun permanentDelete(uuid: String)

    suspend fun canArchive(uuid: String): Boolean

    suspend fun canPermanentlyDeleteImmediately(uuid: String): Boolean

    suspend fun getActiveTrainingsUsing(exerciseUuid: String): List<String>

    fun pagedArchived(): Flow<PagingData<ExerciseDataModel>>

    fun observeArchivedCount(): Flow<Int>

    suspend fun countSessionsUsing(exerciseUuid: String): Int

    /**
     * Count of distinct active library trainings (non-archived, non-adhoc) referencing
     * [exerciseUuid] via `training_exercise_table`. Surfaces as the "in M trainings"
     * footer segment on Exercise rows. (v2.4 F1.)
     */
    fun observeLinkedTrainingsCount(exerciseUuid: String): Flow<Int>

    /**
     * Timestamp (epoch millis) of the most recently finished session in which a non-skipped
     * performed-exercise referenced [exerciseUuid]. `null` when no such session exists.
     * Surfaces as the "last Xd ago" footer segment on Exercise rows. (v2.4 F2.)
     */
    fun observeLastTrainedAt(exerciseUuid: String): Flow<Long?>

    fun pagedActiveByTags(tagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>

    /**
     * Paged active library exercises joined with derived stats: session count,
     * linked-trainings count, last-trained timestamp, and tag names. Drives the v2.4
     * footer on the all-exercises list. When [filterTagUuids] is non-empty, applies OR
     * semantics (matches any of the given tags). (v2.4 E6.)
     */
    fun pagedActiveWithStats(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<ExerciseListItem>>

    suspend fun getRecentHistory(exerciseUuid: String, limit: Int): List<HistoryEntry>

    /**
     * UUID of the exercise from the most recently finished session, or `null` when no
     * finished sessions exist. Used by the v2.2 chart screen to pick a default selection
     * when entered without an explicit `exerciseUuid`.
     */
    suspend fun getLastTrainedExerciseUuid(): String?

    /**
     * Active exercises with at least one finished session, ordered by most-recent-finished
     * first. Powers the v2.2 chart picker.
     */
    suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDataModel>

    /**
     * Returns the tag names denormalized through the `exercise_tag` join. Replaces the
     * legacy `ExerciseDataModel.labels` field; callers query this only when they actually
     * need to render tags so the Exercise data model stays untainted by join data.
     */
    suspend fun getLabels(exerciseUuid: String): List<String>

    /**
     * Bulk-archive a batch of exercises. Mirrors [ExerciseRepository.archive] except it
     * runs in one transaction; exercises currently used by an active (non-archived)
     * training are excluded and surfaced in [BulkArchiveOutcome.blockedNames].
     */
    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveOutcome

    /**
     * Bulk-permanent-delete exercises. Caller is expected to pre-validate via
     * [canBulkPermanentDelete] — the DAO RESTRICTs deletion when an exercise is
     * referenced by an active template or any session history.
     */
    suspend fun bulkPermanentDelete(uuids: Set<String>)

    /**
     * True when every exercise in [uuids] has zero session history and is not used by an
     * active (non-archived) template.
     */
    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean

    sealed interface SaveResult {

        data object Success : SaveResult

        data object DuplicateName : SaveResult
    }

    data class BulkArchiveOutcome(
        val archivedCount: Int,
        val blockedNames: List<String>,
    )

    /**
     * Result of [createInlineAdhocExercise]. [reusedExisting] is true when a case-insensitive
     * name match was found and the existing row was returned without insert. The picker uses
     * this flag to decide whether to fetch the PR baseline — reused rows have history,
     * fresh-inserted ad-hoc rows do not.
     */
    data class InlineAdhocResult(
        val exercise: ExerciseDataModel,
        val reusedExisting: Boolean,
    )
}
