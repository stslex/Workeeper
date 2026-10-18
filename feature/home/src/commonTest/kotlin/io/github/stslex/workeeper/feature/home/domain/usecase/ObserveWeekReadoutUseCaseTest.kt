// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class ObserveWeekReadoutUseCaseTest {

    private val zone = TimeZone.of("Europe/Moscow")
    private val sessionRepository = WindowSessionRepository()
    private val useCase = ObserveWeekReadoutUseCaseImpl(
        sessionRepository = sessionRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.toInstant(zone).toEpochMilliseconds()

    // 2026-08-05 is a Wednesday; its ISO week is Mon 08-03 00:00 .. Mon 08-10 00:00.
    private val wednesdayNoon = millisOf(LocalDateTime(2026, 8, 5, 12, 0))
    private val weekStart = millisOf(LocalDateTime(2026, 8, 3, 0, 0))
    private val weekEnd = millisOf(LocalDateTime(2026, 8, 10, 0, 0))

    @Test
    fun `queries the repository with the Monday-first week window around now`() = runTest {
        sessionRepository.answer(weekStart, weekEnd, flowOf(emptyList()))

        val readout = useCase(nowMillis = wednesdayNoon, timeZone = zone).first()

        assertEquals(WeekReadoutDomain(sessionsThisWeek = 0, trainedDayIndexes = emptySet()), readout)
    }

    @Test
    fun `counts every session and collapses same-day sessions into one trained day`() = runTest {
        sessionRepository.answer(
            weekStart,
            weekEnd,
            flowOf(
                listOf(
                    millisOf(LocalDateTime(2026, 8, 3, 9, 0)),
                    millisOf(LocalDateTime(2026, 8, 3, 19, 0)),
                    millisOf(LocalDateTime(2026, 8, 5, 8, 30)),
                    millisOf(LocalDateTime(2026, 8, 9, 23, 15)),
                ),
            ),
        )

        val readout = useCase(nowMillis = wednesdayNoon, timeZone = zone).first()

        assertEquals(4, readout.sessionsThisWeek)
        assertEquals(setOf(0, 2, 6), readout.trainedDayIndexes)
    }

    /**
     * The strict double: [observeFinishedTimesBetween] answers only the identity's exact window;
     * any other window and every other member fail fast.
     */
    private class WindowSessionRepository : SessionRepository {

        private var window: Pair<Long, Long>? = null
        private var times: Flow<List<Long>>? = null

        fun answer(startInclusive: Long, endExclusive: Long, flow: Flow<List<Long>>) {
            window = startInclusive to endExclusive
            times = flow
        }

        override fun observeFinishedTimesBetween(
            startInclusive: Long,
            endExclusive: Long,
        ): Flow<List<Long>> {
            check(window == startInclusive to endExclusive) {
                "unexpected window $startInclusive..$endExclusive; answered $window"
            }
            return checkNotNull(times)
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

        private fun unreached(member: String): Nothing =
            error("$member must not be used by ObserveWeekReadoutUseCase")
    }
}
