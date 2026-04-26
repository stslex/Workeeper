package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppElevation = staticCompositionLocalOf<AppElevation> {
    error("AppElevation not provided. Wrap your composable in AppTheme.")
}
