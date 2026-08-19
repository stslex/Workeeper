// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension

/**
 * The radius the display's own corners are cut with, for the clip every screen carries.
 *
 * ## Why a screen is clipped at all, and why always rather than during the gesture
 *
 * The predictive-back preview shrinks the leaving screen into a card, and a card with square
 * corners is the one thing about it that reads as wrong — the platform's own preview is rounded.
 * There is no corner-radius channel in a `ContentTransform` (`TransitionData` is exhaustively fade
 * / slide / changeSize / scale / veil / hold), so the rounding cannot come from the transition.
 *
 * It does not need to. **The platform does not round the window for the gesture either — the window
 * is always that shape, and you only ever notice once it shrinks away from the display's edge.**
 * So the clip is unconditional, on every screen, at rest as well as in motion, and the gesture gets
 * its rounded card for free with no signal to plumb and no per-frame work.
 *
 * At rest the clip is invisible by construction: `android:windowBackground` is the literal value of
 * `AppColors.surfaceTier0` and `App.kt`'s root `Box` paints `colorScheme.background`, which is the
 * same colour every graph paints. Whatever the corners cut away, what shows through is that colour.
 * On hardware whose display is physically rounded — which is where this radius comes from — the
 * cut pixels are outside the panel anyway.
 *
 * ## Where the number comes from
 *
 * `RoundedCorner` is API 31+; below that the platform reports nothing and
 * [AppDimension.Radius.big] stands in. A device that reports a zero or absent radius (square panel,
 * most emulators) also takes the fallback, which is deliberate: the point of the clip is the
 * shrunken card, and a square card is the defect being fixed.
 *
 * Read on composition rather than cached: see the body for why a `remember` here holds the wrong
 * number across a rotation and can pin the fallback permanently.
 */
@Composable
internal fun displayCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current

    // Read, never cached. `LocalConfiguration` is here to SUBSCRIBE, not to compute: MainActivity
    // declares `configChanges` for orientation and screenSize, so a rotation neither recreates the
    // activity nor replaces the View — a `remember(view, density)` would hold the pre-rotation
    // radius forever, and on hardware whose corners are not symmetric that is the wrong number.
    // Reading the configuration makes this composable recompose when one arrives.
    //
    // Not remembering also makes the fallback self-healing. `rootWindowInsets` is null until the
    // first inset dispatch, so a composition that beats it resolves to [AppDimension.Radius.big] —
    // cached, that would be permanent; read, the next recomposition picks up the real value. The
    // window it is wrong in is invisible: the radius only becomes visible once a gesture shrinks
    // the screen off the display edge.
    LocalConfiguration.current

    val radiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.rootWindowInsets
            ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            ?.radius
    } else {
        null
    }
    return radiusPx
        ?.takeIf { it > 0 }
        ?.let { with(density) { it.toDp() } }
        ?: AppDimension.Radius.big
}
