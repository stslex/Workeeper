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
 * The three transitions `NavDisplay` takes, and the one of them that is not a crossfade.
 *
 * `transitionSpec` (forward navigation, and every bottom-tab switch — `NavigatorExt` REPLACES the
 * top entry for `isSingleTop`, which `NavDisplay.isPop` correctly reads as not-a-pop) and
 * `popTransitionSpec` (top-bar chevron, `navigator.popBack()`, three-button back) run the fade this
 * host has always run. `predictivePopTransitionSpec` — the finger-driven gesture, and only that —
 * runs the preview.
 *
 * GUARD: `NavDisplay` has NO fallback between the three — its `contentTransform` picks ONE of
 * `predictivePopTransitionSpec` / `popTransitionSpec` / `transitionSpec` and never chains. A spec left
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

/** The crossfade starts at 0.3, not at 0 — the incoming screen is faintly present from frame one. */
private const val ENTER_INITIAL_ALPHA = 0.3f

private const val EXIT_TARGET_ALPHA = 0f

/**
 * Material 3's own `SearchBarPredictiveBackMinScale`, matched rather than invented — and, with
 * [predictivePivot]'s off-centre pivot, its horizontal shift comes with it on the same channel.
 * The arithmetic is in `documentation/architecture.md` § "Navigation host and shared element
 * transitions".
 *
 * GUARD: changing this without moving the pivot changes both the size AND the shift.
 */
private const val PREVIEW_SCALE = 0.9f

/**
 * The screen being uncovered starts fractionally small and grows into place, so the two screens
 * read as stacked in depth rather than swapped. The platform's own preview does the same; kept
 * shallow because at 0.95 the band it opens around the incoming screen is the root `Box`'s
 * background, which is the colour that screen paints anyway.
 */
private const val REVEAL_INITIAL_SCALE = 0.95f

private const val PIVOT_NEAR = 0f
private const val PIVOT_FAR = 1f
private const val PIVOT_CENTRE = 0.5f

/**
 * Material's own predictive-back response curve, matched rather than invented:
 * `PredictiveBackEasing` in `material3/internal/BackHandler.kt` (1.5.0-alpha24), which every M3
 * predictive-back surface applies to raw gesture progress before using it.
 *
 * It is deliberately front-loaded, because a preview that lags the finger reads as unresponsive and
 * one that keeps moving all the way reads as unbounded. The platform's preview saturates; so does
 * this.
 *
 * GUARD: it lives here rather than in [AppMotion] on purpose — it reproduces Android, it does not
 * express Workeeper — and no curve on the scale may stand in for it. `AppMotion.out` is the nearest
 * and was measured and rejected; the numbers are in `documentation/architecture.md` § "Navigation
 * host and shared element transitions" and in §26's "The gesture's two curves are Android's".
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
 * The mirror image of [PREVIEW_EASING]: an ease-IN, so the dissolve is back-loaded and the card
 * stays solid under the finger — 90% opaque at a 40% drag — before reaching zero at the end.
 *
 * GUARD: **must stay continuous.** Expressing the same intent as a DELAYED tween puts a corner at
 * the delay boundary, and under a seek the fraction is the finger, so a corner is one frame in
 * which the card stops being solid. Any `delayMillis` on this spec reintroduces that.
 *
 * §26 is amended by this — alpha rides [AppMotion.linear] when it is a transit, and the
 * back-loading here is character. See the ledger row in
 * `documentation/feature-specs/v3-redesign-spec.md` §26.
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

/**
 * How dark the screen being uncovered is held while the card is still up, and the one knob if the
 * depth cue reads wrong. The bracket it was judged against is in
 * `documentation/feature-specs/v3-redesign-spec.md` §26, "The predictive scrim may not be themed".
 */
private const val SCRIM_ALPHA = 0.32f

/**
 * Forward navigation, bottom-tab switches, and every non-gesture pop.
 *
 * GUARD: `tween(motion.base)` resolves to `FastOutSlowInEasing`, not [AppMotion.linear], which
 * contradicts [AppMotion.linear]'s "the curve for alpha". Repointing it changes every navigation in
 * the app and is invisible to every gate, so it owes its own commit and its own §26 row — do not
 * fold it into a gesture change.
 */
internal fun navFadeTransform(motion: AppMotion): ContentTransform = fadeIn(
    animationSpec = tween(motion.base),
    initialAlpha = ENTER_INITIAL_ALPHA,
) togetherWith fadeOut(
    animationSpec = tween(motion.base),
    targetAlpha = EXIT_TARGET_ALPHA,
)

/**
 * The shrink, and the incoming screen's growth: the channel the finger reads.
 *
 * Spans the whole window on [PREVIEW_EASING], so a small drag already produces most of the preview
 * and further dragging refines it — the platform's bounded-preview behaviour, reproduced.
 *
 * GUARD: **must stay front-loaded.** A curve that spends less than roughly two thirds of its
 * travel in the first quarter of the drag leaves the card trailing the thumb early and still
 * moving after the gesture has committed. `AppMotion`'s own curves were measured against this and
 * none of them lands in the band — see the §26 row "The gesture's two curves are Android's" in
 * `documentation/feature-specs/v3-redesign-spec.md`.
 */
internal fun predictiveGeometrySpec(motion: AppMotion): TweenSpec<Float> = tween(
    durationMillis = motion.base,
    easing = PREVIEW_EASING,
)

/**
 * The departure: the card dissolves and the scrim lifts, on one window and one curve, so they stay
 * in lockstep and neither has a moment where it starts.
 *
 * No delay anywhere. Every channel in this file is `tween(base)` with delay 0, which makes the
 * "each channel lands exactly at fraction 1.0" invariant hold by inspection rather than by
 * arithmetic.
 */
internal fun <T> predictiveDepartureSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = DEPARTURE_EASING,
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
 * during predictive back (`NavDisplay` gives the incoming scene `initialZIndex - 1f`), so at fraction
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
    animationSpec = predictiveDepartureSpec(motion),
    targetAlpha = EXIT_TARGET_ALPHA,
)

/**
 * The screen being uncovered: fully opaque throughout, growing from [REVEAL_INITIAL_SCALE] into
 * place under a scrim that lifts as the card departs. It never fades — it is ALREADY THERE, which
 * is the reading the gesture needs; only its depth changes.
 *
 * The scrim and the shallow scale are the only depth cues a `ContentTransform` can express:
 * `TransitionData` is exhaustively fade / slide / changeSize / scale / veil / hold, so there is no
 * elevation and no shadow. The card's rounded edge is NOT drawn here — it is a clip on each graph's
 * root modifier in `AppNavigationHost`, which is how the platform does it too: the window carries
 * the display's corner radius at all times and you only notice it once the window shrinks.
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun predictivePopEnter(motion: AppMotion): EnterTransition = unveilIn(
    animationSpec = predictiveDepartureSpec(motion),
    initialColor = predictiveScrimColor(),
) + scaleIn(
    animationSpec = predictiveGeometrySpec(motion),
    initialScale = REVEAL_INITIAL_SCALE,
)

/**
 * GUARD: pure and allocation-light on purpose — `NavDisplay` invokes the spec TWICE per segment,
 * once for each half — `NavDisplay` builds its `AnimatedContent` `ContentTransform` by calling the
 * spec separately for `targetContentEnter` and `initialContentExit`. Never read composition state
 * in here; the lambda
 * that holds it is not `@Composable`.
 */
internal fun predictivePopTransform(
    motion: AppMotion,
    swipeEdge: Int,
): ContentTransform = predictivePopEnter(motion) togetherWith predictivePopExit(motion, swipeEdge)
