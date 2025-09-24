package io.github.stslex.workeeper.feature.single_training.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.SingleTrainingComponent
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State

internal typealias TrainingStoreProcessor = StoreProcessor<State, Action, Event>

internal const val TRAINING_SCOPE_NAME = "single_trainings_scope"

/**
 * TrainingsFeature is a Koin feature module that provides the TrainingStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore]
 * */
internal object TrainingFeature : Feature<TrainingStoreProcessor, SingleTrainingComponent>(
    TRAINING_SCOPE_NAME,
) {

    @Composable
    override fun processor(
        component: SingleTrainingComponent,
    ): TrainingStoreProcessor = rememberStoreProcessor(component)
}
