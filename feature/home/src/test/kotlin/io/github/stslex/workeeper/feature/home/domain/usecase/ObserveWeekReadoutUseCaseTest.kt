// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.usecase

import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
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
internal class ObserveWeekReadoutUseCaseTest {

    private val zone = TimeZone.of("Europe/Moscow")
    private val sessionRepository = mockk<SessionRepository>()
    private val useCase = ObserveWeekReadoutUseCase(
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
        every {
            sessionRepository.observeFinishedTimesBetween(weekStart, weekEnd)
        } returns flowOf(emptyList())

        val readout = useCase(nowMillis = wednesdayNoon, timeZone = zone).first()

        assertEquals(WeekReadoutDomain(sessionsThisWeek = 0, trainedDayIndexes = emptySet()), readout)
    }

    @Test
    fun `counts every session and collapses same-day sessions into one trained day`() = runTest {
        every {
            sessionRepository.observeFinishedTimesBetween(weekStart, weekEnd)
        } returns flowOf(
            listOf(
                millisOf(LocalDateTime(2026, 8, 3, 9, 0)),
                millisOf(LocalDateTime(2026, 8, 3, 19, 0)),
                millisOf(LocalDateTime(2026, 8, 5, 8, 30)),
                millisOf(LocalDateTime(2026, 8, 9, 23, 15)),
            ),
        )

        val readout = useCase(nowMillis = wednesdayNoon, timeZone = zone).first()

        assertEquals(4, readout.sessionsThisWeek)
        assertEquals(setOf(0, 2, 6), readout.trainedDayIndexes)
    }
}
