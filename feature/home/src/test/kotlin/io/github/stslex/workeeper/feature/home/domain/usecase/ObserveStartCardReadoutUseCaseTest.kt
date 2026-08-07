// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class ObserveStartCardReadoutUseCaseTest {

    private val zone = TimeZone.of("Europe/Moscow")
    private val sessionRepository = mockk<SessionRepository>()
    private val tagRepository = mockk<TagRepository>()
    private val trainingRepository = mockk<TrainingRepository>()
    private val observeWeekReadoutUseCase = mockk<ObserveWeekReadoutUseCase>()
    private val useCase = ObserveStartCardReadoutUseCase(
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
        every { observeWeekReadoutUseCase(now, zone) } returns flowOf(week)
        every { sessionRepository.observeLastFinishedSession() } returns
            flowOf(lastSession(finishedAt = now - 1000L))

        val readout = useCase(StartCardModeDomain.WEEK, now, zone).first()

        assertEquals(StartCardReadoutDomain.Week(week), readout)
    }

    @Test
    fun `WEEK with no session ever is the mode's own empty state, even at count zero`() = runTest {
        every { observeWeekReadoutUseCase(now, zone) } returns
            flowOf(WeekReadoutDomain(sessionsThisWeek = 0, trainedDayIndexes = emptySet()))
        every { sessionRepository.observeLastFinishedSession() } returns flowOf(null)

        val readout = useCase(StartCardModeDomain.WEEK, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoSessions, readout)
    }

    @Test
    fun `DAYS_SINCE_LAST measures calendar days and anchors on the last session`() = runTest {
        // Finished late on Aug 4th; noon on the 7th is 3 calendar days later.
        every { sessionRepository.observeLastFinishedSession() } returns
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
        every { sessionRepository.observeLastFinishedSession() } returns flowOf(null)

        val readout = useCase(StartCardModeDomain.DAYS_SINCE_LAST, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoSessions, readout)
    }

    @Test
    fun `LAGGING_GROUPS maps idle stats to calendar days in repository order`() = runTest {
        every { tagRepository.observeTagIdleStats(3) } returns flowOf(
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
        every { tagRepository.observeTagIdleStats(3) } returns flowOf(emptyList())

        val readout = useCase(StartCardModeDomain.LAGGING_GROUPS, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoTaggedHistory, readout)
    }

    @Test
    fun `FORGOTTEN_TRAINING carries days idle and composition`() = runTest {
        every { trainingRepository.observeMostForgottenTemplate() } returns
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
            every { trainingRepository.observeMostForgottenTemplate() } returns
                flowOf(template(lastSessionAt = null))

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
        every { trainingRepository.observeMostForgottenTemplate() } returns flowOf(null)

        val readout = useCase(StartCardModeDomain.FORGOTTEN_TRAINING, now, zone).first()

        assertEquals(StartCardReadoutDomain.NoTemplates, readout)
    }
}
