package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val localActivity = LocalActivity.current

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

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

    // use to update status bar icon when orientation changes, as well as subscribe to theme changes
    LocalConfiguration.current // subscribe to config changes
    SideEffect {
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

/**
 * Projects [AppColors] onto Material 3's colour roles, because `MaterialTheme` is what every
 * stock M3 widget reads.
 *
 * `ColorScheme` has **48** colour roles (counted by reflection, not from the spec — see
 * `M3RoleContractTest`). The previous mapping set 35 of them and left 13 at the Material
 * baseline, which in practice meant **twelve roles rendering baseline purple**: the whole
 * `*Fixed` family. The thirteenth, `scrim`, was set — to `Color.Black`, which is also its
 * baseline value, so it only *looked* unmapped.
 *
 * Those twelve are now mapped too. The honest reason is not that they were proven reachable —
 * no project file reads `MaterialTheme.colorScheme.primaryFixed` (grep: zero hits), and no
 * current stock component does either. It is that *proving* a role unreachable means proving a
 * negative across every M3 component and every future version of the library, and the cost of
 * simply mapping them is zero. An unmapped role is a purple waiting for a library upgrade to
 * find it.
 *
 * `*Fixed` means "does not flip between light and dark", so each is given the tonal pairing
 * that keeps that promise: the raised surface for containers, max-contrast text on top.
 */
internal fun AppColors.toM3ColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primaryFixed = accentTintedBackground,
        primaryFixedDim = surfaceTier4,
        onPrimaryFixed = textPrimary,
        onPrimaryFixedVariant = textSecondary,
        secondaryFixed = accentTintedBackground,
        secondaryFixedDim = surfaceTier4,
        onSecondaryFixed = textPrimary,
        onSecondaryFixedVariant = textSecondary,
        tertiaryFixed = accentTintedBackground,
        tertiaryFixedDim = surfaceTier4,
        onTertiaryFixed = textPrimary,
        onTertiaryFixedVariant = textSecondary,
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
