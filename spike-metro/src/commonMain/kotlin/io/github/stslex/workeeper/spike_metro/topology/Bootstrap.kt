package io.github.stslex.workeeper.spike_metro.topology

import dev.zacsweers.metro.createGraph

/**
 * Shared (commonMain) bootstrap of the canonical topology: build the app graph, then
 * obtain the assisted Store factory through the feature extension. This whole surface
 * is platform-neutral — both androidMain and iosMain consume it.
 */
fun createAppGraph(): AppGraph = createGraph<AppGraph>()

/** The platform-neutral way to mint a Store for a screen (used directly by iOS). */
fun AppGraph.newStore(screenId: String): ChartStore =
    featureGraph.storeFactory.create(screenId)
