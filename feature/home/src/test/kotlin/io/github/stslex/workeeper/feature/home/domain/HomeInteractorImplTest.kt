// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.usecase.ObserveWeekReadoutUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HomeInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val sessionConflictResolver = mockk<SessionConflictResolver>(relaxed = true)
    private val observeWeekReadoutUseCase = mockk<ObserveWeekReadoutUseCase>(relaxed = true)
    private val interactor = HomeInteractorImpl(
        sessionRepository = sessionRepository,
        trainingRepository = trainingRepository,
        sessionConflictResolver = sessionConflictResolver,
        observeWeekReadoutUseCase = observeWeekReadoutUseCase,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `observeActiveSession maps repository ActiveSessionWithStats to domain`() = runTest {
        val row = SessionRepository.ActiveSessionWithStats(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            isAdhoc = false,
            startedAt = 123L,
            totalCount = 5,
            doneCount = 2,
        )
        every { sessionRepository.observeActiveSessionWithStats() } returns flowOf(row)

        val mapped = interactor.observeActiveSession().first()

        assertEquals(
            ActiveSessionWithStatsDomain(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Push Day",
                isAdhoc = false,
                startedAt = 123L,
                totalCount = 5,
                doneCount = 2,
            ),
            mapped,
        )
    }

    @Test
    fun `observeWeekReadout delegates to the use case with the caller's now`() = runTest {
        val readout = WeekReadoutDomain(sessionsThisWeek = 2, trainedDayIndexes = setOf(0, 3))
        every { observeWeekReadoutUseCase(nowMillis = 42L) } returns flowOf(readout)

        val emitted = interactor.observeWeekReadout(nowMillis = 42L).first()

        assertEquals(readout, emitted)
        verify(exactly = 1) { observeWeekReadoutUseCase(nowMillis = 42L) }
    }
}
