// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.session

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class SessionDaoObserveAnyActiveSessionTest : BaseDatabaseTest() {

    private val sessionDao get() = database.sessionDao
    private val trainingDao get() = database.trainingDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `observeAnyActiveSession emits null when no in-progress session exists`() = runTest {
        val result = sessionDao.observeAnyActiveSession().first()

        assertNull(result)
    }

    @Test
    fun `observeAnyActiveSession emits the active row when one exists`() = runTest {
        val trainingUuid = Uuid.random()
        val sessionUuid = Uuid.random()
        seedTraining(trainingUuid, "Push Day")
        sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 1_000L,
                finishedAt = null,
            ),
        )

        val result = sessionDao.observeAnyActiveSession().first()

        assertEquals(sessionUuid, result?.uuid)
        assertEquals(trainingUuid, result?.trainingUuid)
        assertEquals(1_000L, result?.startedAt)
    }

    @Test
    fun `observeAnyActiveSession ignores finished sessions`() = runTest {
        val trainingUuid = Uuid.random()
        seedTraining(trainingUuid, "Push Day")
        sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 2_000L,
            ),
        )

        val result = sessionDao.observeAnyActiveSession().first()

        assertNull(result)
    }

    private suspend fun seedTraining(uuid: Uuid, name: String) {
        trainingDao.insert(
            TrainingEntity(
                uuid = uuid,
                name = name,
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
    }
}
