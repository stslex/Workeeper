package io.github.stslex.workeeper.wear.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.wear.domain.WearInteractor
import io.github.stslex.workeeper.wear.domain.WearInteractorImpl
import io.github.stslex.workeeper.wear.mvi.store.WearStoreImpl
import io.github.stslex.workeeper.wear.runtime.WatchRuntime

@GraphExtension(WearScope::class)
internal interface WearGraph {
    val store: WearStoreImpl

    @Binds val WearInteractorImpl.bindInteractor: WearInteractor
    @Binds val WearHandlerStoreImpl.bindHandlerStore: WearHandlerStore

    @GraphExtension.Factory
    fun interface Factory {
        fun createWearGraph(@Provides runtime: WatchRuntime): WearGraph
    }
}
