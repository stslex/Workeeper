package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class StoreDispatchers @Inject constructor(
    @param:DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @param:MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
