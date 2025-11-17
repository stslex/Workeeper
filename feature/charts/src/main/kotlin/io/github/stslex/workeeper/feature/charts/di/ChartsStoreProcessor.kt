package io.github.stslex.workeeper.feature.charts.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.Charts
import io.github.stslex.workeeper.feature.charts.mvi.handler.ChartsComponent
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Action
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.Event
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore.State
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStoreImpl

internal typealias ChartsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * ChartsFeature is a Hilt feature module that provides the HomeStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore]
 * */
internal object ChartsFeature : Feature<ChartsStoreProcessor, Charts, ChartsComponent>() {

    @Composable
    override fun processor(
        screen: Charts,
    ): ChartsStoreProcessor = createProcessor<ChartsStoreImpl, ChartsStoreImpl.Factory>(screen)
}
