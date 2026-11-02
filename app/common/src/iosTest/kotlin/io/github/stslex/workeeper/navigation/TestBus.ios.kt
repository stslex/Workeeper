// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.core.platform.AppReinitializer

internal actual fun newTestBus(): NavigatorEventBus = NavigatorEventBus(
    AppReinitializer(object : AppReinitializationHost {
        override fun requestReinitialize() = Unit
    }),
)
