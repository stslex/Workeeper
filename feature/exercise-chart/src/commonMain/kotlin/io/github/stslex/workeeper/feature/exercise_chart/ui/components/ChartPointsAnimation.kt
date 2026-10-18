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
 * One drawn point: normalised x/y (0..1, y downward) and presence, all animated state.
 * [key] is its identity across datasets — the completed session.
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
 * The chart's single collection of drawn points; a new dataset retargets them (matched,
 * entering, exiting) instead of replacing the collection.
 * GUARD: no overshoot on these animations — a point's y *is* the reading.
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
        // Seeded at rest: the golden harness renders one frame without advancing the clock.
        val seeded = initial.map { target ->
            AnimatedChartPoint(key = target.key, x = target.x, y = target.y, presence = 1f)
        }
        entries.addAll(seeded)
        live.addAll(seeded)
    }

    val drawn: List<AnimatedChartPoint> get() = entries
    val series: List<AnimatedChartPoint> get() = live

    /**
     * Point the collection at [targets]. [animate] false snaps everything: first composition,
     * and hosts that must render at rest such as the golden harness.
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
 * Resolve a dataset into normalised targets — x by index across the band, y against this
 * dataset's own value domain, resolved once here and never again in the draw phase.
 */
internal fun List<ChartPointUiModel>.toTargets(): List<PointTarget> {
    // Defence: the state model already refuses to compose the canvas below MIN_CHART_POINTS.
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
