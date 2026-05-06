package io.github.stslex.workeeper.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.NavController
import androidx.navigation.NavHostController

internal object KeyboardUtils {

    @Composable
    fun ClearFocusOnDestinationChanged(
        navController: NavHostController,
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

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
}
