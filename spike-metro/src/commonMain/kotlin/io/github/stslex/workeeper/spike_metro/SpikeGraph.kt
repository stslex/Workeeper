package io.github.stslex.workeeper.spike_metro

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraph

/**
 * Phase B.1 — trivial Metro graph in commonMain, exposed. One `@Inject` class + one
 * `@DependencyGraph` accessor. The whole point is fail-fast: this must compile for
 * androidMain AND iosSimulatorArm64. No scopes/contributions yet (that's B.2) — a
 * commonMain graph is valid here precisely because there are no platform contributions
 * to merge.
 */
@Inject
class Greeter {
    fun greet(): String = "hello from the metro spike"
}

@DependencyGraph
interface SpikeGraph {
    val greeter: Greeter
}

fun createSpikeGraph(): SpikeGraph = createGraph<SpikeGraph>()
