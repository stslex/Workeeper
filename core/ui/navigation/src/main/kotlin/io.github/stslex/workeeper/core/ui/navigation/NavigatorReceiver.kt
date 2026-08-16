// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import kotlinx.coroutines.flow.SharedFlow

/**
 * The read side of the navigation command bus: what the composition root collects from.
 *
 * Lives here rather than beside its implementation because it is navigation tooling over
 * [NavCommand], which this module already owns, and the `core:ui` tier is where navigation tooling
 * belongs by the architecture rule. It was `:app:app`-internal until KMP phase 4 moved the
 * composition root into `app:common`, at which point the only interface standing between the two
 * modules was this one — three lines, over a type that was already here.
 */
interface NavigatorReceiver {

    val commands: SharedFlow<NavCommand>
}
