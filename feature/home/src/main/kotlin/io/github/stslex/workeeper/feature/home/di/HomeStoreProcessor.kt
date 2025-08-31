package io.github.stslex.workeeper.feature.home.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore.State
import org.koin.core.module.Module
import org.koin.ksp.generated.module

internal typealias HomeStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * HomeFeature is a Koin feature module that provides the HomeStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.home.ui.mvi.store.HomeStore]
 * */
internal object HomeFeature : Feature<HomeStoreProcessor, HomeComponent>(
    scopeClass = HomeScope::class
) {

    override val module: Module by lazy { ModuleFeatureHome().module }

    @Composable
    override fun processor(
        component: HomeComponent
    ): HomeStoreProcessor = rememberStoreProcessor(component)
}
