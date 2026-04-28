// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PastSessionInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val setRepository = mockk<SetRepository>(relaxed = true)
    private val interactor = PastSessionInteractorImpl(
        sessionRepository = sessionRepository,
        setRepository = setRepository,
    )

    @Test
    fun `getSessionDetail forwards repository result`() = runTest {
        val detail = SessionDetailDataModel(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            isAdhoc = false,
            startedAt = 0L,
            finishedAt = 1_000L,
            exercises = emptyList(),
        )
        coEvery { sessionRepository.getSessionDetail("session-1") } returns detail

        val result = interactor.getSessionDetail("session-1")

        assertEquals(detail, result)
    }

    @Test
    fun `updateSet forwards to SetRepository update`() = runTest {
        val set = SetsDataModel(
            uuid = "set-1",
            reps = 8,
            weight = 100.0,
            type = SetsDataType.WORK,
        )

        interactor.updateSet(
            performedExerciseUuid = "performed-1",
            position = 2,
            set = set,
        )

        coVerify(exactly = 1) {
            setRepository.update(
                performedExerciseUuid = "performed-1",
                position = 2,
                set = set,
            )
        }
    }

    @Test
    fun `deleteSession forwards to SessionRepository deleteSession`() = runTest {
        interactor.deleteSession("session-1")

        coVerify(exactly = 1) { sessionRepository.deleteSession("session-1") }
    }
}
