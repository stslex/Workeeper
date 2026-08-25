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

    LocalConfiguration.current // subscribe to config and theme changes
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
 * The theme-invariant tones behind Material's `*Fixed` roles. GUARD: literals, never palette
 * slots — a `*Fixed` role must hold the same tone in light and dark.
 */
private object FixedTones {

    /** v3 `raise`, frozen. */
    val CONTAINER = Color(RAISE)

    /** v3 `slab`, frozen — the dimmer companion tone. */
    val CONTAINER_DIM = Color(SLAB)

    /** v3 `max`, frozen. */
    val ON_CONTAINER = Color(MAX)

    /** v3 `body`, frozen. */
    val ON_CONTAINER_VARIANT = Color(BODY)

    private const val RAISE: Long = 0xFF242B32
    private const val SLAB: Long = 0xFF1E242A
    private const val MAX: Long = 0xFFF1F5F9
    private const val BODY: Long = 0xFFB7C0CA
}

/**
 * Projects [AppColors] onto Material 3's colour roles, which every stock M3 widget reads.
 * GUARD: map every role — one left unset falls back to the Material baseline, i.e. purple.
 */
internal fun AppColors.toM3ColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primaryFixed = FixedTones.CONTAINER,
        primaryFixedDim = FixedTones.CONTAINER_DIM,
        onPrimaryFixed = FixedTones.ON_CONTAINER,
        onPrimaryFixedVariant = FixedTones.ON_CONTAINER_VARIANT,
        secondaryFixed = FixedTones.CONTAINER,
        secondaryFixedDim = FixedTones.CONTAINER_DIM,
        onSecondaryFixed = FixedTones.ON_CONTAINER,
        onSecondaryFixedVariant = FixedTones.ON_CONTAINER_VARIANT,
        tertiaryFixed = FixedTones.CONTAINER,
        tertiaryFixedDim = FixedTones.CONTAINER_DIM,
        onTertiaryFixed = FixedTones.ON_CONTAINER,
        onTertiaryFixedVariant = FixedTones.ON_CONTAINER_VARIANT,
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
