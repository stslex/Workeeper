package io.github.stslex.workeeper.probe_agg_y

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.probe_agg_x.AppService
import io.github.stslex.workeeper.probe_agg_x.FeatureScope
import io.github.stslex.workeeper.probe_agg_x.FeatureService

/**
 * P2.a — FEATURE-scoped contribution from module Y. It depends on the APP-scoped
 * [AppService] (contributed from module X), so a feature graph must see the app-scoped
 * binding inherited from its parent — cross-module + cross-scope resolution.
 */
@Inject
@SingleIn(FeatureScope::class)
@ContributesBinding(FeatureScope::class)
class FeatureServiceImpl(
    private val appService: AppService,
) : FeatureService {
    override fun feature(): String = "feature(${appService.app()})"
}
