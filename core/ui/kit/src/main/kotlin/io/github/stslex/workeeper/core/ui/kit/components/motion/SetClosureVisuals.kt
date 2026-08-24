// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.motion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The merged set-closure automaton (v3 §9): one automaton, with [isRecord] as a colour parameter.
 *
 * GUARD: geometry runs on `AppUi.motion.spring` and colour on `.out` — spring overshoots past 1.0,
 * which is meaningful on geometry and invalid on a colour lerp.
 */
@Immutable
data class SetClosureVisuals(
    /**
     * 0 = resting circle, 1 = fully closed plate. Geometry, so it may transiently exceed 1.0 on
     * the spring overshoot — read it as "how closed", not as a fraction to index into.
     */
    val closedFraction: Float,
    /**
     * How much of the checkmark is stroked in: 0 = undrawn (`stroke-dashoffset: 26`), 1 = fully
     * drawn. A bounded fraction, so `out`, and it carries the mockup's own 60ms delay.
     */
    val tickProgress: Float,
    /** Row flash opacity, 1 at the moment of closure decaying to 0. Colour, so `out`. */
    val flashAlpha: Float,
    /** The accent the moment resolves to: `max` for a closure, `molten` for a record. */
    val accent: Color,
)

/**
 * Drives [SetClosureVisuals] from the two booleans that describe a set row. [isRecord] only ever
 * selects a colour, never a different animation, duration or order.
 */
@Composable
fun rememberSetClosureVisuals(
    isDone: Boolean,
    isRecord: Boolean,
): SetClosureVisuals {
    // Geometry: `spring`, overshoot intended.
    val closedFraction by animateFloatAsState(
        targetValue = if (isDone) 1f else 0f,
        animationSpec = tween(
            durationMillis = AppUi.motion.base,
            easing = AppUi.motion.spring,
        ),
        label = "setClosure-closedFraction",
    )
    // Bounded fraction: `out`, never `spring`. Carries the mockup's 60ms delay
    // (`transition: stroke-dashoffset 260ms var(--e-out) 60ms`).
    val tickProgress by animateFloatAsState(
        targetValue = if (isDone) 1f else 0f,
        animationSpec = tween(
            durationMillis = AppUi.motion.base,
            delayMillis = TICK_DELAY_MS,
            easing = AppUi.motion.out,
        ),
        label = "setClosure-tickProgress",
    )
    // Colour: `out`. The flash is TRANSIENT — it peaks at closure and decays — so it cannot be a
    // state-driven tween, which would be a permanently-zero constant that animates nothing.
    val flash = remember { Animatable(0f) }
    // `AppUi.motion` is a composable accessor, so the spec is built here and captured — it
    // cannot be read from inside the effect body.
    val flashSpec = tween<Float>(
        durationMillis = AppUi.motion.slow,
        easing = AppUi.motion.out,
    )
    // GUARD: the flash marks the TRANSITION into done. `LaunchedEffect` also runs on first
    // composition, so `wasDone` is seeded from the current value to keep that composition a no-op.
    val wasDone = remember { mutableStateOf(isDone) }
    LaunchedEffect(isDone) {
        val closedJustNow = closedJustNow(previous = wasDone.value, current = isDone)
        wasDone.value = isDone
        when {
            closedJustNow -> {
                flash.snapTo(1f)
                flash.animateTo(targetValue = 0f, animationSpec = flashSpec)
            }

            !isDone -> flash.snapTo(0f)
        }
    }
    val flashAlpha = flash.value
    val accent by animateColorAsState(
        targetValue = if (isRecord) AppUi.colors.molten.solid else AppUi.colors.accent,
        animationSpec = tween(
            durationMillis = AppUi.motion.base,
            easing = AppUi.motion.out,
        ),
        label = "setClosure-accent",
    )
    return SetClosureVisuals(
        closedFraction = closedFraction,
        tickProgress = tickProgress,
        flashAlpha = flashAlpha,
        accent = accent,
    )
}

/**
 * `transition: stroke-dashoffset 260ms var(--e-out) 60ms` — the tick waits 60ms so the plate is
 * visibly filling before the stroke starts, rather than the two reading as one instant repaint.
 */
private const val TICK_DELAY_MS = 60

// GUARD: no `markScale` here — `AppCheckmarkButton` already grows the plate off `closedFraction`.

/**
 * The flash gate: a pulse belongs to the moment a set CLOSES, not to the state of being closed.
 * Extracted so `SetClosureFlashTest` can pin it without a frame clock.
 */
internal fun closedJustNow(previous: Boolean, current: Boolean): Boolean = current && !previous
