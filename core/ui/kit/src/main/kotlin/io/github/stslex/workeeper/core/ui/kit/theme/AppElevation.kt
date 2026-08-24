package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Depth tokens. [level0]-[level5] are tone, not z, and [shadow] is `0.dp` because nothing floats.
 * The `lift*` values are the v3 `--slabtop` token; see `Modifier.liftedSurface` for the role.
 */
@Immutable
data class AppElevation(
    val level0: Color,
    val level1: Color,
    val level2: Color,
    val level3: Color,
    val level4: Color,
    val level5: Color,
    val shadow: Dp,
    val borderHairline: Dp,
    /** `--slabtop`, dark half: the 1dp inner top-edge highlight. Transparent in light. */
    val liftHighlight: Color,
    /** `--slabtop`, light half: the cast-shadow elevation of a lifted surface. `0.dp` in dark. */
    val liftShadow: Dp,
    /** The hue [liftShadow] casts in. Meaningless when [liftShadow] is `0.dp`. */
    val liftShadowColor: Color,
)

/** `rgba(255,255,255,.055)` - the dark theme's inner top-edge highlight. */
private const val LIFT_HIGHLIGHT_DARK: Long = 0x0EFFFFFF

/** The outer of the mockup's two shadow layers; Android casts one shadow per elevation. */
private val LIFT_SHADOW_LIGHT: Dp = 6.dp

/** `rgba(13,17,20,…)` — the hue both mockup shadow layers cast in. This is light `max`. */
private const val LIFT_SHADOW_COLOR_LIGHT: Long = 0xFF0D1114

fun provideAppElevation(colors: AppColors): AppElevation = AppElevation(
    level0 = colors.surfaceTier0,
    level1 = colors.surfaceTier1,
    level2 = colors.surfaceTier2,
    level3 = colors.surfaceTier3,
    level4 = colors.surfaceTier4,
    level5 = colors.surfaceTier4,
    shadow = 0.dp,
    borderHairline = 0.5.dp,
    liftHighlight = if (colors.isDark) Color(LIFT_HIGHLIGHT_DARK) else Color.Transparent,
    liftShadow = if (colors.isDark) 0.dp else LIFT_SHADOW_LIGHT,
    liftShadowColor = if (colors.isDark) Color.Transparent else Color(LIFT_SHADOW_COLOR_LIGHT),
)
