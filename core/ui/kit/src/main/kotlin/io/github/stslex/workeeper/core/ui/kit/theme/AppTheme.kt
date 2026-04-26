package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val localActivity = LocalActivity.current

    val colors = remember(darkTheme) {
        if (darkTheme) provideDarkAppColors() else provideLightAppColors()
    }
    val typography = remember { provideAppTypography() }
    val shapes = remember { provideAppShapes() }
    val motion = remember { provideAppMotion() }
    val elevation = remember(colors) { provideAppElevation(colors) }
    val colorScheme = remember(colors) { colors.toM3ColorScheme() }
    val m3Typography = remember(typography) { typography.toM3Typography() }
    val m3Shapes = remember(shapes) { shapes.toM3Shapes() }

    LaunchedEffect(darkTheme) {
        localActivity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    val appUiFeatures = remember { appUiFeaturesState(enableNoise = true, blurEnable = false) }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
        LocalAppShapes provides shapes,
        LocalAppMotion provides motion,
        LocalAppElevation provides elevation,
        LocalAppUiFeatures provides appUiFeatures,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = m3Typography,
            shapes = m3Shapes,
            content = content,
        )
    }
}

internal fun AppColors.toM3ColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentTintedBackground,
        onPrimaryContainer = accentTintedForeground,
        inversePrimary = accentTintedForeground,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceTier4,
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = surfaceTier3,
        onTertiaryContainer = textPrimary,
        background = surfaceTier0,
        onBackground = textPrimary,
        surface = surfaceTier1,
        onSurface = textPrimary,
        surfaceVariant = surfaceTier4,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = status.error,
        onError = onAccent,
        errorContainer = setType.failureBackground,
        onErrorContainer = setType.failureForeground,
        outline = borderDefault,
        outlineVariant = borderSubtle,
        scrim = Color.Black,
        surfaceBright = surfaceTier2,
        surfaceDim = surfaceTier0,
        surfaceContainer = surfaceTier1,
        surfaceContainerHigh = surfaceTier2,
        surfaceContainerHighest = surfaceTier4,
        surfaceContainerLow = surfaceTier1,
        surfaceContainerLowest = surfaceTier0,
    )
}
