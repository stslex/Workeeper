// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import androidx.navigation.NavHostController
import io.github.stslex.workeeper.core.ui.navigation.Navigator

interface AppNavigator : Navigator {

    val navController: NavHostController
}
