// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import kotlinx.coroutines.flow.SharedFlow

/**
 * The read side of the navigation command bus: what the composition root collects from.
 *
 * Lives here rather than beside its implementation because it is navigation tooling over
 * [NavCommand], which this module already owns, and the `core:ui` tier is where navigation tooling
 * belongs by the architecture rule. Keeping it here is what lets the composition root in
 * `app:common` and the navigator implementation sit in different modules without either naming the
 * other.
 */
interface NavigatorReceiver {

    val commands: SharedFlow<NavCommand>
}
