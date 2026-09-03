package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat

@Composable
internal actual fun PlatformWindowChrome(darkTheme: Boolean) {
    val localActivity = LocalActivity.current
    LocalConfiguration.current // subscribe to config and theme changes
    SideEffect {
        localActivity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
}
