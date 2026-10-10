// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.BulkArchiveOutcome
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.ExercisePlanWrite
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AllTrainingsInteractorImplTest {

    private val trainingRepository = RecordingTrainingRepository()
    private val tagRepository = UnreachedTagRepository()
    private val sessionRepository = UnreachedSessionRepository()
    private val interactor = AllTrainingsInteractorImpl(
        trainingRepository = trainingRepository,
        tagRepository = tagRepository,
        sessionRepository = sessionRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `archiveTrainings delegates to repository bulkArchive`() = runTest {
        trainingRepository.bulkArchiveOutcome = BulkArchiveOutcome(2, emptyList())

        val outcome: BulkArchiveResult = interactor.archiveTrainings(setOf("a", "b"))

        assertEquals(2, outcome.archivedCount)
        assertContains(trainingRepository.bulkArchiveCalls, setOf("a", "b"))
    }

    @Test
    fun `deleteTrainings returns target count and delegates`() = runTest {
        val outcome = interactor.deleteTrainings(setOf("a", "b"))

        assertEquals(2, outcome)
        assertContains(trainingRepository.bulkPermanentDeleteCalls, setOf("a", "b"))
    }

    @Test
    fun `canPermanentlyDelete delegates to repository`() = runTest {
        trainingRepository.canBulkPermanentDeleteResult = true
        assertTrue(interactor.canPermanentlyDelete(setOf("a")))
    }
}

private fun unreached(member: String): Nothing =
    error("$member is not reached by AllTrainingsInteractorImplTest")

/**
 * The training repository as the relaxed MockK mock answered these identities: [bulkArchive] and
 * [canBulkPermanentDelete] return the values a test sets, [bulkPermanentDelete] accepts, and the
 * two bulk writes record their argument. Members these identities never reach fail fast.
 */
private class RecordingTrainingRepository : TrainingRepository {

    val bulkArchiveCalls = mutableListOf<Set<String>>()
    val bulkPermanentDeleteCalls = mutableListOf<Set<String>>()
    var bulkArchiveOutcome = BulkArchiveOutcome(archivedCount = 0, blockedNames = emptyList())
    var canBulkPermanentDeleteResult = false

    override suspend fun bulkArchive(uuids: Set<String>): BulkArchiveOutcome {
        bulkArchiveCalls.add(uuids)
        return bulkArchiveOutcome
    }

    override suspend fun bulkPermanentDelete(uuids: Set<String>) {
        bulkPermanentDeleteCalls.add(uuids)
    }

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Boolean =
        canBulkPermanentDeleteResult

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

    override fun observeRecentTemplates(limit: Int): Nothing = unreached("observeRecentTemplates")

    override fun observeMostForgottenTemplate(): Nothing =
        unreached("observeMostForgottenTemplate")
}

/** No identity here reaches the tag repository. */
private class UnreachedTagRepository : TagRepository {

    override fun observeAll(): Nothing = unreached("observeAll")

    override suspend fun searchByPrefix(prefix: String): Nothing = unreached("searchByPrefix")

    override suspend fun findByName(name: String): Nothing = unreached("findByName")

    override suspend fun add(name: String): Nothing = unreached("add")

    override fun observeTagIdleStats(limit: Int): Nothing = unreached("observeTagIdleStats")
}

/** No identity here reaches the session repository. */
private class UnreachedSessionRepository : SessionRepository {

    override fun observeActive(): Nothing = unreached("observeActive")

    override fun observeAnyActiveSession(): Nothing = unreached("observeAnyActiveSession")

    override fun observeActiveSessionWithStats(): Nothing =
        unreached("observeActiveSessionWithStats")

    override suspend fun getAnyActiveSession(): Nothing = unreached("getAnyActiveSession")

    override suspend fun getActiveSessionProgress(): Nothing =
        unreached("getActiveSessionProgress")

    override suspend fun getActive(): Nothing = unreached("getActive")

    override fun pagedRecentWithStats(): Nothing = unreached("pagedRecentWithStats")

    override fun observeFinishedTimesBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Nothing = unreached("observeFinishedTimesBetween")

    override fun observeLastFinishedSession(): Nothing = unreached("observeLastFinishedSession")

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
