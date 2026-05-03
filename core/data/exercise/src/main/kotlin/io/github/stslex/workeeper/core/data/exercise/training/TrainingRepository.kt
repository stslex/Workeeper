package io.github.stslex.workeeper.core.exercise.training

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface TrainingRepository {

    fun getTrainingsUnique(query: String): Flow<PagingData<TrainingDataModel>>

    suspend fun updateTraining(training: TrainingChangeDataModel)

    /**
     * Lightweight name-only update used by the v2.3 Live workout editable header. Side-steps
     * [updateTraining]'s exercise/label sync so a header tap does not retouch
     * `training_exercise_table` or `training_tag_table` rows for the active session's plan.
     */
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

    /**
     * Paged stream of active (non-archived, non-adhoc) trainings with derived stats:
     * exercise count, last finished session, current in-progress session for that
     * training. Used by the Trainings tab.
     */
    fun pagedActiveWithStats(
        filterTagUuids: Set<String>,
    ): Flow<PagingData<TrainingListItem>>

    /**
     * Hot stream of recent template trainings, ordered by `lastSessionAt DESC` (trainings
     * never used come last, sorted by name). Powers the Home training-picker bottom sheet.
     */
    fun observeRecentTemplates(limit: Int): Flow<List<TrainingListItem>>

    /**
     * Bulk-archive a batch of trainings in a single transaction. Trainings with an active
     * (in-progress) session are excluded; the returned [BulkArchiveOutcome] reports how
     * many were archived and which got skipped.
     */
    suspend fun bulkArchive(uuids: Set<String>): BulkArchiveOutcome

    /**
     * Bulk-permanent-delete trainings. The caller is expected to pre-validate that none
     * of the trainings have any session history (active or finished) — the DAO will
     * raise if a foreign key violation surfaces.
     */
    suspend fun bulkPermanentDelete(uuids: Set<String>)

    /**
     * True when every training in [uuids] has zero finished sessions and no in-progress
     * session. Drives the enabled/disabled state of the bulk-delete action.
     */
    suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean

    data class BulkArchiveOutcome(
        val archivedCount: Int,
        val blockedNames: List<String>,
    )
}
