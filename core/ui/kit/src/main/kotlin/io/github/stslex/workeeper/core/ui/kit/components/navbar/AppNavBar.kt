// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.navbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import kotlin.math.abs
import kotlin.math.min

/**
 * The v3 bottom navigation — `pass2d.html` `#s-nav`, the `.nb.track.slide` variant.
 *
 *
 * ## Geometry, derived rather than transcribed (§0.2)
 *
 * The drawn `.nb{height:60px;padding:5px 8px;gap:4px}` decomposes exactly the way `.topbar`'s
 * identical `min-height:60px` already decomposed in [AppDimension] terms — and it is the *same
 * drawn number*, so it gets the *same answer*:
 *
 * | drawn | parts | ships as |
 * |---|---|---|
 * | `height:60px` | pill 50px + 2×5px padding | `heightMd` (48) + 2×`Space.xs` (4) = **56dp** (`heightLg`) |
 * | `padding:5px 8px` | — | `Space.xs` (4) vertical, `Space.sm` (8) horizontal |
 * | `gap:4px` | — | `Space.xs` (4) |
 * | `border-radius:12px` | — | `Radius.small` (8) — the rung `.icon-btn`'s 12px already took |
 * | `svg{width:22px}` | — | `iconMd` (24) — the rung `AppEmptyState` took for the identical 22px draw |
 *
 * **56, not 60**, and the difference is not a rounding preference: 60 is not on the height ladder
 * (32/40/48/56/64) and §0.2 says raw px round onto it, ties toward the value with call sites.
 * `AppTopBar` states the derivation for the same input in as many words — "`min-height:60px`
 * resolves as 48dp button + 2×4dp vertical padding = 56dp (`heightLg`)" — so shipping 60 here
 * would put two different dp answers in the app for one drawn px value, on two bars §26 explicitly
 * says match each other. Note the pill's 48dp is `MetricTabs`' `TAB_HEIGHT` unchanged, which is
 * the same grammar arriving at the same rung from the other direction.
 *
 * ## The hairline
 *
 * `border-top:1px solid var(--hair-s)`. `--hair-s` has **no app token** (D3): the slots that would
 * take it owe 3:1 under WCAG 1.4.11 and it delivers 1.12–1.52:1, so it ships against
 * `borderSubtle` at `borderHairline` — a known approximation with a palette PR owed, identical to
 * the one `TrainingRow`'s row rule already ships. It is drawn as an overlay on the top edge rather
 * than as a fourth box in a column, so the bar's total height stays exactly
 * [AppDimension.BottomNavBar.heightWithInsets]; CSS `border-top` on a `content-box` element adds
 * its 1px *outside* the declared 60, and reproducing that would put the bar 0.5dp off the number
 * the navigation host pads content by.
 *
 * ## Motion — one transit, one character, on two elements
 *
 * §26 "Nav pill motion", unchanged by the §26.1 curve split: the pill's travel is **positional**,
 * so the split leaves it on `out`.
 *
 * - **Transit** — the offset, [NAV_PILL_TRAVEL] on `out`. Monotone, no overshoot. Delete it and
 *   the pill teleports, which is the class's own reader-test for membership.
 * - **Character** — the `gel` stretch: `scaleX` peaks at `1 + 0.30 × k` where `k = |Δ| / barWidth`
 *   clamped to 1, at 42% of the travel, with [TransformOrigin] on the **leading** edge so the tail
 *   lags and catches up. Recorded in the ledger, approved for being noticed, and legal under the
 *   overshoot rule because the pill encodes no value.
 *
 * The two live on **different elements** because one `transform` cannot carry both — the drawing
 * says so (`.ind` transforms, `.ind i` animates) and the same is true here: the offset is a layout
 * offset on the outer box and the scale is a `graphicsLayer` on the inner one.
 *
 * [NAV_PILL_TRAVEL] is 340ms, which is **not** on the motion scale and **not**
 * `continuityPositionalSpec`'s `base`. It is the ledger's own number for this member, recorded in
 * "Nav pill motion" before the continuity class existed, and a member takes a non-default value
 * only by a ledger decision — which this is. It is asserted directly rather than left to reading,
 * because no golden can see a duration.
 *
 * ## What this component does NOT do
 *
 * It fires **no haptic**. §26 "Haptics" puts `SegmentTick` on a nav tab change and it is already
 * shipped, but every haptic in this app is fired at a feature/graph level — measured, zero
 * `performHapticFeedback` call sites in `core/ui/kit/src/main` — so the caller fires it in
 * [onSelect] and the convention stays intact.
 *
 * @param items the destinations, already resolved to icons and strings. See [AppNavBarItem].
 * @param selectedIndex index into [items]; coerced into range, so an unknown destination parks the
 *  pill on the first item rather than crashing or measuring a negative offset.
 * @param onSelect invoked with the tapped index. Re-selecting the current item still reports.
 */
