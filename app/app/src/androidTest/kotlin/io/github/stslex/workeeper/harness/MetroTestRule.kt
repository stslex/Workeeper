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
 * Installs a fresh per-test Metro graph into [MetroTestGraphHolder], then clears it. Declare it at
 * `@Rule(order = 0)` so its teardown runs last. See documentation/testing.md.
 */
internal class MetroTestRule(
    private val appDatabaseFactory: (Context) -> AppDatabase = { ctx -> InMemoryDatabaseProvider.create(ctx) },
    private val imageStorage: () -> ImageStorage = { FakeImageStorage() },
) : ExternalResource() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var installedDatabase: AppDatabase? = null
    private var installedLifetime: AppScopeLifetime? = null

    /** The [AppDatabase] this test's graph was built from; persistence tests read it back. */
    val appDatabase: AppDatabase
        get() = installedDatabase ?: error("MetroTestRule.appDatabase read before @Before installed the graph")

    override fun before() {
        val ctx = context
        val database = appDatabaseFactory(ctx)
        installedDatabase = database
        // Per-test lifetime, cancelled in after() so graph-owned collectors cannot leak.
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
     * Resets the holder, then closes the per-test database. The close stays unguarded: closing a
     * never-opened Room 3 database cannot trip a throwing driver. See documentation/testing.md.
     */
    override fun after() {
        MetroTestGraphHolder.reset()
        // End the generation's jobs before closing its database, so no collector races the close.
        installedLifetime?.let { runBlocking { it.cancelAndJoin() } }
        installedLifetime = null
        installedDatabase?.close()
        installedDatabase = null
    }
}
