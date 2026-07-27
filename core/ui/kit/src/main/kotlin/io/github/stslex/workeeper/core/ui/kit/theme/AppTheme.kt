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
 * The theme-invariant tones behind Material's `*Fixed` roles.
 *
 * These are literals rather than palette slots on purpose: the whole point of a `*Fixed` role is
 * that it does **not** change between light and dark, so it cannot be sourced from anything that
 * does. The tones are taken from the dark end of the v3 ramp — a raised slab with max-contrast
 * content — which reads correctly under either theme because it never changes.
 *
 * Measured: [ON_CONTAINER] on [CONTAINER] is 13.07:1, [ON_CONTAINER_VARIANT] on [CONTAINER] is
 * 7.78:1, and [ON_CONTAINER] on [CONTAINER_DIM] is 14.29:1. Nothing reads these today; they are
 * mapped so that a component which starts to cannot render Material baseline purple.
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
 * `*Fixed` means exactly what it says: Material contracts these roles to hold **the same tone in
 * light and dark**. They are therefore assigned from [FixedTones] — literal constants — and not
 * from `this`. Sourcing them from theme-dependent slots would have satisfied "not purple" while
 * breaking the contract itself, and any component that later started reading them would have
 * flipped on a theme switch.
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
