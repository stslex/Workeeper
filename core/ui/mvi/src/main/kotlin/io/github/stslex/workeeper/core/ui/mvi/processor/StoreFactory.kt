// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.processor

import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.navigation.Screen

interface StoreFactory<TScreen : Screen, TStoreImpl : BaseStore<*, *, *>> {

    fun create(screen: TScreen): TStoreImpl
}
