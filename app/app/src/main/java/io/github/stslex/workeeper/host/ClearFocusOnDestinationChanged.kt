// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder

@Composable
fun ClearFocusOnDestinationChanged(
    navigatorHolder: NavigatorHolder,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // snapshotFlow emits the current top entry on first collection, so — exactly like the Nav2
    // OnDestinationChangedListener, which the controller called once on registration — this also
    // clears focus once at startup. Keyed on the keyboard controller too: a new controller
    // re-binds the collector, mirroring the old DisposableEffect's key set.
    LaunchedEffect(navigatorHolder, focusManager, keyboardController) {
        snapshotFlow { navigatorHolder.backStack.lastOrNull() }
            .collect {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
    }
}
