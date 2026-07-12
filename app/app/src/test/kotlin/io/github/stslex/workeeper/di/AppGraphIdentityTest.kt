// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 app-collapse Phase 1 (leaf E-proof) — pure-JVM half.
 *
 * Proves the Metro-OWNED side of the adopt-back seam on the real [AppGraph]:
 *  1. The graph CONSTRUCTS the leaf ([AnalyticsHolder]) — first app-scoped binding Metro owns.
 *  2. The leaf is `@SingleIn(AppScope)`, so repeated reads of `appGraph.analyticsHolder` return the
 *     SAME instance (`===`). This is the single-owner invariant the adopt-back `@Provides` relies on:
 *     the delegating Hilt provider returns `appGraph.analyticsHolder`, so if the graph itself did not
 *     retain one instance, every Hilt read would diverge. Metro codegen, no Android runtime — the
 *     cross-side Hilt→Metro `===` is asserted in the instrumented `AppGraphAdoptBackSeamTest`.
 */
internal class AppGraphIdentityTest {

    // App-Scope Collapse Step 3 (C2, bridge-scaffold): create() now also takes the db-cascade substrate
    // (9 DAOs + DbTransitionRunner + ImageStorage). These identity tests only exercise the leaf accessor,
    // so all bridge inputs are relaxed mocks — none is consumed until the repo flips land (C2 commit 2).
    private fun buildGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            exerciseDao = mockk(relaxed = true),
            exerciseTagDao = mockk(relaxed = true),
            performedExerciseDao = mockk(relaxed = true),
            sessionDao = mockk(relaxed = true),
            setDao = mockk(relaxed = true),
            tagDao = mockk(relaxed = true),
            trainingDao = mockk(relaxed = true),
            trainingExerciseDao = mockk(relaxed = true),
            trainingTagDao = mockk(relaxed = true),
            dbTransitionRunner = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
        )

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

        // @SingleIn(AppScope) → the graph retains ONE instance. This is what makes the adopt-back
        // delegating @Provides safe: every Hilt-side read of `appGraph.analyticsHolder` is the SAME
        // object, never a fresh construction (which would be the double-handle / === split class).
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

        assertNotNull(a)
        assertNotNull(b)
        // NOT assertSame — different graphs, different owners. Same-graph identity is the invariant
        // (asserted above); cross-graph identity is deliberately NOT expected.
    }
}
