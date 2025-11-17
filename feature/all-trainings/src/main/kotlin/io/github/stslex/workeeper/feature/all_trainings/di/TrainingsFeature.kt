package io.github.stslex.workeeper.feature.all_trainings.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.BottomBar.AllTrainings
import io.github.stslex.workeeper.feature.all_trainings.mvi.handler.AllTrainingsComponent
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.State
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStoreImpl.Factory

internal typealias TrainingStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * TrainingsFeature is a feature module that provides the TrainingStore processor.
 * It is responsible for managing the state and actions related to the profile feature.
 *
 * @see [io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore]
 * */
internal object TrainingsFeature :
    Feature<TrainingStoreProcessor, AllTrainings, AllTrainingsComponent>() {

    @Composable
    override fun processor(
        screen: AllTrainings,
        navigator: Navigator,
    ): TrainingStoreProcessor = createProcessor<TrainingStoreImpl, Factory>(navigator, screen)

    override fun createComponent(
        navigator: Navigator,
        screen: AllTrainings,
    ): AllTrainingsComponent = AllTrainingsComponent.create(navigator)
}
