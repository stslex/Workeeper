// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSessionWrites
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandRequest
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandResponse
import io.github.stslex.workeeper.core.wear.protocol.WatchDecodeResult
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.feature.wear_bridge.PhoneMonotonicClock
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutBridge
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutBridgeImpl
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutSnapshotBuilder
import io.github.stslex.workeeper.feature.wear_bridge.RoomWearSetMutationWriter
import io.github.stslex.workeeper.feature.wear_bridge.RoomWearSkipMutationWriter
import io.github.stslex.workeeper.feature.wear_bridge.WatchKnownRevisions
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeDeps
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkDepsHolder
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeWorkLease
import io.github.stslex.workeeper.feature.wear_bridge.WearMutationLeaseStore
import io.github.stslex.workeeper.feature.wear_bridge.WearMutationWriters
import io.github.stslex.workeeper.feature.wear_bridge.completeTargetBody
import io.github.stslex.workeeper.feature.wear_bridge.toWireUuid
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * wear-paired-transport.md §10.1: the handler end to end on the in-memory database with the real
 * bridge. Only bytes cross the handler, exactly as they cross the Data Layer.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class PhoneWearRpcEndToEndTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var database: AppDatabase
    private lateinit var bridge: PhoneWorkoutBridge

    @BeforeEach
    fun setUp() {
        env = RepositoryTestEnv()
        database = env.rawDatabase()
        runBlocking { prepareWearSyncStorage(database, rotateDatabaseEpoch = false) }
        bridge = PhoneWorkoutBridgeImpl(
            database = database,
            transition = env.transition,
            snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
            leaseStore = WearMutationLeaseStore(env.transition),
            clock = PhoneMonotonicClock { 0L },
            writers = WearMutationWriters(
                RoomWearSetMutationWriter(database),
                RoomWearSkipMutationWriter(database),
            ),
            knownRevisions = WatchKnownRevisions(),
            externalSessionWrites = ExternalSessionWrites(),
        )
    }

    @AfterEach
    fun tearDown() = env.close()

    @Test
    fun `handshake bytes, then a complete command, then its replay write exactly one set`() = runTest {
        val performed = seedSession(plans = listOf(plan(weight = 100.0, reps = 5), plan(weight = 100.0, reps = 5)))
        val handler = PhoneWearRpcHandler(holder = SingleGenerationHolder(bridge), admissionScope = backgroundScope)

        val snapshot = handshake(handler)
        val active = snapshot.snapshot.payload as SnapshotPayload.Active
        val commandBytes = WearProtocolCodec.encode(
            command(snapshot, active.completeTargetBody().copy(reps = 7, weightHundredthsKg = 10_250)),
        )

        val applied = handler.handle(SOURCE_NODE, commandBytes)
        val replayed = handler.handle(SOURCE_NODE, commandBytes)

        val response = (WearProtocolCodec.decodeForWatch(applied) as WatchDecodeResult.Success).envelope
            as WatchCommandResponse
        assertEquals(CommandOutcome.Applied, response.outcome)
        assertArrayEquals(applied, replayed, "the same attempt replayed gets the same response")
        assertEquals(1, database.setDao.countByPerformedExercise(performed), "exactly one written set")
    }

    @Test
    fun `handshake bytes, then a skip command, then its replay skip the exercise once`() = runTest {
        val performed = seedSession(plans = listOf(plan(weight = 100.0, reps = 5)))
        val handler = PhoneWearRpcHandler(holder = SingleGenerationHolder(bridge), admissionScope = backgroundScope)

        val snapshot = handshake(handler)
        val commandBytes = WearProtocolCodec.encode(
            command(snapshot, WatchCommandBody.SetExerciseSkipped(performed.toWireUuid(), skipped = true)),
        )

        val applied = handler.handle(SOURCE_NODE, commandBytes)
        val replayed = handler.handle(SOURCE_NODE, commandBytes)

        val response = (WearProtocolCodec.decodeForWatch(applied) as WatchDecodeResult.Success).envelope
            as WatchCommandResponse
        assertEquals(CommandOutcome.Applied, response.outcome)
        val after = response.replacement.payload as SnapshotPayload.Active
        assertEquals(true, after.exercises.single().skipped, "the answer's outline shows the skip")
        assertArrayEquals(applied, replayed, "the same attempt replayed gets the same response")
        assertEquals(true, database.performedExerciseDao.getAll().single { it.uuid == performed }.skipped)
        assertEquals(0, database.setDao.countByPerformedExercise(performed), "a skip writes no set")
    }

    private suspend fun handshake(handler: PhoneWearRpcHandler): ActiveWorkoutSnapshotResponse {
        val snapshotBytes = handler.handle(
            SOURCE_NODE,
            WearProtocolCodec.encode(GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random())),
        )
        return (WearProtocolCodec.decodeForWatch(snapshotBytes) as WatchDecodeResult.Success).envelope
            as ActiveWorkoutSnapshotResponse
    }

    private fun command(snapshot: ActiveWorkoutSnapshotResponse, body: WatchCommandBody): WatchCommandRequest {
        val active = snapshot.snapshot.payload as SnapshotPayload.Active
        val grant = active.mutationAuthority as MutationAuthority.Granted
        return WatchCommandRequest(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = CanonicalUuid.random(),
            commandId = CanonicalUuid.random(),
            databaseEpoch = snapshot.snapshot.databaseEpoch,
            sessionUuid = active.sessionUuid,
            sessionRevision = active.sessionRevision,
            mutationLeaseId = grant.mutationLeaseId,
            mutationLeaseGeneration = grant.mutationLeaseGeneration,
            body = body,
        )
    }

    private suspend fun seedSession(plans: List<PlanSetDataModel>): kotlin.uuid.Uuid {
        val training = TrainingEntity(
            name = "Strength",
            description = null,
            isAdhoc = false,
            archived = false,
            createdAt = 1,
            archivedAt = null,
        )
        database.trainingDao.insert(training)
        val exercise = ExerciseEntity(
            name = "Deadlift",
            type = ExerciseTypeEntity.WEIGHTED,
            description = null,
            imagePath = null,
            archived = false,
            createdAt = 1,
            archivedAt = null,
            lastAdhocSets = null,
        )
        database.exerciseDao.insert(exercise)
        database.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = training.uuid,
                    exerciseUuid = exercise.uuid,
                    position = 0,
                    planSets = PlanSetsConverter.toJson(plans),
                ),
            ),
        )
        val session = SessionEntity(
            trainingUuid = training.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1,
            finishedAt = null,
        )
        database.sessionDao.insert(session)
        val performed = PerformedExerciseEntity(
            sessionUuid = session.uuid,
            exerciseUuid = exercise.uuid,
            position = 0,
            skipped = false,
        )
        database.performedExerciseDao.insert(listOf(performed))
        return performed.uuid
    }

    private fun plan(weight: Double, reps: Int) =
        PlanSetDataModel(weight = weight, reps = reps, type = SetTypeDataModel.WORK)

    /** One generation, admitted at once; each call's lease is released by the handler. */
    private class SingleGenerationHolder(private val bridge: PhoneWorkoutBridge) : WearBridgeWorkDepsHolder {
        override suspend fun awaitWearBridgeWorkLease(): WearBridgeWorkLease = object : WearBridgeWorkLease {
            override val deps: WearBridgeDeps = object : WearBridgeDeps {
                override val phoneWorkoutBridge: PhoneWorkoutBridge = bridge
            }

            override fun release() = Unit
        }
    }

    private companion object {
        const val SOURCE_NODE = "synthetic-watch-node"
    }
}
