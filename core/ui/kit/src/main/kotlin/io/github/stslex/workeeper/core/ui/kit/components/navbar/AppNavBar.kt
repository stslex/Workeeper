// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.navbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import io.github.stslex.workeeper.core.ui.kit.components.INDICATOR_STRETCH
import io.github.stslex.workeeper.core.ui.kit.components.rememberPressScale
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppMotion
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import kotlin.math.abs
import kotlin.math.min

/**
 * The v3 bottom navigation — `pass2d.html` `#s-nav`, the `.nb.track.slide` variant. See §26
 * "Bottom navigation" in documentation/feature-specs/v3-redesign-spec.md.
 *
 * GUARD: the hairline is an overlay on the top edge, not a column child, so the bar's height stays
 * exactly [AppDimension.BottomNavBar.heightWithInsets]. Fires no haptic; the caller does.
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
            // `maxWidth` is inside the track's horizontal padding; the mockup's `k` divides by
            // the outer box, so the padding is added back.
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

            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                items.forEachIndexed { index, item ->
                    // ONE TIMELINE: tint and pill are one state change, so both run for
                    // NAV_PILL_TRAVEL. `NavPillTest` asserts the two are equal, not each pinned.
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
                    // NO RIPPLE: neither mockup draws one, and `selectable` would otherwise take
                    // `LocalIndication`.
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressScale by rememberPressScale(interactionSource)
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(pillShape)
                            // `selectable`, not `clickable`: TalkBack and the test tree read
                            // `Selected` / `Role.Tab` from here and nowhere else.
                            .selectable(
                                selected = index == selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                            ) { onSelect(index) }
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
 * `.nb.slide .ind` (offset) wrapping `.nb.slide .ind i` (fill + stretch) — two elements because one
 * transform cannot carry both. The stretch keys off [selectedIndex], which still carries the delta.
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
    // `AppUi.motion` is a @Composable getter, so the curve is read here and captured.
    val out = AppUi.motion.out
    val animatedOffset by animateDpAsState(
        targetValue = offset,
        animationSpec = navPillOffsetSpec(AppUi.motion),
        label = "nav-pill-offset",
    )

    val stretch = remember { Animatable(1f) }
    // Seeded with the initial selection so first composition does not read a 0 -> n jump.
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    // GUARD: latch the origin inside the effect; deriving it during composition flips it back
    // mid-travel as soon as `previousIndex` catches up.
    var stretchOrigin by remember { mutableStateOf(LEADING_EDGE_RIGHT) }

    LaunchedEffect(selectedIndex) {
        val jumped = selectedIndex - previousIndex
        previousIndex = selectedIndex
        if (jumped == 0) return@LaunchedEffect
        // `transform-origin` on the LEADING edge, so the tail lags and catches up.
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

/** The pill's transit — extracted so the curve is assertable, not only the duration. */
internal fun <T> navPillOffsetSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = NAV_PILL_TRAVEL,
    easing = motion.travel,
)

/** `scaleX` peak for a jump of [travel] across [barWidth] — `1 + 0.30 * min(delta/W, 1)`. */
internal fun navPillStretchPeak(travel: Dp, barWidth: Dp): Float {
    if (barWidth.value <= 0f) return 1f
    val k = min(abs(travel.value) / barWidth.value, 1f)
    return 1f + INDICATOR_STRETCH * k
}

/** `.nb{padding:5px 8px}` — the 8px half. */
private val TRACK_PADDING_HORIZONTAL = AppDimension.Space.sm

/** `.nb{padding:5px 8px}` — the 5px half, on the 4dp rung like `.topbar`'s identical 5px. */
private val TRACK_PADDING_VERTICAL = AppDimension.Space.xs

/** `.nb{gap:4px}`. */
private val ITEM_GAP = AppDimension.Space.xs

/**
 * `.nb.slide .ind{transition:transform 340ms}` — §26 "Nav pill motion". Not on the motion scale;
 * this member's own recorded number.
 */
internal const val NAV_PILL_TRAVEL: Int = 340

/**
 * The icon tint's duration, defined as the pill's travel. A separate constant so `NavPillTest` can
 * assert the two are equal — the defect it closes is divergence.
 */
internal const val NAV_ITEM_TINT_DURATION: Int = NAV_PILL_TRAVEL

/**
 * `@keyframes gel{42%{…}}` — the peak's position in the 340ms timeline, written once.
 *
 * GUARD: do not unify this with the tabs' `GEL_PEAK_FRACTION`; that is motion work (B31).
 */
internal const val NAV_PILL_STRETCH_PEAK_MS: Int = 143

/** `--sx:(1+0.30*k)` — the stretch coefficient in `nbPick()`. */
internal const val NAV_PILL_STRETCH: Float = INDICATOR_STRETCH

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
