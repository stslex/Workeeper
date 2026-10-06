// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import androidx.sqlite.SQLiteException
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSetWrites
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetBody
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.feature.wear_bridge.PhoneMonotonicClock
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutBridgeImpl
import io.github.stslex.workeeper.feature.wear_bridge.PhoneWorkoutSnapshotBuilder
import io.github.stslex.workeeper.feature.wear_bridge.RoomWearSetMutationWriter
import io.github.stslex.workeeper.feature.wear_bridge.WatchKnownRevisions
import io.github.stslex.workeeper.feature.wear_bridge.WatchStateKey
import io.github.stslex.workeeper.feature.wear_bridge.WearMutationLeaseStore
import io.github.stslex.workeeper.feature.wear_bridge.transport.FakeNudgeLink.Companion.WATCH_A
import io.github.stslex.workeeper.feature.wear_bridge.transport.FakeNudgeLink.Companion.WATCH_B
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

/**
 * wear-live-sync.md §10.1, the notifier and the key flow's handshake half: the real database and the
 * real bridge, a fake link, the notifier's timing on virtual time. The bridge's key recording (D7)
 * and its tests come with the bridge change (§6.4); here the known revisions are written directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class PhoneChangeNotifierTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var database: AppDatabase
    private var closed = false
    private val known = WatchKnownRevisions()
    private val link = FakeNudgeLink()
    private val logger = SignalLogger()

    /** The real bridge, recording into the same known revisions the notifier reads (D7). */
    private val bridge by lazy {
        PhoneWorkoutBridgeImpl(
            database = database,
            transition = env.transition,
            snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
            leaseStore = WearMutationLeaseStore(env.transition),
            clock = PhoneMonotonicClock { 0L },
            mutationWriter = RoomWearSetMutationWriter(database),
            knownRevisions = known,
            externalSetWrites = ExternalSetWrites(),
        )
    }

    @BeforeEach
    fun setUp() {
        env = RepositoryTestEnv()
        database = env.rawDatabase()
        runBlocking { prepareWearSyncStorage(database, rotateDatabaseEpoch = false) }
    }

    @AfterEach
    fun tearDown() {
        if (!closed) env.close()
    }

    // region key flow

    @Test
    fun `a granting handshake through the real bridge produces no new distinct key`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = KeyTap()
        backgroundScope.launch {
            database.wearSyncDao.activeWearKeys().onEach(tap::record).distinctUntilChanged().collect { }
        }
        awaitKeys(tap) { it.isNotEmpty() }
        val baseline = tap.snapshot().single()
        val generationBefore = leaseGeneration(seed)

        grantingHandshake()
        assertTrue(leaseGeneration(seed) > generationBefore, "the handshake wrote the lease generation")
        awaitKeys(tap) { it.size >= 2 }

        assertTrue(tap.snapshot().all { it == baseline }, "Room re-ran the query and found the same key")
    }

    // endregion

    // region notifier

    @Test
    fun `arming with an active session sends nothing`() = runTest {
        database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()

        assertEquals(emptyList<String>(), link.signals, "the generation's first value is a baseline")
    }

    @Test
    fun `one set write sends exactly one signal, CHANGE_SETTLE_MS after it`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        writeSet(seed, position = 0)
        awaitDistinct(tap, 2)
        advanceTimeBy(CHANGE_SETTLE_MS - 1)
        runCurrent()
        assertEquals(emptyList<String>(), link.signals)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(WATCH_A), link.signals)

        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()
        assertEquals(listOf(WATCH_A), link.signals, "one change, one signal")
    }

    /** D15 (§10.1): both times are literals, so the test pins the value itself, not the constant. */
    @Test
    fun `one set write sends no signal 149 ms after it and exactly one 150 ms after it`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        writeSet(seed, position = 0)
        awaitDistinct(tap, 2)
        val written = currentTime
        advanceTimeBy(written + 149 - currentTime)
        runCurrent()
        assertEquals(emptyList<String>(), link.signals, "149 ms after the write the change still settles")
        advanceTimeBy(written + 150 - currentTime)
        runCurrent()
        assertEquals(listOf(WATCH_A), link.signals, "150 ms after the write exactly one signal went out")
    }

    @Test
    fun `three writes within 100 ms send one signal`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        writeSet(seed, position = 0)
        awaitDistinct(tap, 2)
        advanceTimeBy(BURST_STEP_MS)
        writeSet(seed, position = 1)
        awaitDistinct(tap, 3)
        advanceTimeBy(BURST_STEP_MS)
        writeSet(seed, position = 0, reps = 6)
        awaitDistinct(tap, 4)
        advanceTimeBy(PAST_ONE_SIGNAL_MS * 2)
        runCurrent()

        assertEquals(listOf(WATCH_A), link.signals)
    }

    @Test
    fun `a write inside the minimum interval is sent once at its end`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        writeSet(seed, position = 0)
        awaitDistinct(tap, 2)
        advanceTimeBy(CHANGE_SETTLE_MS)
        runCurrent()
        assertEquals(1, link.signals.size, "the first signal goes out after the settle time")

        // CHANGE_SETTLE_MS into the minimum interval that started with that signal.
        advanceTimeBy(CHANGE_SETTLE_MS)
        writeSet(seed, position = 1)
        awaitDistinct(tap, 3)
        advanceTimeBy(CHANGE_MIN_INTERVAL_MS - CHANGE_SETTLE_MS - 1)
        runCurrent()
        assertEquals(1, link.signals.size, "nothing inside the minimum interval")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, link.signals.size, "the change goes out at the interval's end")
    }

    @Test
    fun `a granting handshake through the real bridge sends nothing`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }
        val generationBefore = leaseGeneration(seed)

        grantingHandshake()
        assertTrue(leaseGeneration(seed) > generationBefore, "the handshake wrote the lease generation")
        awaitKeys(tap) { it.size >= 2 }
        advanceTimeBy(PAST_ONE_SIGNAL_MS * 2)
        runCurrent()

        assertEquals(emptyList<String>(), link.signals, "a handshake never changes the key (F2, §6.1)")
    }

    @Test
    fun `a session start and a finish each send one signal`() = runTest {
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }
        assertEquals(listOf<WatchStateKey?>(null), tap.snapshot(), "no session: the baseline key is null")

        val seed = runBlocking { database.seedActiveWorkout() }
        val started = WatchStateKey.of(seed.sessionUuid, revision(seed))
        awaitKeys(tap) { keys -> keys.last() == started }
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()
        assertEquals(1, link.signals.size, "the start, committed in several transactions, is one signal")

        runBlocking {
            val session = requireNotNull(database.sessionDao.getById(seed.sessionUuid))
            env.transition.mutate {
                database.sessionDao.update(session.copy(state = SessionStateEntity.FINISHED, finishedAt = 2))
            }
        }
        awaitKeys(tap) { keys -> keys.last() == null }
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()

        assertEquals(listOf(WATCH_A, WATCH_A), link.signals)
    }

    @Test
    fun `a failing link is logged by class and the next change still signals`() = runTest {
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }
        link.failNextLookup = IllegalStateException("Play services unavailable for $WATCH_A")

        writeSet(seed, position = 0)
        awaitDistinct(tap, 2)
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()
        assertEquals(emptyList<String>(), link.signals)

        writeSet(seed, position = 1)
        awaitDistinct(tap, 3)
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()
        assertEquals(listOf(WATCH_A), link.signals, "no retry; the next change signals again")

        val lines = logger.lines.toList()
        assertTrue(
            lines.any { it.startsWith("signal -> IllegalStateException: 0 reachable, 0 signalled, ") },
            "the failure is logged by class: $lines",
        )
        assertTrue(lines.any { it.startsWith("signal -> ok: 1 reachable, 1 signalled, ") }, "$lines")
        val leaks = lines.filter { line -> WATCH_A in line || "Play services" in line || UUID.containsMatchIn(line) }
        assertEquals(emptyList<String>(), leaks, "logs carry no node id, message or identifier")
    }

    @Test
    fun `a watch that already holds the changed key is not signalled`() = runTest {
        link.watches = listOf(WATCH_A, WATCH_B)
        val seed = database.seedActiveWorkout()
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        writeSet(seed, position = 0)
        awaitDistinct(tap, 2)
        // Recorded after the change arrived and before the signal: known keys are read when it is sent.
        known.record(WATCH_A, tap.snapshot().last())
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()

        assertEquals(listOf(WATCH_B), link.signals)
    }

    @Test
    fun `an applied watch command signals nothing back to the watch that wrote it`() = runTest {
        database.seedActiveWorkout()
        val shown = grantingHandshake(WATCH_A)
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        val response = completeShownSet(WATCH_A, shown)
        assertEquals(CompleteCommandOutcome.Applied, response.outcome)
        awaitDistinct(tap, 2)
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()

        assertEquals(emptyList<String>(), link.signals, "the bridge recorded what it answered the writer (D7)")
    }

    @Test
    fun `an applied watch command signals a second watch that holds an older state`() = runTest {
        link.watches = listOf(WATCH_A, WATCH_B)
        database.seedActiveWorkout()
        // B first: every handshake retires the other node's lease (F5), so A's must be the last.
        grantingHandshake(WATCH_B)
        val shown = grantingHandshake(WATCH_A)
        val tap = startNotifier()
        awaitKeys(tap) { it.isNotEmpty() }

        val response = completeShownSet(WATCH_A, shown)
        assertEquals(CompleteCommandOutcome.Applied, response.outcome)
        awaitDistinct(tap, 2)
        advanceTimeBy(PAST_ONE_SIGNAL_MS)
        runCurrent()

        assertEquals(listOf(WATCH_B), link.signals)
    }

    @Test
    fun `a key flow that throws is logged by class, ends the notifier and escapes nowhere`() = runTest {
        val escaped = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, failure -> escaped += failure }
        val failure = SQLiteException("connection pool is closed")
        val keys = flow<WatchStateKey?> {
            emit(null)
            throw failure
        }

        val notifier = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() + handler).launch {
            signalChanges(keys, known, link, logger)
        }
        advanceUntilIdle()

        assertTrue(notifier.isCompleted && !notifier.isCancelled, "the notifier ended normally")
        assertEquals(emptyList<Throwable>(), escaped, "nothing reached the scope's exception handler (D11)")
        assertEquals(listOf("signal stopped: ${failure::class.simpleName}"), logger.lines.toList())
    }

    /**
     * The same failure while the collector is busy: inside its minimum interval after a signal. The
     * conflated chain then fails downstream too, and `catch` rethrows instead of handling (D11).
     */
    @Test
    fun `a key flow that throws during the minimum interval still escapes nowhere`() = runTest {
        val escaped = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, failure -> escaped += failure }
        val failure = SQLiteException("connection pool is closed")
        val changed = WatchStateKey.of(Uuid.random(), revision = 2)
        val keys = flow {
            emit(null)
            emit(changed)
            delay(CHANGE_SETTLE_MS + MID_INTERVAL_MS)
            throw failure
        }

        val notifier = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() + handler).launch {
            signalChanges(keys, known, link, logger)
        }
        advanceUntilIdle()

        assertEquals(listOf(WATCH_A), link.signals, "the change was signalled before the query failed")
        assertTrue(notifier.isCompleted && !notifier.isCancelled, "the notifier ended normally")
        assertEquals(emptyList<Throwable>(), escaped, "nothing reached the scope's exception handler (D11)")
        assertEquals("signal stopped: ${failure::class.simpleName}", logger.lines.last())
    }

    /**
     * Not a mutation target (§10.1): Room may throw or stay suspended when the database closes under
     * the query. The outcome is printed into the test report, and nothing escapes either way.
     */
    @Test
    fun `closing the real database under a running notifier lets nothing escape`() = runTest {
        database.seedActiveWorkout()
        val escaped = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, failure -> escaped += failure }
        val notifier = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() + handler).launch {
            PhoneChangeNotifier(database.wearSyncDao, known, link).run()
        }
        runCurrent()

        closed = true
        env.close()
        val deadline = System.nanoTime() + CLOSE_OBSERVATION_NANOS
        while (System.nanoTime() < deadline && notifier.isActive) {
            advanceTimeBy(PAST_ONE_SIGNAL_MS)
            runCurrent()
            Thread.sleep(CLOSE_POLL_MS)
        }
        val outcome = if (notifier.isActive) "stayed suspended" else "ended (the query failed and was caught)"
        println("WEAR-SIGNAL closing the database under the notifier: $outcome")
        notifier.cancel()
        runCurrent()

        assertEquals(emptyList<Throwable>(), escaped, "nothing reached the scope's exception handler (D11)")
        assertEquals(emptyList<String>(), link.signals)
    }

    // endregion

    private fun TestScope.startNotifier(): KeyTap {
        val tap = KeyTap()
        backgroundScope.launch {
            signalChanges(database.wearSyncDao.activeWearKeys().onEach(tap::record), known, link, logger)
        }
        return tap
    }

    private fun TestScope.awaitDistinct(tap: KeyTap, count: Int) =
        awaitKeys(tap) { keys -> keys.distinct().size >= count }

    /**
     * GUARD: database calls block instead of suspending. While the test body is suspended, runTest
     * advances virtual time to the next delayed task, which would fire the notifier's debounce early.
     */
    private fun writeSet(seed: SignalSeed, position: Int, reps: Int = 5) = runBlocking {
        env.transition.mutate {
            database.setDao.upsertByTarget(
                uuid = Uuid.random(),
                performedExerciseUuid = seed.performedUuid,
                position = position,
                reps = reps,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            )
        }
    }

    private fun leaseGeneration(seed: SignalSeed): Long = runBlocking {
        requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)).leaseGeneration
    }

    private fun revision(seed: SignalSeed): Long = runBlocking {
        requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)).revision
    }

    /** A handshake through the real bridge that grants authority: the only kind that writes (F2). */
    private fun grantingHandshake(node: String = WATCH_A): ActiveWorkoutSnapshotResponse = runBlocking {
        val response = bridge.getActiveWorkout(
            node,
            GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random()),
        )
        val payload = response.snapshot.payload as SnapshotPayload.ActiveWithTarget
        assertInstanceOf(MutationAuthority.Granted::class.java, payload.mutationAuthority, "the handshake granted")
        response
    }

    /** The watch completes the set its last handshake showed, through the real bridge. */
    private fun completeShownSet(node: String, shown: ActiveWorkoutSnapshotResponse): CompleteCurrentSetResponse =
        runBlocking {
            val active = shown.snapshot.payload as SnapshotPayload.ActiveWithTarget
            val authority = active.mutationAuthority as MutationAuthority.Granted
            bridge.completeCurrentSet(
                node,
                CompleteCurrentSetRequest(
                    schemaVersion = WearProtocol.SCHEMA_VERSION,
                    correlationId = CanonicalUuid.random(),
                    commandId = CanonicalUuid.random(),
                    databaseEpoch = shown.snapshot.databaseEpoch,
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
                ),
            )
        }

    private companion object {
        /** Past the settle time and one minimum interval: any signal a change causes has gone out. */
        const val PAST_ONE_SIGNAL_MS = CHANGE_SETTLE_MS + CHANGE_MIN_INTERVAL_MS + 1
        const val BURST_STEP_MS = 50L

        /** Well inside CHANGE_MIN_INTERVAL_MS after the signal. */
        const val MID_INTERVAL_MS = 100L
        const val CLOSE_OBSERVATION_NANOS = 2_000_000_000L
        const val CLOSE_POLL_MS = 20L
        val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-")
    }
}
