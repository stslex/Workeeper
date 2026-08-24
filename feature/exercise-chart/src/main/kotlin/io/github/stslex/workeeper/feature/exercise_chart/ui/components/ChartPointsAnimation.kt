// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One drawn point. Its position and its presence are **animated state**, and they are the
 * only things the draw phase reads about it.
 *
 * [key] is the point's identity across datasets — the completed session. A metric flip
 * preserves every key; a preset or exercise change may not, which is what
 * the enter/exit policy in [ChartPointsAnimator] exists for.
 *
 * Coordinates are **normalised**: [x] and [y] run 0..1 across the plot band, y downward
 * (0 = top). The draw phase multiplies them into pixels and does nothing else — in
 * particular it never recomputes the value domain, which is what made the old morph
 * self-cancelling (see the KDoc on [ChartPointsAnimator]).
 */
@Stable
internal class AnimatedChartPoint(
    val key: String,
    x: Float,
    y: Float,
    presence: Float,
) {
    val x: Animatable<Float, *> = Animatable(x)
    val y: Animatable<Float, *> = Animatable(y)

    /** 0 = absent, 1 = fully drawn. Drives the fade and the radius scale together. */
    val presence: Animatable<Float, *> = Animatable(presence)
}

/**
 * The chart's single collection of drawn points.
 *
 * A new dataset never replaces the collection — it **retargets** the animations of the
 * points already in it. Three cases, one policy each:
 *
 * - **Matched** (key in both datasets): the [AnimatedChartPoint] survives; x and y are
 *   retargeted. `Animatable.animateTo` cancels a running animation on the same Animatable
 *   and continues **from the current value**, so a retarget landing mid-flight cannot jump.
 *   Interruption safety is a property of the construction, not of a guard.
 * - **Entering** (key only in the new dataset): created already at its target position with
 *   `presence = 0`, animating to 1 — the disc fades and scales in. It joins the series path
 *   immediately, so the line grows to meet it.
 * - **Exiting** (key only in the old dataset): keeps its last position, `presence` animates
 *   to 0, and it is removed on arrival. It leaves the series path at once, so the line
 *   contracts and the disc fades where it stood. A point that re-enters mid-fade is matched
 *   again; its exit coroutine dies on the Animatable's own cancellation before it can
 *   remove anything, which is why no bookkeeping guards the removal.
 *
 * ## Overshoot is forbidden on everything this class animates
 *
 * Every animation here drives **value-encoding geometry**: a point's y *is* the reading.
 * `spring` rises past its target (peak ~1.098 — see `AppMotion`), which would draw the
 * series at a value the data never contained: a lie with a number attached. Data motion is
 * `out` at `base`. `spring` stays legal on geometry that encodes nothing — the scrub bar,
 * the tab indicator.
 */
@Stable
internal class ChartPointsAnimator(
    private val scope: CoroutineScope,
    private val spec: AnimationSpec<Float>,
    initial: List<PointTarget>,
) {

    /** Every point currently drawable, including those on their way out. */
    private val entries = mutableStateListOf<AnimatedChartPoint>()

    /** The current dataset's points, in order. The series path follows exactly these. */
    private val live = mutableStateListOf<AnimatedChartPoint>()

    init {
        // Seeded at rest, synchronously, in the constructor: the draw phase must never see
        // an unpopulated collection, and the golden harness renders one frame without ever
        // advancing the clock — anything seeded mid-flight would be captured mid-flight.
        val seeded = initial.map { target ->
            AnimatedChartPoint(key = target.key, x = target.x, y = target.y, presence = 1f)
        }
        entries.addAll(seeded)
        live.addAll(seeded)
    }

    val drawn: List<AnimatedChartPoint> get() = entries
    val series: List<AnimatedChartPoint> get() = live

    /**
     * Point the collection at [targets].
     *
     * [animate] false snaps everything: first composition, and any host that must render at
     * rest — the golden harness draws one frame and never advances the clock, so an entry
     * left animating would be captured mid-flight.
     */
    fun retarget(targets: List<PointTarget>, animate: Boolean) {
        val matched = LinkedHashMap<String, AnimatedChartPoint>(targets.size)

        targets.forEach { target ->
            val existing = entries.firstOrNull { it.key == target.key }
            if (existing == null) {
                val entering = AnimatedChartPoint(
                    key = target.key,
                    x = target.x,
                    y = target.y,
                    presence = if (animate) 0f else 1f,
                )
                entries.add(entering)
                matched[target.key] = entering
                if (animate) {
                    scope.launch { entering.presence.animateTo(1f, spec) }
                }
            } else {
                matched[target.key] = existing
                if (animate) {
                    scope.launch { existing.x.animateTo(target.x, spec) }
                    scope.launch { existing.y.animateTo(target.y, spec) }
                    scope.launch { existing.presence.animateTo(1f, spec) }
                } else {
                    scope.launch {
                        existing.x.snapTo(target.x)
                        existing.y.snapTo(target.y)
                        existing.presence.snapTo(1f)
                    }
                }
            }
        }

        live.clear()
        live.addAll(matched.values)

        entries.filter { it.key !in matched }.forEach { exiting ->
            if (animate) {
                scope.launch {
                    exiting.presence.animateTo(0f, spec)
                    entries.remove(exiting)
                }
            } else {
                entries.remove(exiting)
            }
        }
    }
}

/** A point's resolved, normalised target. Pure data: no animation, no pixels. */
internal data class PointTarget(
    val key: String,
    val x: Float,
    val y: Float,
)

/**
 * Resolve a dataset into normalised targets — x by index across the band, y against **this
 * dataset's own value domain**.
 *
 * The domain is resolved here, once per dataset, and never again in the draw phase. That is
 * the whole fix for the metric morph: the old canvas tweened raw values and then recomputed
 * `min`/`max` from the half-interpolated vector on every frame, so the drawn shape depended
 * on whichever endpoint was numerically larger. Measured on real folds, the three metrics
 * differ in magnitude by 8x-25x (means 96 / 768 / 2392 on one fixture), which made 90% of
 * the visible change land in the first 9-54ms of a 520ms tween when switching *to* a volume
 * metric — an instant jump — while the reverse direction took 316-408ms and looked correct.
 * Since Вес is both the default and the smallest metric, every tap onto Сет/Сессия
 * collapsed and every tap back onto Вес animated: exactly the reported symptom. In the
 * degenerate case where the metrics are proportional (constant reps make Сессия = N x Сет
 * exactly) the renormalisation cancelled the tween outright — measured movement 0.0000dp
 * across all 520ms.
 *
 * Interpolating in normalised space removes the dependence entirely: the tween runs between
 * two resolved shapes, linearly in the easing's own progress, identically in both
 * directions and for every metric pair. Where two metrics genuinely normalise to the same
 * shape the line correctly does not move — the picture *is* the same, and the canvas draws
 * no axis labels to say otherwise (that is B-registry material, not motion).
 */
internal fun List<ChartPointUiModel>.toTargets(): List<PointTarget> {
    // A single point has no x denominator and no line; the state model already refuses to
    // compose the canvas below MIN_CHART_POINTS, so this is defence, not a branch in use.
    if (size < 2) return emptyList()
    val values = map(ChartPointUiModel::value)
    val min = values.min()
    val range = (values.max() - min).takeIf { it > 0.0 } ?: 1.0
    return mapIndexed { index, point ->
        PointTarget(
            key = point.sessionUuid,
            x = index.toFloat() / (size - 1),
            y = (1.0 - (point.value - min) / range).toFloat(),
        )
    }
}
