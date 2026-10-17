// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.SetTypeDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PastSessionInteractorImplTest {

    private val sessionRepository = RecordingSessionRepository()
    private val setRepository = RecordingSetRepository()
    private val personalRecordRepository = StubPersonalRecordRepository()
    private val interactor = PastSessionInteractorImpl(
        sessionRepository = sessionRepository,
        setRepository = setRepository,
        personalRecordRepository = personalRecordRepository,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `observeDetailWithPrs emits null when session is missing`() = runTest {
        sessionRepository.detail = null

        val result = interactor.observeDetailWithPrs("session-1").first()

        assertNull(result)
    }

    @Test
    fun `observeDetailWithPrs combines fresh detail with PR set uuids`() = runTest {
        val performed = PerformedExerciseDetailDataModel(
            performedExerciseUuid = "performed-1",
            exerciseUuid = "exercise-1",
            exerciseName = "Bench",
            exerciseType = ExerciseTypeDataModel.WEIGHTED,
            position = 0,
            skipped = false,
            sets = emptyList(),
        )
        val detail = SessionDetailDataModel(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            isAdhoc = false,
            startedAt = 0L,
            finishedAt = 1_000L,
            exercises = listOf(performed),
        )
        sessionRepository.detail = detail
        personalRecordRepository.prSetUuidFlows[setOf("exercise-1")] = flowOf(setOf("set-prev"))

        val result = interactor.observeDetailWithPrs("session-1").first()

        assertEquals("session-1", result?.detail?.sessionUuid)
        assertEquals("Push Day", result?.detail?.trainingName)
        assertTrue(result?.prSetUuids?.contains("set-prev") == true)
    }

    @Test
    fun `updateSet maps domain SetDomain to data SetsDataModel and forwards`() = runTest {
        interactor.updateSet(
            performedExerciseUuid = "performed-1",
            set = SetDomain(
                uuid = "set-1",
                reps = 8,
                weight = 100.0,
                type = SetTypeDomain.WORK,
                position = 2,
            ),
        )

        assertEquals(1, setRepository.updates.count { it.first == "performed-1" })
        val captured = setRepository.updates.last().second
        assertEquals("set-1", captured.uuid)
        assertEquals(8, captured.reps)
        assertEquals(100.0, captured.weight)
        assertEquals(SetsDataType.WORK, captured.type)
    }

    @Test
    fun `deleteSession forwards to SessionRepository deleteSession`() = runTest {
        interactor.deleteSession("session-1")

        assertEquals(1, sessionRepository.deletedSessions.count { it == "session-1" })
    }
}

/**
 * Answers [getSessionDetail] with [detail] for `session-1` (null for any other uuid, as the relaxed
 * mock did) and records [deleteSession]; every other member fails fast.
 */
private class RecordingSessionRepository : SessionRepository {

    var detail: SessionDetailDataModel? = null
    val deletedSessions = mutableListOf<String>()

    override suspend fun getSessionDetail(sessionUuid: String): SessionDetailDataModel? =
        if (sessionUuid == "session-1") detail else null

    override suspend fun deleteSession(uuid: String) {
        deletedSessions.add(uuid)
    }

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

/** Records each [update] as `(performedExerciseUuid, set)`; every other member fails fast. */
private class RecordingSetRepository : SetRepository {

    val updates = mutableListOf<Pair<String, SetsDataModel>>()

    override suspend fun update(performedExerciseUuid: String, set: SetsDataModel) {
        updates.add(performedExerciseUuid to set)
    }

    override suspend fun getByPerformedExercise(performedExerciseUuid: String): Nothing =
        unreached("getByPerformedExercise")

    override suspend fun getByPerformedExercises(performedExerciseUuids: List<String>): Nothing =
        unreached("getByPerformedExercises")

    @Deprecated(message = "Mirrors SetRepository.getLastFinishedSet, which no identity reaches.")
    override suspend fun getLastFinishedSet(exerciseUuid: String): Nothing =
        unreached("getLastFinishedSet")

    override suspend fun insert(performedExerciseUuid: String, set: SetsDataModel): Nothing =
        unreached("insert")

    override suspend fun reorderSets(
        performedExerciseUuid: String,
        orderedSetUuids: List<String>,
    ): Nothing = unreached("reorderSets")

    override suspend fun delete(uuid: String): Nothing = unreached("delete")

    override suspend fun upsert(
        performedExerciseUuid: String,
        position: Int,
        weight: Double?,
        reps: Int,
        type: SetsDataType,
    ): Nothing = unreached("upsert")

    override suspend fun deleteByPerformedAndPosition(
        performedExerciseUuid: String,
        position: Int,
    ): Nothing = unreached("deleteByPerformedAndPosition")

    override suspend fun deleteAllForPerformedExercise(performedExerciseUuid: String): Nothing =
        unreached("deleteAllForPerformedExercise")

    override suspend fun hasAnyForPerformed(performedExerciseUuid: String): Nothing =
        unreached("hasAnyForPerformed")

    override suspend fun countByPerformedExercise(performedExerciseUuid: String): Nothing =
        unreached("countByPerformedExercise")
}

/** Answers [observePrSetUuids] with the flow an identity set up for its key; the rest fail fast. */
private class StubPersonalRecordRepository : PersonalRecordRepository {

    val prSetUuidFlows = mutableMapOf<Set<String>, Flow<Set<String>>>()

    override fun observePrSetUuids(exerciseUuids: Set<String>): Flow<Set<String>> =
        prSetUuidFlows[exerciseUuids] ?: unreached("observePrSetUuids($exerciseUuids)")

    override suspend fun getPersonalRecord(exerciseUuid: String): Nothing =
        unreached("getPersonalRecord")

    override fun observePersonalRecord(exerciseUuid: String): Nothing =
        unreached("observePersonalRecord")

    override fun observePersonalRecordsBatch(exerciseUuids: Set<String>): Nothing =
        unreached("observePersonalRecordsBatch")
}

private fun unreached(member: String): Nothing =
    error("$member must not be used by PastSessionInteractorImplTest")
