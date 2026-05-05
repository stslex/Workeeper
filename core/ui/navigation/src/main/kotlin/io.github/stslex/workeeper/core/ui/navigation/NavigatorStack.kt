// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import kotlinx.coroutines.flow.StateFlow

interface NavigatorStack {

    fun setCurrentStack(vararg stackAttr: SaveHandlerAttr<*>)

    fun <T : Any> subscribeToStackAttr(saveHandlerAttr: SaveHandlerAttr<T>): StateFlow<T?>?
}
