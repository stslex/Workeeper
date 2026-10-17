// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.ExercisePlanWrite
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
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
internal class ObserveStartCardReadoutUseCaseTest {

    private val zone = TimeZone.of("Europe/Moscow")
    private val sessionRepository = LastSessionRepository()
    private val tagRepository = IdleStatsTagRepository()
    private val trainingRepository = ForgottenTrainingRepository()
    private val observeWeekReadoutUseCase = AnsweringWeekReadoutUseCase()
    private val useCase = ObserveStartCardReadoutUseCaseImpl(
        sessionRepository = sessionRepository,
        tagRepository = tagRepository,
        trainingRepository = trainingRepository,
        observeWeekReadoutUseCase = observeWeekReadoutUseCase,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.toInstant(zone).toEpochMilliseconds()

    private val now = millisOf(LocalDateTime(2026, 8, 7, 12, 0))

    private fun lastSession(finishedAt: Long) = SessionRepository.LastFinishedSession(
        sessionUuid = "s1",
        finishedAt = finishedAt,
        trainingName = "Ноги",
        isAdhoc = false,
    )

    private fun template(lastSessionAt: Long?) = TrainingListItem(
        data = TrainingDataModel(
            uuid = "t1",
            name = "Спина и бицепс",
            description = null,
            isAdhoc = false,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
        ),
        exerciseCount = 6,
        lastSessionAt = lastSessionAt,
        isActive = false,
        activeSessionUuid = null,
        activeSessionStartedAt = null,
    )

    @Test
    fun `WEEK with history wraps the week readout`() = runTest {
        val week = WeekReadoutDomain(sessionsThisWeek = 2, trainedDayIndexes = setOf(0, 4))
        observeWeekReadoutUseCase.answer(now, zone, flowOf(week))
        sessionRepository.lastFinished = flowOf(lastSession(finishedAt = now - 1000L))

        val readout = useCase(StartCardModeDomain.WEEK, now, zone).first()

        assertEquals(StartCardReadoutDomain.Week(week), readout)
    }

    @Test
    fun `WEEK with no session ever is the mode's own empty state even at count zero`() = runTest {
        observeWeekReadoutUseCase.answer(
            now,
            zone,
            flowOf(WeekReadoutDomain(sessionsThisWeek = 0, trainedDayIndexes = emptySet())),
        )
        sessionRepository.lastFinished = flowOf(null)

        val readout = useCase(StartCardModeDomain.WEEK, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoSessions, readout)
    }

    @Test
    fun `DAYS_SINCE_LAST measures calendar days and anchors on the last session`() = runTest {
        // Finished late on Aug 4th; noon on the 7th is 3 calendar days later.
        sessionRepository.lastFinished =
            flowOf(lastSession(finishedAt = millisOf(LocalDateTime(2026, 8, 4, 23, 30))))

        val readout = useCase(StartCardModeDomain.DAYS_SINCE_LAST, now, zone).first()

        assertEquals(
            StartCardReadoutDomain.DaysSince(
                daysSince = 3,
                lastTrainingName = "Ноги",
                lastIsAdhoc = false,
                lastFinishedAt = millisOf(LocalDateTime(2026, 8, 4, 23, 30)),
            ),
            readout,
        )
    }

    @Test
    fun `DAYS_SINCE_LAST with no session ever is NoSessions`() = runTest {
        sessionRepository.lastFinished = flowOf(null)

        val readout = useCase(StartCardModeDomain.DAYS_SINCE_LAST, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoSessions, readout)
    }

    @Test
    fun `LAGGING_GROUPS maps idle stats to calendar days in repository order`() = runTest {
        tagRepository.answer(
            3,
            flowOf(
                listOf(
                    TagRepository.TagIdleStat(
                        name = "спина",
                        lastTrainedAt = millisOf(LocalDateTime(2026, 7, 24, 10, 0)),
                    ),
                    TagRepository.TagIdleStat(
                        name = "ноги",
                        lastTrainedAt = millisOf(LocalDateTime(2026, 8, 5, 10, 0)),
                    ),
                ),
            ),
        )

        val readout = useCase(StartCardModeDomain.LAGGING_GROUPS, now, zone).first()

        assertEquals(
            StartCardReadoutDomain.TagIdle(
                entries = listOf(
                    StartCardReadoutDomain.TagIdle.Entry(name = "спина", daysIdle = 14),
                    StartCardReadoutDomain.TagIdle.Entry(name = "ноги", daysIdle = 2),
                ),
            ),
            readout,
        )
    }

    @Test
    fun `LAGGING_GROUPS with no tagged history is NoTaggedHistory`() = runTest {
        tagRepository.answer(3, flowOf(emptyList()))

        val readout = useCase(StartCardModeDomain.LAGGING_GROUPS, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoTaggedHistory, readout)
    }

    @Test
    fun `FORGOTTEN_TRAINING carries days idle and composition`() = runTest {
        trainingRepository.mostForgotten =
            flowOf(template(lastSessionAt = millisOf(LocalDateTime(2026, 7, 17, 9, 0))))

        val readout = useCase(StartCardModeDomain.FORGOTTEN_TRAINING, now, zone).first()

        assertEquals(
            StartCardReadoutDomain.Forgotten(
                trainingUuid = "t1",
                trainingName = "Спина и бицепс",
                daysIdle = 21,
                exerciseCount = 6,
            ),
            readout,
        )
    }

    @Test
    fun `FORGOTTEN_TRAINING never run keeps daysIdle null — HD1's most forgotten thing`() =
        runTest {
            trainingRepository.mostForgotten = flowOf(template(lastSessionAt = null))

            val readout = useCase(StartCardModeDomain.FORGOTTEN_TRAINING, now, zone).first()

            assertEquals(
                StartCardReadoutDomain.Forgotten(
                    trainingUuid = "t1",
                    trainingName = "Спина и бицепс",
                    daysIdle = null,
                    exerciseCount = 6,
                ),
                readout,
            )
        }

    @Test
    fun `FORGOTTEN_TRAINING with no templates is NoTemplates`() = runTest {
        trainingRepository.mostForgotten = flowOf(null)

        val readout = useCase(StartCardModeDomain.FORGOTTEN_TRAINING, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoTemplates, readout)
    }

    /** The strict week double: answers only the identity's exact (now, zone). */
    private class AnsweringWeekReadoutUseCase : ObserveWeekReadoutUseCase {

        private var arguments: Pair<Long, TimeZone>? = null
        private var week: Flow<WeekReadoutDomain>? = null

        fun answer(nowMillis: Long, timeZone: TimeZone, flow: Flow<WeekReadoutDomain>) {
            arguments = nowMillis to timeZone
            week = flow
        }

        override fun invoke(nowMillis: Long, timeZone: TimeZone): Flow<WeekReadoutDomain> {
            check(arguments == nowMillis to timeZone) {
                "unexpected week arguments ($nowMillis, $timeZone); answered $arguments"
            }
            return checkNotNull(week)
        }
    }

    /** Answers [observeLastFinishedSession] with the identity's flow; every other member fails fast. */
    private class LastSessionRepository : SessionRepository {

        var lastFinished: Flow<SessionRepository.LastFinishedSession?>? = null

        override fun observeLastFinishedSession(): Flow<SessionRepository.LastFinishedSession?> =
            checkNotNull(lastFinished) { "the identity set up no last-session flow" }

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

    /** Answers [observeTagIdleStats] for the identity's exact limit; every other call fails fast. */
    private class IdleStatsTagRepository : TagRepository {

        private var limit: Int? = null
        private var stats: Flow<List<TagRepository.TagIdleStat>>? = null

        fun answer(limit: Int, flow: Flow<List<TagRepository.TagIdleStat>>) {
            this.limit = limit
            stats = flow
        }

        override fun observeTagIdleStats(limit: Int): Flow<List<TagRepository.TagIdleStat>> {
            check(this.limit == limit) { "unexpected idle-stats limit $limit; answered ${this.limit}" }
            return checkNotNull(stats)
        }

        override fun observeAll(): Nothing = unreached("observeAll")

        override suspend fun searchByPrefix(prefix: String): Nothing = unreached("searchByPrefix")

        override suspend fun findByName(name: String): Nothing = unreached("findByName")

        override suspend fun add(name: String): Nothing = unreached("add")
    }

    /** Answers [observeMostForgottenTemplate] with the identity's flow; every other member fails fast. */
    private class ForgottenTrainingRepository : TrainingRepository {

        var mostForgotten: Flow<TrainingListItem?>? = null

        override fun observeMostForgottenTemplate(): Flow<TrainingListItem?> =
            checkNotNull(mostForgotten) { "the identity set up no forgotten-template flow" }

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

        override suspend fun bulkArchive(uuids: Set<String>): Nothing = unreached("bulkArchive")

        override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing =
            unreached("bulkPermanentDelete")

        override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing =
            unreached("canBulkPermanentDelete")
    }

    private companion object {

        fun unreached(member: String): Nothing =
            error("$member must not be used by ObserveStartCardReadoutUseCase")
    }
}
