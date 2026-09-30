// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * The two round-screen extremes the gates run at, derived from the Android SDK's own Wear
 * device definitions (all declare `hw.lcd.density=320`, so dp = px / 2):
 *
 * - `wearos_small_round`: 384×384 px → **192dp** — the smallest round profile the SDK defines.
 * - `wearos_xl_round`: 480×480 px → **240dp** — the largest; it is also exactly the figure the
 *   redesign spec budgeted against, so the original 240dp gate geometry corresponds to a real
 *   profile and is kept as the large end.
 *
 * `wearos_large_round` (454×454 px → 227dp — the AVD the on-watch run used) sits between the
 * two and is covered by the extremes for every constraint the gates assert.
 */
internal enum class WearScreen(val sizeDp: Int) {
    SMALL_ROUND(192),
    XL_ROUND(240),
}

/**
 * Hosts gate content on a simulated [screen]: the composition sees a [LocalConfiguration] with
 * the profile's dimensions, a [LocalDensity] carrying [fontScale], and incoming constraints of
 * exactly the profile's size — so everything driven by constraints, configuration, or font
 * scale lays out as it would on that device class. The class-level Robolectric window stays at
 * the largest profile so the host box is never itself clipped.
 */
@Composable
internal fun WearGateHost(
    screen: WearScreen,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val baseDensity = LocalDensity.current
    val configuration = Configuration(LocalConfiguration.current).apply {
        screenWidthDp = screen.sizeDp
        screenHeightDp = screen.sizeDp
        this.fontScale = fontScale
    }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalDensity provides Density(baseDensity.density, fontScale),
    ) {
        Box(modifier = Modifier.requiredSize(screen.sizeDp.dp)) {
            content()
        }
    }
}
