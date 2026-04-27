// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HomeInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val interactor = HomeInteractorImpl(
        sessionRepository = sessionRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `observeActiveSession maps repository model to Home ActiveSessionInfo`() = runTest {
        every { sessionRepository.observeActiveSessionWithStats() } returns flowOf(
            SessionRepository.ActiveSessionWithStats(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Push Day",
                isAdhoc = false,
                startedAt = 123L,
                totalCount = 5,
                doneCount = 2,
            ),
        )

        val mapped = interactor.observeActiveSession().first()

        assertEquals("session-1", mapped?.sessionUuid)
        assertEquals("training-1", mapped?.trainingUuid)
        assertEquals("Push Day", mapped?.trainingName)
        assertEquals(123L, mapped?.startedAt)
        assertEquals(5, mapped?.totalCount)
        assertEquals(2, mapped?.doneCount)
        assertTrue(mapped?.elapsedDurationLabel?.isNotBlank() == true)
    }
}
