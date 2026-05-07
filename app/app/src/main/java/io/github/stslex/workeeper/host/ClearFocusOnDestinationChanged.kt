// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.NavController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Composable
fun ClearFocusOnDestinationChanged(
    navigatorHolder: NavigatorHolder,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val navController = navigatorHolder.navController

    DisposableEffect(navController, focusManager, keyboardController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
}
