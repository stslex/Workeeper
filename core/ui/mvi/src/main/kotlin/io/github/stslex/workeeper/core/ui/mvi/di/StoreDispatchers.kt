package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
data class StoreDispatchers @Inject constructor(
    @DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
