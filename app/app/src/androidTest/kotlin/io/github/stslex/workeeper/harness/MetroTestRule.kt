// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import org.junit.rules.ExternalResource

/**
 * JUnit rule that installs a FRESH Metro [io.github.stslex.workeeper.di.AppGraph] into
 * [MetroTestGraphHolder] for each test (App-Scope Collapse Step 6, Phase 3.3), then clears it.
 *
 * Instrumentation creates the [TestApplication] once per process, so the graph MUST be rebuilt per
 * test — otherwise a fail-fast-DB test and an in-memory-DB test running in the same process would share
 * one graph. `@Before` (via [before]) builds the graph from the two `create()` roots and installs it;
 * `@After` (via [after]) resets the holder so a leaked read between tests fails loudly.
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
    private val appDatabase: (Context) -> AppDatabase = { ctx -> InMemoryDatabaseProvider.create(ctx) },
    private val imageStorage: () -> ImageStorage = { FakeImageStorage() },
) : ExternalResource() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    override fun before() {
        val ctx = context
        MetroTestGraphHolder.install(
            buildAppGraph(
                applicationContext = ctx,
                appDatabase = appDatabase(ctx),
                imageStorage = imageStorage(),
            ),
        )
    }

    override fun after() {
        MetroTestGraphHolder.reset()
    }
}
