package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppTypography = staticCompositionLocalOf<AppTypography> {
    error("AppTypography not provided. Wrap your composable in AppTheme.")
}
