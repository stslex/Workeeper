// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The two theme variants every golden is recorded in, supplied by `@EnumSource` so a single
 * `@ParameterizedTest` covers both.
 *
 * [windowTheme] is Paparazzi's window background. It should never actually be visible: every
 * golden paints `surfaceTier0` over the full frame (see `golden`). It is still set per variant
 * so that a golden which *forgets* to paint its background produces an obviously wrong image
 * rather than a plausible one.
 */
enum class GoldenTheme(
    val themeMode: ThemeMode,
    val windowTheme: String,
    /** Becomes the trailing segment of the golden file name. */
    val suffix: String,
) {
    LIGHT(
        themeMode = ThemeMode.LIGHT,
        windowTheme = "android:Theme.Material.Light.NoActionBar",
        suffix = "light",
    ),
    DARK(
        themeMode = ThemeMode.DARK,
        windowTheme = "android:Theme.Material.NoActionBar",
        suffix = "dark",
    ),
}
