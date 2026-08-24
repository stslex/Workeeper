package io.github.stslex.workeeper.core.data.exercise.exercise

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseListItem
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.exercise.model.RecentExerciseDataModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
interface ExerciseRepository {

    val exercises: Flow<PagingData<ExerciseDataModel>>

    fun getUniqueExercises(query: String): Flow<PagingData<ExerciseDataModel>>

    suspend fun getExercisesByUuid(uuids: List<String>): List<ExerciseDataModel>

    suspend fun getAdhocPlans(uuids: List<String>): Map<String, List<PlanSetDataModel>?>

    suspend fun getExercise(uuid: String): ExerciseDataModel?

    suspend fun saveItem(item: ExerciseChangeDataModel): SaveResult

    /** Inserts an `is_adhoc = 1` exercise from a typed name; type defaults to `WEIGHTED`. */
    suspend fun createInlineAdhocExercise(name: String): InlineAdhocResult

    suspend fun getAdhocPlan(exerciseUuid: String): List<PlanSetDataModel>?

    suspend fun setAdhocPlan(exerciseUuid: String, planSets: List<PlanSetDataModel>?)

    /**
     * Updates only `exercise_table.type`. GUARD: on a flip to WEIGHTLESS the caller owes
     * [clearWeightsFromAllPlansForExercise]; [saveItem] derives that cascade itself.
     */
    suspend fun setExerciseType(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    )

    /**
     * Wipes `weight` from `exercise.last_adhoc_sets` and every referencing `plan_sets`, in one
     * transaction. For the [setExerciseType] path — [saveItem] runs the same cascade itself.
     */
    suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String)

    suspend fun deleteItem(uuid: String)

    /** Active exercises matching [query] (case-insensitive prefix) minus [excludeUuids]. */
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

    /** Count of active library trainings referencing [exerciseUuid] ("in M trainings" footer). */
    fun observeLinkedTrainingsCount(exerciseUuid: String): Flow<Int>

    /** Epoch millis of the last finished, non-skipped use of [exerciseUuid]; null when none. */
    fun observeLastTrainedAt(exerciseUuid: String): Flow<Long?>

    fun pagedActiveByTags(tagUuids: Set<String>): Flow<PagingData<ExerciseDataModel>>

    /**
     * Paged active library exercises with derived stats (sessions, linked trainings, last
     * trained, tags). A non-empty [filterTagUuids] matches any of the given tags.
     */
    fun pagedActiveWithStats(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<ExerciseListItem>>

    suspend fun getRecentHistory(exerciseUuid: String, limit: Int): List<HistoryEntry>

    /** UUID of the exercise from the most recent finished session; null when there are none. */
    suspend fun getLastTrainedExerciseUuid(): String?

    /** Active exercises with at least one finished session, most-recent-finished first. */
    suspend fun getRecentlyTrainedExercises(): List<RecentExerciseDataModel>

    /** Tag names via the `exercise_tag` join; queried only where tags are actually rendered. */
    suspend fun getLabels(exerciseUuid: String): List<String>

    /**
     * Bulk-archive in one transaction; exercises used by an active training are skipped and
     * surfaced in [BulkArchiveOutcome.blocked] with the blocking training names.
     */
    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveOutcome

    /** Bulk-permanent-delete; pre-validate via [canBulkPermanentDelete] — the DAO RESTRICTs. */
    suspend fun bulkPermanentDelete(uuids: Set<String>)

    /** True when every exercise in [uuids] has no session history and no active template. */
    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean

    sealed interface SaveResult {

        data object Success : SaveResult

        data object DuplicateName : SaveResult
    }

    data class BulkArchiveOutcome(
        val archivedCount: Int,
        val blocked: List<BlockedExercise>,
    ) {

        /** An exercise blocked by active trainings; [activeTrainings] names them for the UI. */
        data class BlockedExercise(
            val name: String,
            val activeTrainings: List<String>,
        )
    }

    /**
     * Result of [createInlineAdhocExercise]. [reusedExisting] means a case-insensitive name
     * match was returned without insert, so the row has history and a PR baseline to fetch.
     */
    data class InlineAdhocResult(
        val exercise: ExerciseDataModel,
        val reusedExisting: Boolean,
    )
}
