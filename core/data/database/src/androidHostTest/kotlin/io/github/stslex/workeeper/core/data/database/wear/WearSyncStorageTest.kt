// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class WearSyncStorageTest : BaseDatabaseTest() {

    @BeforeEach
    fun setup() = initDb()

    @AfterEach
    fun teardown() = clearDb()

    @Test
    fun `fresh database gets one stable random epoch and active writes advance revision`() = runTest {
        val first = prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
        val second = prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
        Uuid.parse(first)
        assertEquals(first, second)

        val seed = insertActiveSession()
        val afterPerformedInsert = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        database.setDao.insert(
            SetEntity(
                performedExerciseUuid = seed.performedUuid,
                position = 0,
                reps = 8,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
        )
        val afterSetInsert = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        assertEquals(afterPerformedInsert.revision + 1, afterSetInsert.revision)
    }

    @Test
    fun `restore rotation changes epoch clears receipt and preserves monotonic counters`() = runTest {
        val originalEpoch = prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
        val seed = insertActiveSession()
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        assertEquals(1, database.wearSyncDao.incrementLeaseGeneration(seed.sessionUuid, before.revision))
        val fingerprint = ByteArray(FINGERPRINT_SIZE_BYTES) { index -> index.toByte() }
        assertEquals(
            1,
            database.wearSyncDao.storeReceipt(
                sessionUuid = seed.sessionUuid,
                commandId = Uuid.random().toString(),
                attemptFingerprint = fingerprint,
                databaseEpoch = originalEpoch,
                revision = before.revision,
            ),
        )
        val stored = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        assertArrayEquals(fingerprint, stored.receiptAttemptFingerprint)

        val rotatedEpoch = prepareWearSyncStorage(database, rotateDatabaseEpoch = true)
        val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertNotEquals(originalEpoch, rotatedEpoch)
        Uuid.parse(rotatedEpoch)
        assertEquals(stored.revision, after.revision)
        assertEquals(stored.leaseGeneration, after.leaseGeneration)
        assertNull(after.receiptCommandId)
        assertNull(after.receiptAttemptFingerprint)
        assertNull(after.receiptDatabaseEpoch)
        assertNull(after.receiptRevision)
    }

    private suspend fun insertActiveSession(): Seed {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        val sessionUuid = Uuid.random()
        val performedUuid = Uuid.random()
        database.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Strength",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 1,
                archivedAt = null,
            ),
        )
        database.exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = "Deadlift",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 1,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        database.sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 1,
                finishedAt = null,
            ),
        )
        database.performedExerciseDao.insert(
            PerformedExerciseEntity(
                uuid = performedUuid,
                sessionUuid = sessionUuid,
                exerciseUuid = exerciseUuid,
                position = 0,
                skipped = false,
            ),
        )
        return Seed(sessionUuid, performedUuid)
    }

    private data class Seed(
        val sessionUuid: Uuid,
        val performedUuid: Uuid,
    )

    private companion object {
        const val FINGERPRINT_SIZE_BYTES = 34
    }
}