@Composable
fun AppNavBar(
    items: List<AppNavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selected = selectedIndex.coerceIn(0, items.lastIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimension.BottomNavBar.heightWithInsets)
            .background(AppUi.colors.surfaceTier1),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(AppDimension.BottomNavBar.height)
                .padding(
                    horizontal = TRACK_PADDING_HORIZONTAL,
                    vertical = TRACK_PADDING_VERTICAL,
                ),
        ) {
            // `maxWidth` here is the width INSIDE the track's horizontal padding; the mockup's
            // `k` divides by `bar.offsetWidth`, which is the outer box, so the padding is added
            // back rather than the ratio being taken against a different denominator than the
            // drawing's.
            val contentWidth = maxWidth
            val barWidth = contentWidth + TRACK_PADDING_HORIZONTAL * 2
            val itemWidth = (contentWidth - ITEM_GAP * (items.size - 1)) / items.size
            val itemPitch = itemWidth + ITEM_GAP
            val pillShape = RoundedCornerShape(AppDimension.Radius.small)

            NavPill(
                offset = itemPitch * selected,
                width = itemWidth,
                shape = pillShape,
                selectedIndex = selected,
                itemPitch = itemPitch,
                barWidth = barWidth,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                items.forEachIndexed { index, item ->
                    // ONE TIMELINE. The tint and the pill are two properties of a single state
                    // change, so they run for the same length: `NAV_PILL_TRAVEL`, not `base`.
                    //
                    // They used to be 340 against 260, and the device pass read the destination as
                    // already selected while the pill was still travelling — 80ms of one property
                    // finished before the other. Nothing about the CURVE is decided here: both are
                    // `out`, as before, and whether a colour transit wants a different curve is a
                    // separate question §26.1 now records as an open gap rather than an omission.
                    val tint by animateColorAsState(
                        targetValue = if (index == selected) {
                            AppUi.colors.textPrimary
                        } else {
                            AppUi.colors.textTertiary
                        },
                        animationSpec = tween(
                            durationMillis = NAV_ITEM_TINT_DURATION,
                            easing = AppUi.motion.out,
                        ),
                        label = "nav-item-tint",
                    )
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .clickable { onSelect(index) }
                            .testTag(item.testTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconMd),
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            tint = tint,
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            thickness = AppDimension.borderHairline,
            color = AppUi.colors.borderSubtle,
        )
    }
}

/**
 * `.nb.slide .ind` (offset, the transit) wrapping `.nb.slide .ind i` (fill + stretch, the
 * character) — two elements because one transform cannot carry both.
 *
 * The stretch is driven off [selectedIndex] rather than off the offset's animation, because the
 * peak depends on the **distance jumped**, which the offset value no longer contains once it has
 * started moving. `k` is recomputed on each change from the index delta, exactly as `nbPick()`
 * computes it from `btn.offsetLeft - cur.offsetLeft`.
 */
@Composable
private fun NavPill(
    offset: Dp,
    width: Dp,
    shape: RoundedCornerShape,
    selectedIndex: Int,
    itemPitch: Dp,
    barWidth: Dp,
) {
    // `AppUi.motion` is a @Composable getter, so the curve is read here and captured — the
    // stretch's keyframes are built inside a coroutine, which is not a composition.
    val out = AppUi.motion.out
    val animatedOffset by animateDpAsState(
        targetValue = offset,
        animationSpec = tween(
            durationMillis = NAV_PILL_TRAVEL,
            easing = out,
        ),
        label = "nav-pill-offset",
    )

    val stretch = remember { Animatable(1f) }
    // Seeded with the initial selection so the first composition does not read a 0 -> n jump as
    // travel and fire a stretch nobody asked for. A settled bar animates nothing.
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    // LATCHED, not derived. The origin belongs to the jump in flight, and the jump's delta is
    // gone from the state as soon as `previousIndex` catches up — so recomputing it during
    // composition would flip the origin back to its default part-way through the animation and
    // stretch the pill from the wrong edge for the rest of the travel. Written once, inside the
    // effect, alongside the peak it belongs to.
    var stretchOrigin by remember { mutableStateOf(LEADING_EDGE_RIGHT) }

    LaunchedEffect(selectedIndex) {
        val jumped = selectedIndex - previousIndex
        previousIndex = selectedIndex
        if (jumped == 0) return@LaunchedEffect
        // `transform-origin` on the LEADING edge, so the tail lags: moving right (positive
        // delta) pins the right edge and the body stretches back toward where it came from.
        stretchOrigin = if (jumped > 0) LEADING_EDGE_RIGHT else LEADING_EDGE_LEFT
        val peak = navPillStretchPeak(travel = itemPitch * abs(jumped), barWidth = barWidth)
        stretch.snapTo(1f)
        stretch.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = NAV_PILL_TRAVEL
                1f at 0 using out
                peak at NAV_PILL_STRETCH_PEAK_MS using out
                1f at NAV_PILL_TRAVEL
            },
        )
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(animatedOffset.roundToPx(), 0) }
            .width(width)
            .fillMaxHeight(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = stretch.value
                    transformOrigin = stretchOrigin
                }
                .liftedSurface(shape = shape),
        )
    }
}

