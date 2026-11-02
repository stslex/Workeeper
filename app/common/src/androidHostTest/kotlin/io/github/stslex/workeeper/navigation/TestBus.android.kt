// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.mockk.mockk

internal actual fun newTestBus(): NavigatorEventBus = NavigatorEventBus(mockk(relaxed = true))
