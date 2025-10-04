package io.github.stslex.workeeper.feature.all_trainings.di

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore.State
import javax.inject.Inject

@ViewModelScoped
internal class TrainingHandlerStoreImpl @Inject constructor() : TrainingHandlerStore,
    BaseHandlerStore<State, Action, Event>()
