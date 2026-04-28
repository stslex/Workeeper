package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getUniqueExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    suspend fun getExercisesByUuid(uuids: List<String>): List<ExerciseDataModel>

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun saveItem(item: ExerciseChangeDataModel): SaveResult

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
}
