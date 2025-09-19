package io.github.stslex.workeeper.feature.all_trainings.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore.State

internal interface TrainingHandlerStore : HandlerStore<State, Action, Event>
