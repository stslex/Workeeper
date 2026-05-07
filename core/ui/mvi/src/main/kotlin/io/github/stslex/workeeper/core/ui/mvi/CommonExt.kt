// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.SavedStateHandle
import io.github.stslex.workeeper.core.ui.navigation.SaveHandlerAttr
import kotlinx.coroutines.flow.StateFlow

fun <T> SavedStateHandle.getStateFlow(
    attr: SaveHandlerAttr<T>,
): StateFlow<T?> = getStateFlow(attr.key, attr.defaultValue)

fun SavedStateHandle.setAttrDefaultValue(attr: SaveHandlerAttr<*>) {
    set(attr.key, attr.defaultValue)
}
