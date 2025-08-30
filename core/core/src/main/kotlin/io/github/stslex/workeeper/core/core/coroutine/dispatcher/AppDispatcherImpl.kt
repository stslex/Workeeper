package io.github.stslex.workeeper.core.core.coroutine.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import org.koin.core.annotation.Single

@Single
class AppDispatcherImpl : AppDispatcher {

    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: MainCoroutineDispatcher = Dispatchers.Main
    override val immediate: MainCoroutineDispatcher = Dispatchers.Main.immediate
}