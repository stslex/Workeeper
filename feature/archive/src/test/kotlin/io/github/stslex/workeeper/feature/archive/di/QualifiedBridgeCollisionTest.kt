// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 batch commit #1 — proves the qualifier-preserving bridge on the COLLISION case that
 * the strip-qualifier bridge (M0) would have broken.
 *
 * Four of the remaining features (exercise, settings, single-training, recovery) bridge TWO
 * app-scoped `CoroutineDispatcher`s distinguished only by their `@DefaultDispatcher` /
 * `@IODispatcher` (or `@MainImmediateDispatcher`) qualifier. Under the old bridge both arrived
 * "bare" → duplicate `CoroutineDispatcher` binding / silent wrong-dispatcher. With
 * `metro { interop { includeJavax() } }`, Metro's binding key is `(type + qualifier)`, so the
 * two coexist and each resolves to its own instance.
 *
 * Archive's real graph has only one dispatcher, so this proof is a FOCUSED test graph that
 * mirrors a colliding feature: two same-typed, differently-qualified `@Provides` bound
 * instances + two consumers each requesting one by qualifier. If `includeJavax()` did NOT
 * register the javax qualifiers, this file would FAIL TO COMPILE (duplicate binding) — so the
 * test compiling AND passing is itself the proof the strip-bug cannot recur.
 */
internal class QualifiedBridgeCollisionTest {

    /** Consumer of the @DefaultDispatcher-qualified dispatcher. */
    @Inject
    class DefaultConsumer(
        @DefaultDispatcher val dispatcher: CoroutineDispatcher,
    )

    /** Consumer of the @IODispatcher-qualified dispatcher. */
    @Inject
    class IoConsumer(
        @IODispatcher val dispatcher: CoroutineDispatcher,
    )

    /**
     * A graph receiving two same-typed dispatchers distinguished only by qualifier — the exact
     * shape a colliding feature's graph will have. Mirrors the M0 bridge: qualified `@Provides`
     * bound instances handed in via the factory.
     */
    @DependencyGraph
    internal interface TwoDispatcherGraph {

        val defaultConsumer: DefaultConsumer

        val ioConsumer: IoConsumer

        // Direct qualified accessors — prove each qualifier resolves independently.
        @DefaultDispatcher
        val default: CoroutineDispatcher

        @IODispatcher
        val io: CoroutineDispatcher

        @DependencyGraph.Factory
        fun interface Factory {
            fun create(
                @Provides @DefaultDispatcher default: CoroutineDispatcher,
                @Provides @IODispatcher io: CoroutineDispatcher,
            ): TwoDispatcherGraph
        }
    }

    @Test
    fun `two same-typed dispatchers resolve by qualifier with no collision`() {
        // Two distinct instances so === identity distinguishes them unambiguously.
        val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
        val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        val graph = createGraphFactory<TwoDispatcherGraph.Factory>()
            .create(default = defaultDispatcher, io = ioDispatcher)

        // Each qualified accessor resolves to its own bound instance (=== identity, no cross-wire).
        assertSame(defaultDispatcher, graph.default, "@DefaultDispatcher must resolve to the default instance")
        assertSame(ioDispatcher, graph.io, "@IODispatcher must resolve to the IO instance")

        // Each @Inject consumer receives the correctly-qualified instance — proves downstream
        // resolution by qualifier, not just accessor-level.
        assertSame(
            defaultDispatcher,
            graph.defaultConsumer.dispatcher,
            "DefaultConsumer must receive the @DefaultDispatcher instance",
        )
        assertSame(
            ioDispatcher,
            graph.ioConsumer.dispatcher,
            "IoConsumer must receive the @IODispatcher instance",
        )
    }
}
