package io.github.stslex.workeeper.probe_di_root

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.github.stslex.workeeper.probe_hilt.HiltGreeter
import io.github.stslex.workeeper.probe_metro.MetroGreeter
import javax.inject.Inject

/**
 * P2.b coexistence proof: one module, both DI systems.
 * - [HiltGreeter] arrives via Hilt (javax.inject.@Inject constructor, processed by Hilt KSP).
 * - [MetroGreeter] arrives via a Metro [DependencyGraph] (Metro compiler plugin).
 * If this file compiles, Hilt's KSP and Metro's compiler plugin run in the same compilation
 * without codegen/classpath collision.
 */
class RootWiring @Inject constructor(
    private val hiltGreeter: HiltGreeter,
) {

    private val metroGreeter: MetroGreeter = createGraph<RootMetroGraph>().metroGreeter

    fun combined(): String = "${hiltGreeter.greet()} + ${metroGreeter.greet()}"
}

/** Metro graph that constructs the Metro-provided dep (its @Inject ctor lives in :probe-metro). */
@DependencyGraph
interface RootMetroGraph {
    val metroGreeter: MetroGreeter
}
