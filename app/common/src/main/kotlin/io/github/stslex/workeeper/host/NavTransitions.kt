// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.unveilIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigationevent.NavigationEvent
import io.github.stslex.workeeper.core.ui.kit.theme.AppMotion

/*
 * The three transitions `NavDisplay` takes, and the one of them that is not a crossfade.
 *
 * `transitionSpec` (forward navigation, and every bottom-tab switch — `NavigatorExt` REPLACES the
 * top entry for `isSingleTop`, which `NavDisplay.isPop` correctly reads as not-a-pop) and
 * `popTransitionSpec` (top-bar chevron, `navigator.popBack()`, three-button back) run the fade this
 * host has always run. `predictivePopTransitionSpec` — the finger-driven gesture, and only that —
 * runs the preview.
 *
 * GUARD: `NavDisplay` has NO fallback between the three (`NavDisplay.kt:799-814`). A spec left
 * unpassed keeps the library default, and the predictive default is
 * `fadeIn(spring(1f, 1600f)) togetherWith scaleOut(targetScale = 0.7f)` — a shrink with no fade,
 * which given the z-order below ends every gesture in a visible cut.
 *
 * GUARD: every channel here spans exactly [AppMotion.base]. `SeekableTransitionState` maps the
 * finger's fraction to playtime as `fraction × transition.totalDurationNanos`, the MAX over every
 * animation registered on the scene transition — so that equality is what makes "the leaving screen
 * reached alpha 0" and "the gesture completed" the same instant. Three animations Compose registers
 * on our behalf are proven to contribute zero: the transform-origin spring (both endpoints resolve
 * to [predictivePivot]'s value), the outgoing scene's veil (see [predictiveScrimColor]), and
 * `NavDisplay`'s `sizeTransform`, which the host does not pass. The first
 * `sharedBounds`/`sharedElement` added to any screen registers a spring on this same transition and
 * breaks the equality — re-derive these windows before one ships.
 *
 * GUARD: the builders below take [AppMotion] and an `Int` and touch no receiver member.
 * `AnimatedContentTransitionScope` is a sealed interface with only internal implementations, so a
 * builder that read one could not be exercised off a device. The host's lambdas hold the receiver
 * and do nothing but delegate; `NavTransitionsTest` is what that shape buys.
 */

/** Grandfathered from the Nav2 host: the crossfade starts at 0.3, it does not start at 0. */
private const val ENTER_INITIAL_ALPHA = 0.3f

private const val EXIT_TARGET_ALPHA = 0f

/**
 * 9/10 — Material 3's own `SearchBarPredictiveBackMinScale` (material3 1.5.0-alpha24,
 * `SearchBar.kt:3842`), matched rather than invented. With the pivot on the edge opposite the finger
 * it also reproduces M3's horizontal shift for free: the centre moves `(1 - 0.9) / 2 = w/20`, which
 * is `SearchBarPredictiveBackMaxOffsetXRatio` exactly. One channel, not two.
 */
private const val PREVIEW_SCALE = 0.9f

private const val PIVOT_NEAR = 0f
private const val PIVOT_FAR = 1f
private const val PIVOT_CENTRE = 0.5f

/**
 * How dark the screen being uncovered is held while the card is still up. Judged on a device,
 * bracket 0.24-0.40; it is the one number in this file with no derivation behind it.
 */
private const val SCRIM_ALPHA = 0.32f

/**
 * Forward navigation, bottom-tab switches, and every non-gesture pop.
 *
 * GUARD: `tween(motion.base)` resolves to `FastOutSlowInEasing`, not [AppMotion.linear], which
 * contradicts [AppMotion.linear]'s "the curve for alpha". That divergence is pre-existing and
 * deliberately untouched: repointing it changes every navigation in the app, is invisible to every
 * gate, and owes its own commit and its own §26 row. Do not fold it into a gesture change.
 */
internal fun navFadeTransform(motion: AppMotion): ContentTransform = fadeIn(
    animationSpec = tween(motion.base),
    initialAlpha = ENTER_INITIAL_ALPHA,
) togetherWith fadeOut(
    animationSpec = tween(motion.base),
    targetAlpha = EXIT_TARGET_ALPHA,
)

/**
 * The shrink. Spans the whole transition, so it is the channel the finger reads.
 *
 * [AppMotion.travel], for a mechanical reason rather than an aesthetic one. `NavDisplay` finishes a
 * COMMITTED gesture with `SeekableTransitionState.animateTo(scene)` and a null spec
 * (`NavDisplay.kt:757-760`), which advances the fraction with a plain `lerp` — the
 * `animate(..., tween(remaining))` branch beside it is the CANCEL path. So this easing is the only
 * deceleration the arrival gets. [AppMotion.linear] would stop the card dead; [AppMotion.out] is
 * near-expo and lands 82.6% of the shrink inside the first quarter of the drag; [AppMotion.spring]
 * peaks at 1.098, which under a seek means the scale dips BELOW its target at f≈0.57 and climbs
 * back — a non-monotone answer to a monotone drag.
 */
