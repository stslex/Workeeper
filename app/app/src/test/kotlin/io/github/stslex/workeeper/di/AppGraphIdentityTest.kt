// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pure-JVM identity invariants for an app-scoped binding on the real [AppGraph], read through the
 * [AnalyticsHolder] leaf: one retained instance per graph, and ownership per-graph, not global.
 */
internal class AppGraphIdentityTest {

    // Leaf accessors only: the Android roots are relaxed mocks, the lifetime is real because the
    // graph exposes it back verbatim (asserted below).
    private fun buildGraph(
        lifetime: AppScopeLifetime = AppScopeLifetime(),
    ): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = lifetime,
            databaseReplacement = mockk(relaxed = true),
        )

    @Test
    fun `graph exposes the exact lifetime root it was created with`() {
        val lifetime = AppScopeLifetime()

        val graph = buildGraph(lifetime)

        // Every scope-owning singleton derives from this object; a graph-minted double would make
        // Quiescing cancel the wrong jobs.
        assertSame(
            lifetime,
            graph.appScopeLifetime,
            "AppGraph must expose the exact AppScopeLifetime passed at create()",
        )
    }

    @Test
    fun `app graph constructs the leaf singleton`() {
        val holder = buildGraph().analyticsHolder

        assertNotNull(holder, "AppGraph must construct AnalyticsHolder as an app-owned binding")
    }

    @Test
    fun `leaf is a single-owner app-scoped singleton - repeated reads are identical`() {
        val graph = buildGraph()

        val first = graph.analyticsHolder
        val second = graph.analyticsHolder

        assertSame(
            first,
            second,
            "AnalyticsHolder must be a single retained instance per graph (===), not re-provided",
        )
    }

    @Test
    fun `separate graphs own separate instances - ownership is per-graph not global`() {
        val a = buildGraph().analyticsHolder
        val b = buildGraph().analyticsHolder

        // A process-global instance (or an `object`) would make this the only assertion to go red.
        assertNotSame(
            a,
            b,
            "two independently-built AppGraphs must own distinct AnalyticsHolder instances",
        )
    }
}
