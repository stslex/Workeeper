// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.wear.ActiveWearKeyRow
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

/**
 * wear-live-sync.md §10.1, ASM-7: the phone's change key follows every set write the phone's own
 * repository makes. The set triggers raise `session_table.wear_revision` from inside another table's
 * write, and Room must re-run the `session_table` query for it; if it did not, the change signal
 * would miss every set (SPEC §14 Q5). The handshake half of the key-flow test needs the bridge and
 * lives in `:feature:wear-bridge`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class WearActiveKeyFlowDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: SetRepositoryImpl

    @BeforeEach
    fun setUp() {
        env = RepositoryTestEnv()
        repository = SetRepositoryImpl(
            dao = env.setDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun tearDown() = env.close()

    @Test
    fun `a set insert, update and delete each produce a new distinct key for the active session`() = runTest {
        val training = env.seedTraining()
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(sessionUuid = session.uuid, exerciseUuid = env.seedExercise().uuid)
        prepareWearSyncStorage(env.rawDatabase(), rotateDatabaseEpoch = false)
        val keys = Channel<ActiveWearKeyRow?>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.Default) {
            env.rawDatabase().wearSyncDao.observeActiveWearKey().distinctUntilChanged().collect { keys.send(it) }
        }
        val baseline = requireNotNull(keys.next()) { "an active session has a key" }
        assertEquals(session.uuid, baseline.sessionUuid)

        val set = SetsDataModel(
            uuid = Uuid.random().toString(),
            reps = 5,
            weight = 100.0,
            position = 0,
            type = SetsDataType.WORK,
        )
        repository.insert(performed.uuid.toString(), set)
        val afterInsert = keys.nextAfterWrite(session.uuid, baseline.revision)

        repository.update(performed.uuid.toString(), set.copy(reps = 6, weight = 102.5))
        val afterUpdate = keys.nextAfterWrite(session.uuid, afterInsert.revision)

        repository.delete(set.uuid)
        keys.nextAfterWrite(session.uuid, afterUpdate.revision)
    }

    /**
     * The next distinct key, which must be this session at a higher revision. The stored revision is
     * read first, so a RED here tells a trigger that did not fire from a query Room did not re-run.
     */
    private suspend fun Channel<ActiveWearKeyRow?>.nextAfterWrite(session: Uuid, previous: Long): ActiveWearKeyRow {
        val stored = requireNotNull(env.rawDatabase().wearSyncDao.getSessionSync(session)).revision
        assertTrue(stored > previous, "the set trigger raised the stored revision ($previous -> $stored)")
        val next = requireNotNull(next()) { "the key flow emitted no key after the write" }
        assertEquals(session, next.sessionUuid)
        assertEquals(stored, next.revision, "the key flow re-emitted the raised revision")
        return next
    }

    /** Waits in real time: Room re-runs the query on its own threads, outside virtual time. */
    private suspend fun Channel<ActiveWearKeyRow?>.next(): ActiveWearKeyRow? =
        withContext(Dispatchers.Default) { withTimeout(KEY_TIMEOUT_MS) { receive() } }

    private companion object {
        const val KEY_TIMEOUT_MS = 10_000L
    }
}
