// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SessionConflictResolverTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val resolver = SessionConflictResolver(sessionRepository)

    @Test
    fun `resolve returns ProceedFresh when no active session exists`() = runTest {
        coEvery { sessionRepository.getAnyActiveSession() } returns null

        val result = resolver.resolve(requestedTrainingUuid = "training-1")

        assertEquals(SessionConflictResolver.Resolution.ProceedFresh, result)
    }

    @Test
    fun `resolve returns SilentResume when active session is for the requested training`() = runTest {
        val active = ActiveSessionInfo(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            startedAt = 0L,
        )
        coEvery { sessionRepository.getAnyActiveSession() } returns active

        val result = resolver.resolve(requestedTrainingUuid = "training-1")

        assertEquals(SessionConflictResolver.Resolution.SilentResume("session-1"), result)
    }

    @Test
    fun `resolve returns NeedsUserChoice when active session is for a different training`() = runTest {
        val active = ActiveSessionInfo(
            sessionUuid = "session-2",
            trainingUuid = "training-other",
            startedAt = 0L,
        )
        coEvery { sessionRepository.getAnyActiveSession() } returns active

        val result = resolver.resolve(requestedTrainingUuid = "training-1")

        assertEquals(SessionConflictResolver.Resolution.NeedsUserChoice(active), result)
    }
}
