// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PastSessionInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val setRepository = mockk<SetRepository>(relaxed = true)
    private val personalRecordRepository = mockk<PersonalRecordRepository>(relaxed = true)
    private val interactor = PastSessionInteractorImpl(
        sessionRepository = sessionRepository,
        setRepository = setRepository,
        personalRecordRepository = personalRecordRepository,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `observeDetailWithPrs emits null when session is missing`() = runTest {
        coEvery { sessionRepository.getSessionDetail("session-1") } returns null

        val result = interactor.observeDetailWithPrs("session-1").first()

        assertNull(result)
    }

    @Test
    fun `observeDetailWithPrs combines detail with PR map`() = runTest {
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
        val pr = PersonalRecordDataModel(
            sessionUuid = "session-prev",
            performedExerciseUuid = "performed-prev",
            setUuid = "set-prev",
            weight = 105.0,
            reps = 5,
            type = SetsDataType.WORK,
            finishedAt = 500L,
        )
        coEvery { sessionRepository.getSessionDetail("session-1") } returns detail
        every {
            personalRecordRepository.observePersonalRecords(
                mapOf("exercise-1" to ExerciseTypeDataModel.WEIGHTED),
            )
        } returns flowOf(mapOf("exercise-1" to pr))

        val result = interactor.observeDetailWithPrs("session-1").first()

        assertEquals(detail, result?.detail)
        assertEquals(pr, result?.prMap?.get("exercise-1"))
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