internal fun predictiveGeometrySpec(motion: AppMotion): TweenSpec<Float> = tween(
    durationMillis = motion.base,
    easing = motion.travel,
)

/**
 * The handoff: the card dissolves and the scrim lifts, on one window and therefore in lockstep.
 *
 * Held at its start value for [AppMotion.fast], then run for the remainder of [AppMotion.base] — so
 * for the first 54% of the gesture the preview is a fully opaque card, which is the whole difference
 * between this and a crossfade. Both numbers come off the scale and their sum is `base`, which is
 * what makes the leaving screen provably gone at fraction 1.0.
 *
 * §26 is unamended by this: alpha still rides [AppMotion.linear]. Only the window moved.
 */
internal fun <T> predictiveHandoffSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base - motion.fast,
    delayMillis = motion.fast,
    easing = motion.linear,
)

/**
 * GUARD: pure black, and it may not be themed. `AnimatedContent` gives the enter transition to the
 * EXITING scene as well, where the veil animates between this colour at alpha 0 and
 * `SharedMutableTransformState.lastVeil` = `Color.Transparent`. Those are the same colour only while
 * the base is black; any other base makes them differ in the animation's vector space, the fallback
 * `spring<Color>` then contributes ~600ms to `transition.totalDurationNanos`, and every window in
 * this file stops landing where it was computed to land. `NavTransitionsTest` pins the invariant.
 */
internal fun predictiveScrimColor(): Color = Color.Black.copy(alpha = SCRIM_ALPHA)

/**
 * The card shrinks toward the edge the finger is NOT on, matching M3's predictive back and the
 * platform preview.
 *
 * No `layoutDirection` input, deliberately: `NavigationEvent.EDGE_*` is a PHYSICAL edge and
 * `TransformOrigin` is written straight onto `GraphicsLayerScope` with no RTL mirroring. M3 needs an
 * `rtlMultiplier` because it computes a layout offset; a pivot needs none.
 *
 * `else ->` rather than an exhaustive `when`: `EDGE_NONE` cannot reach here through `NavDisplay`'s
 * own gating (the predictive branch requires `InProgress`, and `EDGE_NONE` is produced only on the
 * `Idle` arm), but a `NavigationEvent` carrying it is representable and a future fourth constant
 * must not crash a transition. A centred shrink is the honest answer to "back, with no direction".
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
 *
 * GUARD: the fade is not decoration — it is what CLEARS the scene. The incoming is placed BELOW
 * during predictive back (`NavDisplay.kt:673`, `targetZIndex = initialZIndex - 1f`), so at fraction
 * 1.0 the outgoing must contribute no pixels or it covers the screen it just revealed.
 *
 * No `slide` channel, and that is load-bearing twice over: `slide` and `changeSize` are read inside
 * the placement block, so they invalidate layout every frame and every graph here carries
 * `Modifier.reportScreenPlace<..>()`, whose `onPlaced` would then fire per frame per scene; and both
 * store a lambda that is reference-compared, which would put this transition out of reach of a JVM
 * assertion. `scale` and `fade` are read in the graphics layer, `veil` in a draw node.
 */
internal fun predictivePopExit(
    motion: AppMotion,
    swipeEdge: Int,
): ExitTransition = scaleOut(
    animationSpec = predictiveGeometrySpec(motion),
    targetScale = PREVIEW_SCALE,
    transformOrigin = predictivePivot(swipeEdge),
) + fadeOut(
    animationSpec = predictiveHandoffSpec(motion),
    targetAlpha = EXIT_TARGET_ALPHA,
)

/**
 * The screen being uncovered: full size, full opacity, held under a scrim that lifts on the handoff
 * window. No alpha and no geometry — it is ALREADY THERE, which is the reading the gesture needs.
 *
 * The scrim is the only depth cue a `ContentTransform` can express: `TransitionData` is exhaustively
 * fade / slide / changeSize / scale / veil / hold, so there is no elevation, no shadow and no corner
 * radius to draw the card's edge with.
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun predictivePopEnter(motion: AppMotion): EnterTransition = unveilIn(
    animationSpec = predictiveHandoffSpec(motion),
    initialColor = predictiveScrimColor(),
)

/**
 * GUARD: pure and allocation-light on purpose — `NavDisplay` invokes the spec TWICE per segment,
 * once for each half (`NavDisplay.kt:824-831`). Never read composition state in here; the lambda
 * that holds it is not `@Composable`.
 */
internal fun predictivePopTransform(
    motion: AppMotion,
    swipeEdge: Int,
): ContentTransform = predictivePopEnter(motion) togetherWith predictivePopExit(motion, swipeEdge)
