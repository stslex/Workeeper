package io.github.stslex.workeeper.spike_metro.topology

/**
 * iOS consumption: no ViewModel. iOS resolves the assisted Store factory from the
 * feature graph and calls create() directly — the retention/lifecycle is owned by
 * the iOS side (e.g. a SwiftUI observable). Proves the same commonMain Store factory
 * is usable from Kotlin/Native platform code with zero Android APIs.
 */
fun iosCreateStore(appGraph: AppGraph, screenId: String): ChartStore =
    appGraph.newStore(screenId)

fun iosRenderStore(appGraph: AppGraph, screenId: String): String =
    iosCreateStore(appGraph, screenId).render()
