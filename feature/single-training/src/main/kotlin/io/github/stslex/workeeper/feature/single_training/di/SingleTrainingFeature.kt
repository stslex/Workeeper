// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.mvi.handler.SingleTrainingComponent
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl

internal typealias SingleTrainingStoreProcessor = StoreProcessor<State, Action, Event>

internal object SingleTrainingFeature :
    Feature<SingleTrainingStoreProcessor, Screen.Training, SingleTrainingComponent>() {

    @Composable
    override fun processor(
        screen: Screen.Training,
    ): SingleTrainingStoreProcessor =
        createProcessor<SingleTrainingStoreImpl, SingleTrainingStoreImpl.Factory>(screen)
}
