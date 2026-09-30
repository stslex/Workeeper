package io.github.stslex.workeeper.wear.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder

@DependencyGraph(AppScope::class)
internal interface WearApplicationGraph {
    val wearGraphFactory: WearGraph.Factory

    @Provides
    @SingleIn(AppScope::class)
    fun analyticsHolder(): AnalyticsHolder = AnalyticsHolder()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides lifetime: AppScopeLifetime): WearApplicationGraph
    }
}
