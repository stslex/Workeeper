// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import kotlinx.coroutines.flow.SharedFlow

/** The read side of the navigation command bus: what the composition root collects from. */
interface NavigatorReceiver {

    val commands: SharedFlow<NavCommand>
}
