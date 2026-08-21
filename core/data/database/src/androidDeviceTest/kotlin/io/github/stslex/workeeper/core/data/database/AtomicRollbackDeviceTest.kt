// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
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
 * ATOMICITY ORACLE — real device, real FILE-BACKED database. Answers: does the
 * `DbTransitionRunner` transaction primitive roll back writes on a throw, for the two
 * shapes production actually uses inside `transition { }`?
 *
 * ⚠️ THIS TEST MUST STAY androidDeviceTest + FILE-BACKED. Do NOT "simplify" it onto
 * Robolectric or an in-memory DB. Robolectric's shadow SQLite gave a **false NEGATIVE on this exact
 * measurement twice** — a Robolectric variant of this probe reported shape B (concurrent
 * `async` children) as NOT rolling back, when the real device proves it does. Robolectric
 * is not a valid oracle for transaction/async-child rollback semantics here; the device is.
 * See documentation/tech-debt.md → "Robolectric is not a valid oracle for transaction
 * semantics".
 *
 * Four runs, mirroring the two real production shapes with their REAL helpers (imported from
 * core:core — not hand-rolled):
 *  - CONTROL  : `withTransaction { insert; insert; throw }`            → must roll back.
 *  - NEGATIVE : write outside the transaction survives an unrelated rollback → must survive.
 *  - SHAPE A  : finishSessionAtomic's shape — sequential `asyncScope { write }` writers.
 *  - SHAPE B  : clearWeightsFromAllPlans' shape — concurrent `coroutineScope { async { write } }`.
 *
 * The transaction runner is built inline exactly as production's `DbTransitionRunner`
 * (`withTransaction { coroutineScope { block() } }`).
 *
 * Each rollback run makes THREE assertions, not one, so none of them can pass vacuously:
 *  1. the throwable escaping `transition { }` is the intentional [RollbackTrigger]
 *     (see `assertRollbackTrigger`) — proves the body ran to its throw rather than failing on entry;
 *  2. the rows are readable INSIDE the transaction just before the throw (`readNames`) — proves the
 *     writes actually reached the connection;
 *  3. the table is empty (or holds only the outside write) afterwards — proves the rollback.
 * (1) + (2) are what turn (3) from "empty, for any reason" into "written, then rolled back".
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
        // Real file-backed DB on the device — NOT in-memory, so transaction/connection
        // semantics match production, not Robolectric's shadow SQLite.
        database = Room.databaseBuilder<AppDatabase>(context, PROBE_DB)
            .setDriver(AndroidSQLiteDriver())
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
     * One-shot read of the tag names written by the current test, ordered by name.
     *
     * Called from INSIDE `transition { }`: Room 3's connection pool confines the writer connection to
     * the coroutine context, so a read issued within the transaction reuses that same connection and
     * therefore sees the still-uncommitted rows. That is what turns "table empty afterwards" into
     * "written, then rolled back".
     */
    private suspend fun readNames(prefix: String): List<String> =
        tagDao.searchByPrefix(prefix).map { it.name }

    /**
     * Vacuity gate. Without it every test here would also pass when `transition { }` throws BEFORE the
     * first insert (e.g. a Room upgrade making `useWriterConnection` fail on entry from `runBlocking`) —
     * `runCatching` swallows that too, and an empty table satisfies the "rolled back" assertion.
     * Asserting the escaping throwable is the intentional [RollbackTrigger] pins that the transaction
     * body actually ran to its throw.
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
