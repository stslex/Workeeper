package io.github.stslex.workeeper.feature.single_training.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.State

internal interface TrainingHandlerStore : HandlerStore<State, Action, Event>
