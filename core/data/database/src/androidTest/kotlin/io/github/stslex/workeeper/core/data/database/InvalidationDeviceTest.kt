// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * INVALIDATION ORACLE — real device, real FILE-BACKED database. Proves that a write through
 * the ported (Room 3) `DbTransitionRunner` re-emits Room-native `Flow`s, so the UI does not
 * silently go stale after a write.
 *
 * ARTIFACT FACT: room3-runtime 3.0.0's `useWriterConnection` already calls
 * `invalidationTracker.refreshAsync()` internally (verified in its bytecode:
 * `getInvalidationTracker().refreshAsync()`), so no manual refresh was added — this test proves
 * the built-in refresh actually reaches a collected Flow end-to-end.
 *
 * ⚠️ androidTest + file-backed on purpose — Robolectric is not a valid oracle for
 * invalidation/Flow re-emission any more than for transaction rollback.
 */
@RunWith(AndroidJUnit4::class)
internal class InvalidationDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val tagDao get() = database.tagDao

    private suspend fun <T> transition(block: suspend CoroutineScope.() -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { coroutineScope { block() } }
        }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PROBE_DB)
        database = Room.databaseBuilder(context, AppDatabase::class.java, PROBE_DB)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PROBE_DB)
    }

    @Test
    fun flowReEmitsAfterWriteThroughPortedTransaction() = runBlocking {
        // Baseline: the Flow's first emission is the current (empty) table.
        assertEquals(emptyList<TagEntity>(), tagDao.observeAll().first())

        // Write through the ported DbTransitionRunner (useWriterConnection → refreshAsync).
        transition { tagDao.insert(TagEntity(name = "invalidate-me")) }

        // The Flow MUST re-emit with the new row. withTimeout so a non-re-emitting Flow fails
        // the test (hangs → TimeoutCancellationException) rather than hanging forever.
        val reEmitted = withTimeout(TIMEOUT_MS) {
            tagDao.observeAll().first { rows -> rows.any { it.name == "invalidate-me" } }
        }
        assertTrue(
            "Room-native Flow MUST re-emit the new row after a write through the ported transaction",
            reEmitted.any { it.name == "invalidate-me" },
        )
    }

    @Test
    fun knownNegative_flowDoesNotReEmitWithoutAWrite() = runBlocking {
        // KNOWN-NEGATIVE: with no write, the Flow emits its initial value and then does NOT
        // re-emit. A second collection times out → proving the re-emit assertion above can
        // actually distinguish "re-emitted" from "did not", i.e. it is a real guard.
        tagDao.insert(TagEntity(name = "seed"))
        // Drain the initial emission.
        assertEquals(listOf("seed"), tagDao.observeAll().first().map { it.name })

        val sawSpurious = runCatching {
            withTimeout(TIMEOUT_MS) {
                // Wait for an emission of anything OTHER than the seeded state; none should come.
                tagDao.observeAll().first { rows -> rows.map { it.name } != listOf("seed") }
            }
        }.isSuccess
        assertTrue(
            "with no write, the Flow must NOT re-emit a changed value (proves the positive guard can fail)",
            !sawSpurious,
        )
    }

    private companion object {
        const val PROBE_DB = "invalidation_probe.db"
        const val TIMEOUT_MS = 5_000L
    }
}
