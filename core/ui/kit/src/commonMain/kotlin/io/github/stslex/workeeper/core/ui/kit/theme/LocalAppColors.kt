package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("AppColors not provided. Wrap your composable in AppTheme.")
}
