package io.github.stslex.workeeper.probe_agg_x

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

// Shared scope markers (visible to modules Y and Root).
abstract class AppScope private constructor()

abstract class FeatureScope private constructor()

// Shared contracts.
interface AppService {
    fun app(): String
}

interface FeatureService {
    fun feature(): String
}

/**
 * P2.a — APP-scoped contribution from module X. @ContributesBinding(AppScope) means any
 * @DependencyGraph merging AppScope auto-binds this as AppService, WITHOUT the root graph
 * naming this module — the cross-module aggregation under test.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppServiceImpl : AppService {
    override fun app(): String = "app"
}
