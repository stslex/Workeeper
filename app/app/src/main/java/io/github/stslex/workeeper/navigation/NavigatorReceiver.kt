// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import kotlinx.coroutines.flow.SharedFlow

interface NavigatorReceiver {

    val commands: SharedFlow<NavigationCommand>
}
