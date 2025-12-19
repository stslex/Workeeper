package io.github.stslex.workeeper.feature.single_training.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Training
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.SingleTrainingComponent
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStoreImpl

internal typealias TrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * TrainingsFeature is a Koin feature module that provides the TrainingStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore]
 * */
internal object TrainingFeature :
    Feature<TrainingStoreProcessor, Training, SingleTrainingComponent>() {

    @Composable
    override fun processor(
        screen: Training,
    ): TrainingStoreProcessor = createProcessor<TrainingStoreImpl, TrainingStoreImpl.Factory>(
        screen = screen,
    )
}
