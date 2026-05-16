// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import kotlinx.coroutines.flow.SharedFlow

interface NavigatorReceiver {

    val commands: SharedFlow<NavCommand>
}
