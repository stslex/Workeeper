// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `every snapshot table mutation advances the active revision and clears its receipt`() =
        runTest {
            val epoch = prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
            val seed = insertActiveSession()

            suspend fun assertInvalidates(
                label: String,
                mutation: suspend () -> Unit,
            ) {
                val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
                assertEquals(
                    1,
                    database.wearSyncDao.storeReceipt(
                        sessionUuid = seed.sessionUuid,
                        commandId = Uuid.random().toString(),
                        attemptFingerprint = ByteArray(FINGERPRINT_SIZE_BYTES) { 0x5a },
                        databaseEpoch = epoch,
                        revision = before.revision,
                    ),
                    "$label receipt setup",
                )

                mutation()

                val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
                assertTrue(after.revision > before.revision, "$label did not advance revision")
                assertNull(after.receiptCommandId, "$label retained command receipt")
                assertNull(after.receiptAttemptFingerprint, "$label retained fingerprint receipt")
                assertNull(after.receiptDatabaseEpoch, "$label retained receipt epoch")
                assertNull(after.receiptRevision, "$label retained receipt revision")
            }

            val set = SetEntity(
                performedExerciseUuid = seed.performedUuid,
                position = 0,
                reps = 8,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            )
            assertInvalidates("set insert") { database.setDao.insert(set) }
            assertInvalidates("set update") { database.setDao.update(set.copy(reps = 9)) }
            assertInvalidates("set delete") { database.setDao.delete(set.uuid) }

            val secondExercise = ExerciseEntity(
                name = "Press",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 2,
                archivedAt = null,
                lastAdhocSets = null,
            )
            database.exerciseDao.insert(secondExercise)
            val secondPerformed = PerformedExerciseEntity(
                sessionUuid = seed.sessionUuid,
                exerciseUuid = secondExercise.uuid,
                position = 1,
                skipped = false,
            )
            assertInvalidates("performed insert") {
                database.performedExerciseDao.insert(secondPerformed)
            }
            assertInvalidates("performed skipped update") {
                database.performedExerciseDao.setSkipped(secondPerformed.uuid, skipped = true)
            }
            assertInvalidates("performed delete") {
                database.performedExerciseDao.deleteByUuid(secondPerformed.uuid)
            }

            val plan = TrainingExerciseEntity(
                trainingUuid = seed.trainingUuid,
                exerciseUuid = seed.exerciseUuid,
                position = 0,
                planSets = PlanSetsConverter.toJson(
                    listOf(PlanSetDataModel(100.0, 5, SetTypeDataModel.WORK)),
                ),
            )
            assertInvalidates("plan attach") { database.trainingExerciseDao.insert(plan) }
            assertInvalidates("plan value update") {
                database.trainingExerciseDao.updatePlanSets(
                    seed.trainingUuid,
                    seed.exerciseUuid,
                    PlanSetsConverter.toJson(
                        listOf(PlanSetDataModel(101.25, 6, SetTypeDataModel.FAILURE)),
                    ),
                )
            }
            assertInvalidates("plan detach") {
                database.trainingExerciseDao.deleteByTrainingAndExercise(
                    seed.trainingUuid,
                    seed.exerciseUuid,
                )
            }

            assertInvalidates("training name") {
                database.trainingDao.updateName(seed.trainingUuid, "Strength 2")
            }
            assertInvalidates("exercise name") {
                val exercise = requireNotNull(database.exerciseDao.getById(seed.exerciseUuid))
                database.exerciseDao.update(exercise.copy(name = "Deadlift 2"))
            }
            assertInvalidates("exercise type") {
                database.exerciseDao.updateType(seed.exerciseUuid, ExerciseTypeEntity.WEIGHTLESS)
            }
            assertInvalidates("ad-hoc plan") {
                database.exerciseDao.updateLastAdhocSets(
                    seed.exerciseUuid,
                    PlanSetsConverter.toJson(
                        listOf(PlanSetDataModel(null, 10, SetTypeDataModel.WORK)),
                    ),
                )
            }
            assertInvalidates("session identity state") {
                val session = requireNotNull(database.sessionDao.getById(seed.sessionUuid))
                database.sessionDao.update(session.copy(startedAt = session.startedAt + 1))
            }
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
        return Seed(
            sessionUuid = sessionUuid,
            performedUuid = performedUuid,
            trainingUuid = trainingUuid,
            exerciseUuid = exerciseUuid,
        )
    }

    private data class Seed(
        val sessionUuid: Uuid,
        val performedUuid: Uuid,
        val trainingUuid: Uuid,
        val exerciseUuid: Uuid,
    )

    private companion object {
        const val FINGERPRINT_SIZE_BYTES = 34
    }
}
