package io.github.stslex.workeeper.core.core.di

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.DefaultDispatcher
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.IODispatcher
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.MainDispatcher
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("io.github.stslex.workeeper.core.core")
class ModuleCore {

    @MainDispatcher
    @Single
    fun bindMain(): CoroutineDispatcher = Dispatchers.Main

    @MainImmediateDispatcher
    @Single
    fun bindMainImmediate(): CoroutineDispatcher = Dispatchers.Main.immediate

    @DefaultDispatcher
    @Single
    fun bindDefault(): CoroutineDispatcher = Dispatchers.Default

    @IODispatcher
    @Single
    fun bindIO(): CoroutineDispatcher = Dispatchers.IO
}

