// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.unveilIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigationevent.NavigationEvent
import io.github.stslex.workeeper.core.ui.kit.theme.AppMotion

/*
 * The three transitions `NavDisplay` takes; only the predictive pop is not a crossfade.
 * GUARD: NavDisplay never chains the three specs; an unpassed spec keeps the library default.
 * GUARD: every channel spans [AppMotion.base]; a shared-element spring would break that equality.
 */

/** The crossfade starts at 0.3, not 0 — the incoming screen is faintly present from frame one. */
private const val ENTER_INITIAL_ALPHA = 0.3f

private const val EXIT_TARGET_ALPHA = 0f

/** M3's `SearchBarPredictiveBackMinScale`; with [predictivePivot] it carries the shift too. */
private const val PREVIEW_SCALE = 0.9f

/** The uncovered screen starts fractionally small and grows, so the screens read as stacked. */
private const val REVEAL_INITIAL_SCALE = 0.95f

private const val PIVOT_NEAR = 0f
private const val PIVOT_FAR = 1f
private const val PIVOT_CENTRE = 0.5f

/**
 * Material's `PredictiveBackEasing`, matched rather than invented: front-loaded, so the preview
 * saturates like the platform's. See architecture.md § "Navigation host and shared element".
 */
private val PREVIEW_EASING: Easing = CubicBezierEasing(
    PREVIEW_X1,
    PREVIEW_Y1,
    PREVIEW_X2,
    PREVIEW_Y2,
)

private const val PREVIEW_X1 = 0.1f
private const val PREVIEW_Y1 = 0.1f
private const val PREVIEW_X2 = 0f
private const val PREVIEW_Y2 = 1f

/**
 * Mirror of [PREVIEW_EASING]: back-loaded, so the card stays solid under the finger.
 * GUARD: must stay continuous — a `delayMillis` puts a corner in the seeked fraction.
 */
private val DEPARTURE_EASING: Easing = CubicBezierEasing(
    DEPARTURE_X1,
    DEPARTURE_Y1,
    DEPARTURE_X2,
    DEPARTURE_Y2,
)

private const val DEPARTURE_X1 = 0.8f
private const val DEPARTURE_Y1 = 0f
private const val DEPARTURE_X2 = 1f
private const val DEPARTURE_Y2 = 1f

/** How dark the uncovered screen is held while the card is up. See v3-redesign-spec.md §26. */
private const val SCRIM_ALPHA = 0.32f

/**
 * Forward navigation, bottom-tab switches, and every non-gesture pop.
 * GUARD: `tween(motion.base)` resolves to `FastOutSlowInEasing`, not [AppMotion.linear].
 */
internal fun navFadeTransform(motion: AppMotion): ContentTransform = fadeIn(
    animationSpec = tween(motion.base),
    initialAlpha = ENTER_INITIAL_ALPHA,
) togetherWith fadeOut(
    animationSpec = tween(motion.base),
    targetAlpha = EXIT_TARGET_ALPHA,
)

/**
 * The shrink and the incoming screen's growth: the channel the finger reads.
 * GUARD: must stay front-loaded, or the card trails the thumb early and overruns the commit.
 */
internal fun predictiveGeometrySpec(motion: AppMotion): TweenSpec<Float> = tween(
    durationMillis = motion.base,
    easing = PREVIEW_EASING,
)

/** The departure: card dissolve and scrim lift on one window and one curve, with no delay. */
internal fun <T> predictiveDepartureSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = DEPARTURE_EASING,
)

/**
 * GUARD: pure black, never themed — another base makes the exiting scene's veil endpoints differ,
 * and the fallback `spring<Color>` then stretches every window in this file.
 */
internal fun predictiveScrimColor(): Color = Color.Black.copy(alpha = SCRIM_ALPHA)

/**
 * The card shrinks toward the edge the finger is NOT on.
 * GUARD: no `layoutDirection` input — `EDGE_*` is physical and `TransformOrigin` skips RTL.
 */
internal fun predictivePivot(swipeEdge: Int): TransformOrigin = TransformOrigin(
    pivotFractionX = when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> PIVOT_FAR
        NavigationEvent.EDGE_RIGHT -> PIVOT_NEAR
        else -> PIVOT_CENTRE
    },
    pivotFractionY = PIVOT_CENTRE,
)

/**
 * The leaving screen: opaque and shrinking for the drag, dissolving for the handoff.
 * GUARD: the fade is what CLEARS the scene — the incoming is placed below during predictive back.
 */
internal fun predictivePopExit(
    motion: AppMotion,
    swipeEdge: Int,
): ExitTransition = scaleOut(
    animationSpec = predictiveGeometrySpec(motion),
    targetScale = PREVIEW_SCALE,
    transformOrigin = predictivePivot(swipeEdge),
) + fadeOut(
    animationSpec = predictiveDepartureSpec(motion),
    targetAlpha = EXIT_TARGET_ALPHA,
)

/**
 * The screen being uncovered: opaque throughout, growing into place under a lifting scrim.
 * Its rounded edge is a clip on each graph's root modifier in `AppNavigationHost`, not drawn here.
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun predictivePopEnter(motion: AppMotion): EnterTransition = unveilIn(
    animationSpec = predictiveDepartureSpec(motion),
    initialColor = predictiveScrimColor(),
) + scaleIn(
    animationSpec = predictiveGeometrySpec(motion),
    initialScale = REVEAL_INITIAL_SCALE,
)

/** GUARD: keep pure — `NavDisplay` calls the spec twice per segment; it is not `@Composable`. */
internal fun predictivePopTransform(
    motion: AppMotion,
    swipeEdge: Int,
): ContentTransform = predictivePopEnter(motion) togetherWith predictivePopExit(motion, swipeEdge)
