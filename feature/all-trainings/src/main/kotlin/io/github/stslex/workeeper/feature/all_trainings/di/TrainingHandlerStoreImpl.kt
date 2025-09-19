package io.github.stslex.workeeper.feature.all_trainings.di

import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStoreEmitter
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State
import org.koin.core.annotation.Named
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped(binds = [TrainingHandlerStore::class, HandlerStoreEmitter::class])
@Scope(name = TRAINING_SCOPE_NAME)
@Named(TRAINING_SCOPE_NAME)
internal class TrainingHandlerStoreImpl : TrainingHandlerStore,
    BaseHandlerStore<State, Action, Event>()