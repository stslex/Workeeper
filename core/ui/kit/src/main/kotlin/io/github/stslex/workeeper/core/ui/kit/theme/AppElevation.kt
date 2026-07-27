package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Depth tokens.
 *
 * [level0]–[level5] are **tone**, not z: this palette expresses ordinary depth by surface
 * colour, and [shadow] is `0.dp` because nothing in the app floats. That stays true.
 *
 * The three `lift*` values are the exception, and they are the v3 `--slabtop` token. See
 * `Modifier.liftedSurface` for what the role is and why its mechanism inverts by theme; only
 * the numbers live here.
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
    /**
     * `--slabtop`, the **dark** half: the colour of the 1dp highlight drawn along the inside of
     * a lifted surface's top edge. `rgba(255,255,255,.055)`, transcribed as `0x0EFFFFFF`
     * (0.055 x 255 = 14.0 = 0x0E).
     *
     * `Color.Transparent` in light, where the mechanism is [liftShadow] instead.
     */
    val liftHighlight: Color,
    /**
     * `--slabtop`, the **light** half: the cast-shadow elevation of a lifted surface.
     *
     * `0.dp` in dark, where the mechanism is [liftHighlight] instead.
     */
    val liftShadow: Dp,
    /** The hue [liftShadow] casts in. Meaningless when [liftShadow] is `0.dp`. */
    val liftShadowColor: Color,
)

/**
 * `rgba(255,255,255,.055)` — the dark theme's inner top-edge highlight.
 *
 * 0.055 x 255 = 14.025, which rounds to 14 = `0x0E`. Round-trips to 0.0549.
 */
private const val LIFT_HIGHLIGHT_DARK: Long = 0x0EFFFFFF

/**
 * The mockup draws two shadow layers, `0 1px 3px` and `0 6px 18px`. Android casts one shadow
 * per elevation, so the outer layer's 6px offset is the value carried across — it is the layer
 * that does the lifting; the 1px contact layer is a seam the 6dp shadow already implies.
 */
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
