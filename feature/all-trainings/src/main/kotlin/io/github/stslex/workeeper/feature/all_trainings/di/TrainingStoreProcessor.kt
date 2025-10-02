package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStoreImpl.Factory

internal typealias TrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * TrainingsFeature is a feature module that provides the TrainingStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore]
 * */
internal object TrainingsFeature : Feature<TrainingStoreProcessor, AllTrainingsComponent>() {

    @Composable
    override fun processor(
        component: AllTrainingsComponent,
    ): TrainingStoreProcessor = createProcessor<TrainingStoreImpl, Factory>(component)
}
