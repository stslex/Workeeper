// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * JUnit rule that installs a FRESH Metro [io.github.stslex.workeeper.di.AppGraph] into
 * [MetroTestGraphHolder] for each test (App-Scope Collapse Step 6, Phase 3.3), then clears it.
 *
 * Instrumentation creates the [TestApplication] once per process, so the graph MUST be rebuilt per
 * test — otherwise a fail-fast-DB test and an in-memory-DB test running in the same process would share
 * one graph. `@Before` (via [before]) builds the graph from the two `create()` roots and installs it;
 * `@After` (via [after]) resets the holder so a leaked read between tests fails loudly, and closes the
 * per-test [AppDatabase] so its connection pool does not survive to process death.
 *
 * Declare this rule at `@Rule(order = 0)` — the OUTERMOST slot — whenever the test also has an activity
 * / compose rule. JUnit runs the lowest-ordered rule outermost, so [after] then runs LAST, after the
 * activity has been torn down and nothing can still be querying the database being closed.
 *
 * The two `create()` roots are the test-override boundary the seam is designed around
 * (`AppGraph.Factory.create(applicationContext, appDatabase, imageStorage)`):
 *  - [appDatabase] defaults to an in-memory Room instance; a test needing divergent DB behaviour (the
 *    recovery DB-free tripwire) passes its own factory.
 *  - [imageStorage] defaults to [FakeImageStorage].
 *
 * Lives in `:app:app` androidTest because [buildAppGraph] + `AppGraph` are module-`internal`.
 */
internal class MetroTestRule(
    private val appDatabaseFactory: (Context) -> AppDatabase = { ctx -> InMemoryDatabaseProvider.create(ctx) },
    private val imageStorage: () -> ImageStorage = { FakeImageStorage() },
) : ExternalResource() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var installedDatabase: AppDatabase? = null
    private var installedLifetime: AppScopeLifetime? = null

    /**
     * The [AppDatabase] instance the current test's graph was built from — the SAME `create()` root the
     * app graph derives its DAOs / repositories from. A persistence test reads it back (e.g.
     * `appDatabase.exerciseDao.getAllActive()`) to assert what a Store→repository→Room write produced.
     */
    val appDatabase: AppDatabase
        get() = installedDatabase ?: error("MetroTestRule.appDatabase read before @Before installed the graph")

    override fun before() {
        val ctx = context
        val database = appDatabaseFactory(ctx)
        installedDatabase = database
        // Per-test generation lifetime (Phase 5, spec §8.2): cancelled in after() so graph-owned
        // collectors (dialog reactor, auth mirror, export scope) cannot leak across tests in the
        // shared instrumentation process.
        val lifetime = AppScopeLifetime()
        installedLifetime = lifetime
        MetroTestGraphHolder.install(
            buildAppGraph(
                applicationContext = ctx,
                appDatabase = database,
                imageStorage = imageStorage(),
                appScopeLifetime = lifetime,
            ),
        )
    }

    /**
     * Resets the holder, then closes the per-test database so its Room connection pool / SQLite
     * connections are released instead of living until the instrumentation process dies.
     *
     * The `close()` is UNGUARDED on purpose — no `runCatching`. It is safe even for the DB-free
     * tripwire's never-opened database (`RecoveryActivityDbFreeTest`'s driver throws from `open()`):
     * Room 3's `RoomDatabase.close()` is `closeBarrier.close()` → `coroutineScope.cancel()` +
     * `invalidationTracker.stop()` (Android's `stop()` only stops the multi-instance client, it runs no
     * SQL) + `connectionManager.close()` → `connectionPool.close()`, and BOTH pool implementations skip
     * connections that were never created (`ConnectionPoolImpl` iterates an `arrayOfNulls(capacity)`,
     * `PassthroughConnectionPool` guards on `::connection.isInitialized`). No path calls
     * `SQLiteDriver.open()`, so closing cannot trip the tripwire — and leaving it unguarded means a real
     * close failure surfaces instead of being swallowed. (Verified against androidx.room3 3.0.0 sources.)
     */
    override fun after() {
        MetroTestGraphHolder.reset()
        // End the generation's jobs BEFORE closing its database: a still-running collector must be
        // cancelled-and-joined, not left to race the close. runBlocking is safe here — rules run on
        // the instrumentation thread, never the main thread.
        installedLifetime?.let { runBlocking { it.cancelAndJoin() } }
        installedLifetime = null
        installedDatabase?.close()
        installedDatabase = null
    }
}
