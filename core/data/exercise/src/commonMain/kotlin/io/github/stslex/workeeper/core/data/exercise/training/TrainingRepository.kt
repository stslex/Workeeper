package io.github.stslex.workeeper.core.data.exercise.training

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface TrainingRepository {

    fun getTrainingsUnique(query: String): Flow<PagingData<TrainingDataModel>>

    suspend fun updateTraining(training: TrainingChangeDataModel)

    /**
     * [updateTraining] plus every listed exercise's `plan_sets`, as one transaction.
     * Plan writes land after the exercise sync, which truncates and re-inserts the plan rows.
     */
    suspend fun updateTrainingWithPlans(
        training: TrainingChangeDataModel,
        plans: List<ExercisePlanWrite>,
    )

    /** Name-only update; skips [updateTraining]'s exercise/label sync for the active plan. */
    suspend fun updateName(uuid: String, name: String)

    suspend fun removeTraining(uuid: String)

    suspend fun getTraining(uuid: String): TrainingDataModel?

    fun subscribeForTraining(uuid: String): Flow<TrainingDataModel>

    suspend fun removeAll(uuids: List<String>)

    suspend fun archive(uuid: String)

    suspend fun restore(uuid: String)

    suspend fun permanentDelete(uuid: String)

    fun pagedArchived(): Flow<PagingData<TrainingDataModel>>

    fun observeArchivedCount(): Flow<Int>

    suspend fun countSessionsUsing(trainingUuid: String): Int

    /** Paged active (non-archived, non-adhoc) trainings with derived stats. Trainings tab. */
    fun pagedActiveWithStats(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<TrainingListItem>>

    /** Recent templates by `lastSessionAt DESC`, never-used last by name. Home picker sheet. */
    fun observeRecentTemplates(limit: Int): Flow<List<TrainingListItem>>

    /** Most forgotten template — oldest last session, never-run first; null when none exists. */
    fun observeMostForgottenTemplate(): Flow<TrainingListItem?>

    /** Bulk-archive in one transaction; trainings with an in-progress session are skipped. */
    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveOutcome

    /** Bulk-permanent-delete; caller must pre-validate — the DAO raises on an FK violation. */
    suspend fun bulkPermanentDelete(uuids: Set<String>)

    /** True when every training in [uuids] has no finished and no in-progress session. */
    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean

    /** One exercise's plan, addressed by uuid — [updateTrainingWithPlans]'s unit of write. */
    data class ExercisePlanWrite(
        val exerciseUuid: String,
        val planSets: List<PlanSetDataModel>?,
    )

    data class BulkArchiveOutcome(
        val archivedCount: Int,
        val blockedNames: List<String>,
    )
}
