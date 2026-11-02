// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.ExercisePlanWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class RootSessionRepository : SessionRepository {

    var activeSessionWithStats: Flow<SessionRepository.ActiveSessionWithStats?>? = flowOf(null)

    override fun observeActiveSessionWithStats(): Flow<SessionRepository.ActiveSessionWithStats?> =
        checkNotNull(activeSessionWithStats) { "the identity set up no active-session flow" }

    override fun observeActive(): Nothing = unreached("observeActive")

    override fun observeAnyActiveSession(): Nothing = unreached("observeAnyActiveSession")

    override suspend fun getAnyActiveSession(): Nothing = unreached("getAnyActiveSession")

    override suspend fun getActiveSessionProgress(): Nothing =
        unreached("getActiveSessionProgress")

    override suspend fun getActive(): Nothing = unreached("getActive")

    override fun pagedRecentWithStats(): Flow<PagingData<RecentSessionDataModel>> = flowOf(PagingData.empty())

    override fun observeFinishedTimesBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<Long>> = flowOf(emptyList())

    override fun observeLastFinishedSession(): Flow<SessionRepository.LastFinishedSession?> = flowOf(null)

    override suspend fun getSessionDetail(sessionUuid: String): Nothing =
        unreached("getSessionDetail")

    override fun pagedFinished(): Nothing = unreached("pagedFinished")

    override fun pagedFinishedByTraining(trainingUuid: String): Nothing =
        unreached("pagedFinishedByTraining")

    override suspend fun getRecentFinishedByTraining(trainingUuid: String, limit: Int): Nothing =
        unreached("getRecentFinishedByTraining")

    override suspend fun getById(uuid: String): Nothing = unreached("getById")

    override suspend fun startSession(trainingUuid: String): Nothing = unreached("startSession")

    override suspend fun startSessionWithExercises(
        trainingUuid: String,
        exerciseUuids: List<Pair<String, Int>>,
    ): Nothing = unreached("startSessionWithExercises")

    override suspend fun resumeSession(sessionUuid: String): Nothing = unreached("resumeSession")

    override suspend fun finishSession(sessionUuid: String, finishedAt: Long): Nothing =
        unreached("finishSession")

    override suspend fun finishSessionAtomic(
        sessionUuid: String,
        finishedAt: Long,
        planUpdates: List<PlanUpdate>,
        newTrainingName: String?,
        discardedSetUuids: List<String>,
    ): Nothing = unreached("finishSessionAtomic")

    override suspend fun deleteSession(uuid: String): Nothing = unreached("deleteSession")

    override suspend fun createAdhocSession(name: String, exerciseUuids: List<String>): Nothing =
        unreached("createAdhocSession")

    override suspend fun addExerciseToActiveSession(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        attachToPlan: Boolean,
    ): Nothing = unreached("addExerciseToActiveSession")

    override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String): Nothing =
        unreached("discardAdhocSession")

    override suspend fun removeExerciseFromSession(
        performedExerciseUuid: String,
        exerciseUuid: String,
        trainingUuid: String?,
        removeFromPlan: Boolean,
    ): Nothing = unreached("removeExerciseFromSession")

    override fun pagedHistoryByExercise(exerciseUuid: String): Nothing =
        unreached("pagedHistoryByExercise")

    override suspend fun getHistoryByExercise(exerciseUuid: String): Nothing =
        unreached("getHistoryByExercise")
}

internal class RootTrainingRepository : TrainingRepository {

    override fun getTrainingsUnique(query: String): Nothing = unreached("getTrainingsUnique")

    override suspend fun updateTraining(training: TrainingChangeDataModel): Nothing =
        unreached("updateTraining")

    override suspend fun updateTrainingWithPlans(
        training: TrainingChangeDataModel,
        plans: List<ExercisePlanWrite>,
    ): Nothing = unreached("updateTrainingWithPlans")

    override suspend fun updateName(uuid: String, name: String): Nothing =
        unreached("updateName")

    override suspend fun removeTraining(uuid: String): Nothing = unreached("removeTraining")

    override suspend fun getTraining(uuid: String): Nothing = unreached("getTraining")

    override fun subscribeForTraining(uuid: String): Nothing = unreached("subscribeForTraining")

    override suspend fun removeAll(uuids: List<String>): Nothing = unreached("removeAll")

    override suspend fun archive(uuid: String): Nothing = unreached("archive")

    override suspend fun restore(uuid: String): Nothing = unreached("restore")

    override suspend fun permanentDelete(uuid: String): Nothing = unreached("permanentDelete")

    override fun pagedArchived(): Nothing = unreached("pagedArchived")

    override fun observeArchivedCount(): Nothing = unreached("observeArchivedCount")

    override suspend fun countSessionsUsing(trainingUuid: String): Nothing =
        unreached("countSessionsUsing")

    override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing =
        unreached("pagedActiveWithStats")

    override fun observeRecentTemplates(limit: Int): Flow<List<TrainingListItem>> = flowOf(emptyList())

    override fun observeMostForgottenTemplate(): Nothing =
        unreached("observeMostForgottenTemplate")

    override suspend fun bulkArchive(uuids: Set<String>): Nothing = unreached("bulkArchive")

    override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing =
        unreached("bulkPermanentDelete")

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing =
        unreached("canBulkPermanentDelete")
}

private fun unreached(member: String): Nothing = error("Unexpected root repository operation: $member")
