package io.github.stslex.workeeper.probe_agg_root

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.GraphExtension
import io.github.stslex.workeeper.probe_agg_x.AppScope
import io.github.stslex.workeeper.probe_agg_x.AppService
import io.github.stslex.workeeper.probe_agg_x.FeatureScope
import io.github.stslex.workeeper.probe_agg_x.FeatureService

/**
 * P2.a — CROSS-MODULE aggregation. Neither graph names AppServiceImpl (module X) or
 * FeatureServiceImpl (module Y); Metro discovers them via @ContributesBinding(scope) at
 * graph-codegen time. AppGraph merges AppScope; the FeatureGraph extension merges
 * FeatureScope and inherits the app-scoped AppService (used by FeatureServiceImpl).
 */
@DependencyGraph(scope = AppScope::class)
interface AppGraph {
    val appService: AppService

    val featureGraph: FeatureGraph
}

@GraphExtension(scope = FeatureScope::class)
interface FeatureGraph {
    val featureService: FeatureService
}
