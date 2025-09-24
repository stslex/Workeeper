package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.Single

@Single
data class StoreDispatchers(
    @param:DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @param:MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
