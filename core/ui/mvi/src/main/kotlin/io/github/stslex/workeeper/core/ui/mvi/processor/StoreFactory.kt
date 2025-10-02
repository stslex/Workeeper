package io.github.stslex.workeeper.core.ui.mvi.processor

import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.navigation.Component

interface StoreFactory<TComponent : Component, TStoreImpl : BaseStore<*, *, *>> {

    fun create(component: TComponent): TStoreImpl
}
