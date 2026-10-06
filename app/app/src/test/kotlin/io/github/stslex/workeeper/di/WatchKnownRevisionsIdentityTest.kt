// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * wear-live-sync.md §10.1, the graph item: the phone bridge records into the known revisions the
 * change notifier reads (D7), so the app graph must hand both the one app-scoped instance.
 */
internal class WatchKnownRevisionsIdentityTest {

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
        )

    @Test
    fun `two reads of the known revisions give the one instance the bridge and the notifier share`() {
        val appGraph = buildAppGraph()

        assertSame(
            appGraph.watchKnownRevisions,
            appGraph.watchKnownRevisions,
            "WatchKnownRevisions must be app-scoped, or the notifier reads keys the bridge never wrote",
        )
    }
}
