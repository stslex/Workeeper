package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.exercise.model.RecentExerciseDataModel
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
     *
     * If a library exercise with the same name (case-insensitive) already exists, the
     * existing row is returned instead of raising the unique-name constraint. The caller
     * decides whether to flag this to the user; in the picker flow we silently surface the
     * library entry, since the user expectation is "I'll get an exercise named '<x>' added".
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

    fun pagedActiveByTags(tagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>

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
     * Result of [createInlineAdhocExercise]. The picker-side flow only needs the resulting
     * [exercise]; the [reusedExisting] flag lets call sites measure how often an inline
     * "create" actually surfaces a pre-existing library entry, which matters for both the
     * Q6 baseline detection and tech-debt telemetry.
     */
    data class InlineAdhocResult(
        val exercise: ExerciseDataModel,
        val reusedExisting: Boolean,
    )
}
