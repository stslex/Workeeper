// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController

@Stable
interface NavigatorHolder {

    val navController: NavHostController
}
