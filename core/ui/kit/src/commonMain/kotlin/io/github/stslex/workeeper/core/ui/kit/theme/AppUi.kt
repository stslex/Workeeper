package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Suppress("unused")
object AppUi {

    val uiFeatures: AppUiFeatures
        @Composable
        @ReadOnlyComposable
        get() = LocalAppUiFeatures.current

    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val typography: AppTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTypography.current

    val shapes: AppShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalAppShapes.current

    val motion: AppMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalAppMotion.current

    val elevation: AppElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalAppElevation.current
}
