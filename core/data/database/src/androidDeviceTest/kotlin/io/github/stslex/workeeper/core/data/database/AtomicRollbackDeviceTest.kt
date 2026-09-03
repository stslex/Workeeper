// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.core.coroutine.asyncForEach
import io.github.stslex.workeeper.core.core.coroutine.asyncScope
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Atomicity oracle — real device, real file-backed database: does the `DbTransitionRunner`
 * transaction primitive roll back on a throw for both production shapes? GUARD: keep it a device
 * test: Robolectric's shadow SQLite gave a false negative on this measurement twice (tech-debt.md).
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class AtomicRollbackDeviceTest {

    /** Named trigger so the intentional rollback throw is not a generic exception. */
    private class RollbackTrigger(message: String) : Exception(message)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val tagDao get() = database.tagDao

    /** Production's DbTransitionRunner shape, inline (Room 3 useWriterConnection/immediateTransaction). */
    private suspend fun <T> transition(block: suspend CoroutineScope.() -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { coroutineScope { block() } }
        }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PROBE_DB)
        // Real file-backed DB on the device — connection semantics must match production.
        database = Room.databaseBuilder<AppDatabase>(context, PROBE_DB)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PROBE_DB)
    }

    @Test
    fun control_sequentialWritesRollBackOnThrow() = runBlocking {
        var seenInside: List<String> = emptyList()
        val thrown = runCatching {
            transition {
                tagDao.insert(TagEntity(name = "ctrl-A"))
                tagDao.insert(TagEntity(name = "ctrl-B"))
                seenInside = readNames("ctrl-")
                throw RollbackTrigger("roll back control")
            }
        }.exceptionOrNull()
        assertRollbackTrigger("CONTROL", thrown)
        assertEquals(
            "CONTROL: both writes MUST be visible INSIDE the transaction before the throw",
            listOf("ctrl-A", "ctrl-B"),
            seenInside,
        )
        assertEquals(
            "CONTROL: sequential writes in a transaction MUST roll back on throw",
            emptyList<TagEntity>(),
            tagDao.observeAll().first(),
        )
    }

    @Test
    fun knownNegative_outsideWriteSurvivesUnrelatedRollback() = runBlocking {
        tagDao.insert(TagEntity(name = "outside"))
        var seenInside: List<String> = emptyList()
        val thrown = runCatching {
            transition {
                tagDao.insert(TagEntity(name = "inside-doomed"))
                seenInside = readNames("inside-")
                throw RollbackTrigger("roll back inside only")
            }
        }.exceptionOrNull()
        assertRollbackTrigger("KNOWN-NEGATIVE", thrown)
        assertEquals(
            "KNOWN-NEGATIVE: the inside write MUST be visible inside the transaction before the throw",
            listOf("inside-doomed"),
            seenInside,
        )
        assertEquals(
            "KNOWN-NEGATIVE: the outside write survives; the inside write rolls back",
            listOf("outside"),
            tagDao.observeAll().first().map { it.name },
        )
    }

    @Test
    fun shapeA_asyncScopeSequentialWritersRollBackOnThrow() = runBlocking {
        // finishSessionAtomic's exact shape: sequential asyncScope { write } writers.
        var seenInside: List<String> = emptyList()
        val thrown = runCatching {
            transition {
                val a = asyncScope { tagDao.insert(TagEntity(name = "A-1")) }
                val b = asyncScope { tagDao.insert(TagEntity(name = "A-2")) }
                val c = asyncScope { tagDao.insert(TagEntity(name = "A-3")) }
                a.await()
                b.await()
                c.await()
                seenInside = readNames("A-")
                throw RollbackTrigger("roll back shape A")
            }
        }.exceptionOrNull()
        assertRollbackTrigger("SHAPE A", thrown)
        assertEquals(
            "SHAPE A (finishSessionAtomic): all three asyncScope writes MUST land inside the transaction",
            listOf("A-1", "A-2", "A-3"),
            seenInside,
        )
        assertEquals(
            "SHAPE A (finishSessionAtomic): asyncScope writers MUST roll back on throw",
            emptyList<TagEntity>(),
            tagDao.observeAll().first(),
        )
    }

    @Test
    fun shapeB_concurrentAsyncWritersRollBackOnThrow() = runBlocking {
        // clearWeightsFromAllPlansForExercise's shape: concurrent async{} writers, and the
        // asyncForEach helper it also uses (both launch children of coroutineScope).
        var seenInside: List<String> = emptyList()
        val thrown = runCatching {
            transition {
                coroutineScope {
                    val a = async { tagDao.insert(TagEntity(name = "B-1")) }
                    val b = async { tagDao.insert(TagEntity(name = "B-2")) }
                    a.await()
                    b.await()
                }
                listOf("B-3", "B-4").asyncForEach { tagDao.insert(TagEntity(name = it)) }
                seenInside = readNames("B-")
                throw RollbackTrigger("roll back shape B")
            }
        }.exceptionOrNull()
        assertRollbackTrigger("SHAPE B", thrown)
        assertEquals(
            "SHAPE B (clearWeightsFromAllPlans): all four concurrent writes MUST land inside the transaction",
            listOf("B-1", "B-2", "B-3", "B-4"),
            seenInside,
        )
        assertEquals(
            "SHAPE B (clearWeightsFromAllPlans): concurrent async writers MUST roll back on throw",
            emptyList<TagEntity>(),
            tagDao.observeAll().first(),
        )
    }

    /**
     * One-shot read of this test's tag names, called from INSIDE `transition { }`: Room 3 confines
     * the writer connection to the coroutine context, so the read sees the still-uncommitted rows.
     */
    private suspend fun readNames(prefix: String): List<String> =
        tagDao.searchByPrefix(prefix).map { it.name }

    /**
     * Vacuity gate: without it every test would also pass when `transition { }` throws before the
     * first insert, since an empty table satisfies the "rolled back" assertion.
     */
    private fun assertRollbackTrigger(label: String, thrown: Throwable?) {
        assertTrue(
            "$label: the transaction body must run to the intentional RollbackTrigger, not fail earlier " +
                "on infrastructure. Actual escaping throwable: $thrown",
            thrown is RollbackTrigger,
        )
    }

    private companion object {
        const val PROBE_DB = "atomicity_probe.db"
    }
}
