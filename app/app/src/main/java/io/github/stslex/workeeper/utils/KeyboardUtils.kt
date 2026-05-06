package io.github.stslex.workeeper.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.NavController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

internal object KeyboardUtils {

    @Composable
    fun ClearFocusOnDestinationChanged(
        navigatorHolder: NavigatorHolder,
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        DisposableEffect(navigatorHolder.navController, focusManager, keyboardController) {
            val listener = NavController.OnDestinationChangedListener { _, _, _ ->
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }

            navigatorHolder.navController.addOnDestinationChangedListener(listener)

            onDispose {
                navigatorHolder.navController.removeOnDestinationChangedListener(listener)
            }
        }
    }
}
