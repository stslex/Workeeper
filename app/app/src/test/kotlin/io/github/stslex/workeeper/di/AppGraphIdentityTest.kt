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
 * Pure-JVM identity invariants for a Metro-owned app-scoped binding on the real [AppGraph],
 * exercised through the [AnalyticsHolder] leaf accessor (Metro codegen, no Android runtime):
 *  1. The graph CONSTRUCTS the leaf ([AnalyticsHolder]).
 *  2. The leaf is `@SingleIn(AppScope)`, so repeated reads of `appGraph.analyticsHolder` return the
 *     SAME instance (`===`) — the single-owner-per-graph invariant.
 *  3. Ownership is per-graph, not global: two independently-built graphs own distinct instances.
 */
internal class AppGraphIdentityTest {

    // App-Scope Collapse Step 5 (5a) + Phase 5: create() has 4 roots (applicationContext, appDatabase,
    // imageStorage, appScopeLifetime); the DAOs + DbTransitionRunner derive graph-internally. These
    // identity tests only exercise leaf accessors, so the Android roots are relaxed mocks; the
    // lifetime is a real instance because the graph exposes it back verbatim (asserted below).
    private fun buildGraph(
        lifetime: AppScopeLifetime = AppScopeLifetime(),
    ): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = lifetime,
        )

    @Test
    fun `graph exposes the exact lifetime root it was created with`() {
        val lifetime = AppScopeLifetime()

        val graph = buildGraph(lifetime)

        // The generation lifetime is a create() bound instance, never graph-constructed: the owner
        // that decides the graph's lifetime hands it in, and every scope-owning singleton derives
        // from THIS object. A graph-minted double would make Quiescing cancel the wrong jobs.
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

        // @SingleIn(AppScope) → the graph retains ONE instance: every read of `appGraph.analyticsHolder`
        // is the SAME object, never a fresh construction (a re-provided binding would be the
        // double-handle / === split class this guards against).
        assertSame(
            first,
            second,
            "AnalyticsHolder must be a single retained instance per graph (===), not re-provided",
        )
    }

    @Test
    fun `separate graphs own separate instances - ownership is per-graph not global`() {
        // Two independent graphs (as two processes would each build one) each own their OWN leaf.
        // This confirms the graph is the owner: identity is scoped to the graph, not a static.
        val a = buildGraph().analyticsHolder
        val b = buildGraph().analyticsHolder

        // Non-identity ACROSS graphs is the invariant this test pins: `@SingleIn(AppScope)` retains one
        // instance PER GRAPH, so the owner is the graph, never a static. Flip
        // `AppGraph.provideAnalyticsHolder()` to a process-global `INSTANCE` (or make AnalyticsHolder an
        // `object`) and this assertion — and only this one — goes red.
        assertNotSame(
            a,
            b,
            "two independently-built AppGraphs must own distinct AnalyticsHolder instances",
        )
    }
}