/**
 * `scaleX` peak for a jump of [travel] across a bar of [barWidth] — `1 + 0.30 × min(Δ/W, 1)`.
 *
 * Pure and `internal` so it can be asserted directly. Nothing else can see it: the peak lands at
 * 42% of a 340ms animation, and a golden photographs one static frame (§27). The clamp is the
 * drawing's own `Math.min(Math.abs(dx)/bar.offsetWidth, 1)` and is load-bearing — without it a
 * hypothetical bar narrower than one jump would scale past the recorded ceiling.
 */
internal fun navPillStretchPeak(travel: Dp, barWidth: Dp): Float {
    if (barWidth.value <= 0f) return 1f
    val k = min(abs(travel.value) / barWidth.value, 1f)
    return 1f + NAV_PILL_STRETCH * k
}

/** `.nb{padding:5px 8px}` — the 8px half. */
private val TRACK_PADDING_HORIZONTAL = AppDimension.Space.sm

/** `.nb{padding:5px 8px}` — the 5px half, on the 4dp rung like `.topbar`'s identical 5px. */
private val TRACK_PADDING_VERTICAL = AppDimension.Space.xs

/** `.nb{gap:4px}`. */
private val ITEM_GAP = AppDimension.Space.xs

/**
 * `.nb.slide .ind{transition:transform 340ms}` — §26 "Nav pill motion".
 *
 * Not on the motion scale (140/260/520) and not `continuityPositionalSpec`'s `base`. It is this
 * member's own recorded number, which is the only way a continuity member is allowed to hold one.
 */
internal const val NAV_PILL_TRAVEL: Int = 340

/**
 * The icon tint's duration — **defined as the pill's travel, not as a second number.**
 *
 * A separate constant rather than `NAV_PILL_TRAVEL` used twice, so `NavPillTest` can assert the two
 * are equal: the defect this closes is DIVERGENCE (340 against 260), and an assertion that pins 340
 * in two places would pass just as happily if one of them were later moved alone.
 */
internal const val NAV_ITEM_TINT_DURATION: Int = NAV_PILL_TRAVEL

/** `@keyframes gel{42%{…}}` — the peak's position in the 340ms timeline. */
internal const val NAV_PILL_STRETCH_PEAK_MS: Int = 143

/** `--sx:(1+0.30*k)` — the stretch coefficient in `nbPick()`. */
internal const val NAV_PILL_STRETCH: Float = 0.30f

/** `transform-origin:… 50%` — the stretch is horizontal, so the vertical origin never moves. */
private const val ORIGIN_VERTICAL_CENTRE = 0.5f

/** `transform-origin:100% 50%` — pinned when the pill travels right. */
private val LEADING_EDGE_RIGHT = TransformOrigin(1f, ORIGIN_VERTICAL_CENTRE)

/** `transform-origin:0% 50%` — pinned when the pill travels left. */
private val LEADING_EDGE_LEFT = TransformOrigin(0f, ORIGIN_VERTICAL_CENTRE)

@Preview
@Composable
private fun AppNavBarDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AppNavBar(items = previewItems(), selectedIndex = 0, onSelect = {})
    }
}

@Preview
@Composable
private fun AppNavBarLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AppNavBar(items = previewItems(), selectedIndex = 1, onSelect = {})
    }
}

private fun previewItems(): List<AppNavBarItem> = listOf(
    AppNavBarItem(AppIcons.Home, "Home", "BottomAppBarItem_HOME"),
    AppNavBarItem(AppIcons.Trainings, "Trainings", "BottomAppBarItem_TRAININGS"),
    AppNavBarItem(AppIcons.Exercises, "Exercises", "BottomAppBarItem_EXERCISES"),
)
