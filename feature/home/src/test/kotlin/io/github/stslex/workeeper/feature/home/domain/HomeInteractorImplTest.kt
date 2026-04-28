// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HomeInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val interactor = HomeInteractorImpl(
        sessionRepository = sessionRepository,
        trainingRepository = trainingRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `observeActiveSession forwards repository ActiveSessionWithStats`() = runTest {
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

        assertEquals(row, mapped)
    }
}
