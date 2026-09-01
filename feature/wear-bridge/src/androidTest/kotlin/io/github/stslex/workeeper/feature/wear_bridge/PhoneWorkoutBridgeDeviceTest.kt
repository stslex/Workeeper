// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetBody
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic phone authority proof under the production bundled SQLite driver. */
@Regression
@RunWith(AndroidJUnit4::class)
internal class PhoneWorkoutBridgeDeviceTest {

    private lateinit var database: AppDatabase
    private lateinit var bridge: PhoneWorkoutBridgeImpl

    private val transition = object : DbTransitionRunner {
        private val afterMutationCommitListeners = mutableListOf<() -> Unit>()

        override fun addAfterMutationCommitListener(listener: () -> Unit) {
            afterMutationCommitListeners += listener
        }

        override suspend fun <T> invoke(block: suspend CoroutineScope.() -> T): T {
            return runTransaction(block)
        }

        override suspend fun <T> mutate(block: suspend CoroutineScope.() -> T): T {
            val result = runTransaction(block)
            afterMutationCommitListeners.toList().forEach { listener ->
                runCatching { listener() }
            }
            return result
        }

        private suspend fun <T> runTransaction(block: suspend CoroutineScope.() -> T): T =
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction { coroutineScope { block() } }
            }
    }

    @Before
    fun setUp() = runBlocking {
        database = InMemoryDatabaseProvider.create(ApplicationProvider.getApplicationContext())
        prepareWearSyncStorage(database, rotateDatabaseEpoch = false)
        seedActiveWorkout()
        bridge = PhoneWorkoutBridgeImpl(
            database = database,
            transition = transition,
            snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
            leaseStore = WearMutationLeaseStore(transition),
            clock = PhoneMonotonicClock { 0L },
            mutationWriter = RoomWearSetMutationWriter(database),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun concurrent_exact_delivery_commits_one_authoritative_row_and_receipt() = runBlocking {
        val snapshot = bridge.getActiveWorkout(
            SOURCE_NODE,
            GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random()),
        ).snapshot
        val active = snapshot.payload as SnapshotPayload.ActiveWithTarget
        val authority = active.mutationAuthority as MutationAuthority.Granted
        val command = CompleteCurrentSetRequest(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = CanonicalUuid.random(),
            commandId = CanonicalUuid.random(),
            databaseEpoch = snapshot.databaseEpoch,
            sessionUuid = active.sessionUuid,
            sessionRevision = active.sessionRevision,
            mutationLeaseId = authority.mutationLeaseId,
            mutationLeaseGeneration = authority.mutationLeaseGeneration,
            body = CompleteCurrentSetBody(
                performedExerciseUuid = active.target.performedExerciseUuid,
                setPosition = active.target.setPosition,
                reps = 8,
                weightHundredthsKg = 12_525,
                exerciseType = active.target.exerciseType,
                setType = SetTypeWire.WORK,
            ),
        )

        val responses = coroutineScope {
            listOf(
                async { bridge.completeCurrentSet(SOURCE_NODE, command) },
                async { bridge.completeCurrentSet(SOURCE_NODE, command) },
            ).awaitAll()
        }

        val performedUuid = kotlin.uuid.Uuid.parse(active.target.performedExerciseUuid.value)
        val rows = database.setDao.getByPerformedExercise(performedUuid)
        val sync = database.wearSyncDao.getActiveSessionSync()
        assertEquals(responses.first(), responses.last())
        assertEquals(CompleteCommandOutcome.Applied, responses.singleOutcome())
        assertEquals(1, rows.size)
        assertEquals(8, rows.single().reps)
        assertEquals(125.25, rows.single().weight ?: Double.NaN, 0.0)
        assertNotNull(sync)
        assertEquals(command.commandId.value, sync?.receiptCommandId)
        assertEquals(sync?.revision, sync?.receiptRevision)
        assertTrue(responses.first().replacement.payload is SnapshotPayload.WorkoutComplete)
    }

    private suspend fun seedActiveWorkout() {
        val training = TrainingEntity(
            name = "Synthetic strength",
            description = null,
            isAdhoc = false,
            archived = false,
            createdAt = 1,
            archivedAt = null,
        )
        val exercise = ExerciseEntity(
            name = "Synthetic deadlift",
            type = ExerciseTypeEntity.WEIGHTED,
            description = null,
            imagePath = null,
            archived = false,
            createdAt = 1,
            archivedAt = null,
            lastAdhocSets = null,
        )
        val session = SessionEntity(
            trainingUuid = training.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1,
            finishedAt = null,
        )
        val performed = PerformedExerciseEntity(
            sessionUuid = session.uuid,
            exerciseUuid = exercise.uuid,
            position = 0,
            skipped = false,
        )
        database.trainingDao.insert(training)
        database.exerciseDao.insert(exercise)
        database.trainingExerciseDao.insert(
            TrainingExerciseEntity(
                trainingUuid = training.uuid,
                exerciseUuid = exercise.uuid,
                position = 0,
                planSets = PlanSetsConverter.toJson(
                    listOf(PlanSetDataModel(125.25, 8, SetTypeDataModel.WORK)),
                ),
            ),
        )
        database.sessionDao.insert(session)
        database.performedExerciseDao.insert(performed)
    }

    private fun List<io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse>
        .singleOutcome(): CompleteCommandOutcome = map { it.outcome }.distinct().single()

    private companion object {
        const val SOURCE_NODE: String = "synthetic-device-node"
    }
}
