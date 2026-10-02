// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import androidx.sqlite.SQLiteException
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSetWrite
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSetWrites
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetBody
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.feature.wear_bridge.transport.SignalSeed
import io.github.stslex.workeeper.feature.wear_bridge.transport.seedActiveWorkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.atomic.AtomicInteger

/**
 * wear-live-sync.md §6.4 and §10.1, the bridge's two changes: every returned response records what
 * its node now shows, and an Applied command publishes the set it wrote, only after the transaction.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class PhoneWorkoutBridgeSignalTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var database: AppDatabase
    private lateinit var transition: RecordingTransition
    private lateinit var leaseStore: WearMutationLeaseStore
    private lateinit var collector: Job
    private val known = WatchKnownRevisions()
    private val externalSetWrites = ExternalSetWrites()
    private val published = mutableListOf<Published>()

    @BeforeEach
    fun setUp() {
        env = RepositoryTestEnv()
        database = env.rawDatabase()
        transition = RecordingTransition(env.transition)
        leaseStore = WearMutationLeaseStore(transition)
        runBlocking { prepareWearSyncStorage(database, rotateDatabaseEpoch = false) }
        // Unconfined and undispatched: each publication runs this collector inside publish(), so it
        // records whether a bridge transaction was open at that moment.
        collector = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            externalSetWrites.writes.collect { write -> published += Published(write, transition.inTransaction) }
        }
    }

    @AfterEach
    fun tearDown() {
        collector.cancel()
        env.close()
    }

    // region recorded keys

    @Test
    fun `a handshake records its snapshot's key for the source node only`() = runTest {
        val seed = database.seedActiveWorkout()

        val response = newBridge().handshake(NODE_A)

        assertEquals(WatchStateKey.of(seed.sessionUuid, revision(seed)), response.snapshot.key())
        assertTrue(known.holds(NODE_A, response.snapshot.key()))
        assertFalse(known.holds(NODE_B, response.snapshot.key()), "another node was answered nothing")
    }

    @Test
    fun `a handshake with no active session records no session`() = runTest {
        newBridge().handshake(NODE_A)

        assertTrue(known.holds(NODE_A, null))
    }

    @Test
    fun `the key is recorded from the read-only refresh, never from the prepared response`() = runTest {
        val seed = database.seedActiveWorkout()
        val prepared = WatchStateKey.of(seed.sessionUuid, revision(seed))
        // A phone edit wins the post-commit gap: the lease publication fails and the bridge answers
        // with the current state, read only (PhoneWorkoutBridgeImplTest's publication race).
        transition.beforeFirstInvoke = {
            env.transition.mutate {
                database.trainingExerciseDao.updatePlanSets(
                    trainingUuid = seed.trainingUuid,
                    exerciseUuid = seed.exerciseUuid,
                    planSets = PlanSetsConverter.toJson(
                        listOf(PlanSetDataModel(weight = 101.25, reps = 6, type = SetTypeDataModel.WORK)),
                    ),
                )
            }
        }

        val response = newBridge().handshake(NODE_A)

        val active = response.snapshot.payload as SnapshotPayload.ActiveWithTarget
        assertTrue(active.mutationAuthority is MutationAuthority.Unavailable, "the read-only refresh answered")
        assertNotEquals(prepared, response.snapshot.key(), "the phone edit advanced the revision")
        assertTrue(known.holds(NODE_A, response.snapshot.key()), "the returned response is what the node shows")
        assertFalse(known.holds(NODE_A, prepared), "the prepared response never left the bridge")
    }

    @Test
    fun `command, replay, rejection and protocol rejection responses each record their replacement`() = runTest {
        val seed = database.seedActiveWorkout()
        val bridge = newBridge()
        val command = bridge.handshake(NODE_A).command()

        val applied = bridge.completeCurrentSet(NODE_A, command)
        assertEquals(CompleteCommandOutcome.Applied, applied.outcome)
        assertEquals(WatchStateKey.of(seed.sessionUuid, revision(seed)), applied.replacement.key())
        assertTrue(known.holds(NODE_A, applied.replacement.key()), "the command's replacement")

        known.record(NODE_A, null)
        val replay = bridge.completeCurrentSet(NODE_A, command.copy(correlationId = CanonicalUuid.random()))
        assertEquals(CompleteCommandOutcome.AlreadyApplied, replay.outcome)
        assertTrue(known.holds(NODE_A, replay.replacement.key()), "the replay's replacement")

        known.record(NODE_A, null)
        val stale = bridge.completeCurrentSet(NODE_A, command.another())
        assertNotEquals(CompleteCommandOutcome.Applied, stale.outcome)
        assertTrue(known.holds(NODE_A, stale.replacement.key()), "the rejection's replacement")

        known.record(NODE_A, null)
        val rejected = bridge.protocolRejected(
            NODE_A,
            CompleteCommandRouting(
                schemaVersion = WearProtocol.SCHEMA_VERSION,
                correlationId = CanonicalUuid.random(),
                commandId = CanonicalUuid.random(),
                databaseEpoch = command.databaseEpoch,
                sessionUuid = command.sessionUuid,
                sessionRevision = command.sessionRevision,
                mutationLeaseId = command.mutationLeaseId,
                mutationLeaseGeneration = command.mutationLeaseGeneration,
            ),
            ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
        )
        assertTrue(known.holds(NODE_A, rejected.replacement.key()), "the protocol rejection's replacement")
        assertFalse(known.holds(NODE_B, rejected.replacement.key()))
    }

    // endregion

    // region published writes

    @Test
    fun `an Applied command publishes exactly the written set, after the transaction`() = runTest {
        val seed = database.seedActiveWorkout()
        val bridge = newBridge()
        val command = bridge.handshake(NODE_A).command()

        val response = bridge.completeCurrentSet(NODE_A, command)

        assertEquals(CompleteCommandOutcome.Applied, response.outcome)
        val expected = ExternalSetWrite(
            sessionUuid = seed.sessionUuid.toString(),
            performedExerciseUuid = seed.performedUuid.toString(),
            position = command.body.setPosition,
            weight = command.body.weightHundredthsKg?.div(HUNDREDTHS_PER_KG),
            reps = command.body.reps,
            type = SetsDataType.WORK,
        )
        assertEquals(listOf(Published(expected, inTransaction = false)), published)
        val row = requireNotNull(
            database.setDao.getByPerformedAndPosition(seed.performedUuid, command.body.setPosition),
        )
        assertEquals(expected.reps, row.reps, "the published values are the written ones")
        assertEquals(expected.weight, row.weight)
    }

    @Test
    fun `an exact replay publishes nothing`() = runTest {
        database.seedActiveWorkout()
        val bridge = newBridge()
        val command = bridge.handshake(NODE_A).command()
        bridge.completeCurrentSet(NODE_A, command)
        published.clear()

        val replay = bridge.completeCurrentSet(NODE_A, command.copy(correlationId = CanonicalUuid.random()))

        assertEquals(CompleteCommandOutcome.AlreadyApplied, replay.outcome)
        assertEquals(emptyList<Published>(), published)
    }

    @Test
    fun `rejections publish nothing`() = runTest {
        database.seedActiveWorkout()
        val bridge = newBridge()
        val command = bridge.handshake(NODE_A).command()

        val stale = bridge.completeCurrentSet(NODE_A, command.another(revisionDelta = 1))
        val moved = bridge.completeCurrentSet(
            NODE_A,
            bridge.handshake(NODE_A).command().let { fresh ->
                fresh.another().copy(body = fresh.body.copy(setPosition = fresh.body.setPosition + 1))
            },
        )

        assertNotEquals(CompleteCommandOutcome.Applied, stale.outcome)
        assertNotEquals(CompleteCommandOutcome.Applied, moved.outcome)
        assertEquals(emptyList<Published>(), published)
    }

    @Test
    fun `the write-failure path publishes nothing`() = runTest {
        database.seedActiveWorkout()
        val bridge = newBridge(writer = { throw SQLiteException("synthetic busy") })

        val failed = bridge.completeCurrentSet(NODE_A, bridge.handshake(NODE_A).command())

        assertEquals(CompleteCommandOutcome.RetryableTemporaryFailure, failed.outcome)
        assertEquals(emptyList<Published>(), published)
    }

    @Test
    fun `the bridge maps every set type into the live-workout data type`() {
        SetTypeEntity.entries.forEach { entity ->
            assertEquals(entity, entity.toSetsDataType().toEntity(), "$entity")
        }
        assertEquals(SetsDataType.entries.toSet(), SetTypeEntity.entries.map { it.toSetsDataType() }.toSet())
    }

    // endregion

    private fun newBridge(writer: WearSetMutationWriter = RoomWearSetMutationWriter(database)) =
        PhoneWorkoutBridgeImpl(
            database = database,
            transition = transition,
            snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
            leaseStore = leaseStore,
            clock = PhoneMonotonicClock { 0L },
            mutationWriter = writer,
            knownRevisions = known,
            externalSetWrites = externalSetWrites,
        )

    private suspend fun PhoneWorkoutBridgeImpl.handshake(node: String): ActiveWorkoutSnapshotResponse =
        getActiveWorkout(node, GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random()))

    private fun ActiveWorkoutSnapshotResponse.command(): CompleteCurrentSetRequest {
        val active = snapshot.payload as SnapshotPayload.ActiveWithTarget
        val authority = active.mutationAuthority as MutationAuthority.Granted
        return CompleteCurrentSetRequest(
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
                reps = active.target.reps,
                weightHundredthsKg = active.target.weightHundredthsKg,
                exerciseType = active.target.exerciseType,
                setType = active.target.setType,
            ),
        )
    }

    /** A different command under the same lease: new ids, optionally another revision. */
    private fun CompleteCurrentSetRequest.another(revisionDelta: Long = 0) = copy(
        correlationId = CanonicalUuid.random(),
        commandId = CanonicalUuid.random(),
        sessionRevision = sessionRevision + revisionDelta,
    )

    private fun SnapshotData.key(): WatchStateKey? = watchStateKey()

    private suspend fun revision(seed: SignalSeed): Long =
        requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)).revision

    private data class Published(val write: ExternalSetWrite, val inTransaction: Boolean)

    /**
     * Records whether a bridge transaction is open (the wrapper pattern of PhoneWorkoutBridgeImplTest's
     * OneShotInvokeRaceTransition), and can run one phone write before the first read-only transaction.
     */
    private class RecordingTransition(private val delegate: DbTransitionRunner) : DbTransitionRunner {
        private val depth = AtomicInteger(0)
        val inTransaction: Boolean get() = depth.get() > 0
        var beforeFirstInvoke: (suspend () -> Unit)? = null

        override suspend fun <T> invoke(block: suspend CoroutineScope.() -> T): T {
            beforeFirstInvoke?.let { race ->
                beforeFirstInvoke = null
                race()
            }
            return inside { delegate(block) }
        }

        override suspend fun <T> mutate(block: suspend CoroutineScope.() -> T): T = inside { delegate.mutate(block) }

        override fun addAfterMutationCommitListener(listener: () -> Unit) {
            delegate.addAfterMutationCommitListener(listener)
        }

        private suspend fun <T> inside(run: suspend () -> T): T {
            depth.incrementAndGet()
            return try {
                run()
            } finally {
                depth.decrementAndGet()
            }
        }
    }

    private companion object {
        const val NODE_A = "watch-node-a-7f3a"
        const val NODE_B = "watch-node-b-7f3a"
        const val HUNDREDTHS_PER_KG = 100.0
    }
}
