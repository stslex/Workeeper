// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The two theme variants every golden is recorded in, supplied by `@EnumSource`. [windowTheme]
 * should never be visible; it is set per variant so a missing background paint shows up wrong.
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
