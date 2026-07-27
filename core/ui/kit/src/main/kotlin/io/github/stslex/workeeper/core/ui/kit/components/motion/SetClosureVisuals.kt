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
 * The merged set-closure automaton (v3 §9).
 *
 * §9 allows exactly two wow moments — **set closure** (the mark morphs from circle to filled
 * plate, the row flashes, the rail segment fills) and **personal record** (a molten unfurl).
 * They are MERGED here rather than sequenced, and the merge is the whole point of this file:
 *
 * - a record almost always *is* a set closure, so the two fire together far more often than
 *   apart;
 * - the structural half cannot be suppressed — the segment must fill, because the rail's
 *   correctness depends on it — so "play the record animation instead" is not available;
 * - queueing them would double the duration of the single most frequent action in the app.
 *
 * So there is one automaton and [isRecord] is a **parameter**, not a second code path. The
 * morph, the flash and the segment are identical either way; only the colour they resolve to
 * changes, from `max` to `molten`.
 *
 * ### The curve split is structural, not conventional
 *
 * §5's `spring` easing overshoots past 1.0 (peak ~1.098). Overshoot is meaningful on geometry
 * — a plate that springs slightly past its size and settles reads as physical — and is
 * **invalid on colour**, where a lerp past 1.0 clamps or produces garbage.
 *
 * This type enforces that by construction rather than by comment: every geometry value is
 * driven by [AppUi.motion.spring] and every colour value by [AppUi.motion.out], and the two
 * are computed in one place so a caller cannot accidentally hand a colour to the springy
 * curve. `AppMotion`'s own KDoc states the same constraint from the token side.
 */
@Immutable
data class SetClosureVisuals(
    /**
     * 0 = resting circle, 1 = fully closed plate. **Geometry** — driven by `spring`, and may
     * transiently exceed 1.0 on the overshoot. Consumers must tolerate that: read it as
     * "how closed", not as a fraction to index into.
     */
    val closedFraction: Float,
    /** Mark scale. **Geometry** — `spring`, overshoots past 1.0 by design. */
    val markScale: Float,
    /**
     * Row flash opacity, 1 at the moment of closure decaying to 0. **Colour** — driven by
     * `out`, which lands on exactly 1.0 and never overshoots.
     */
    val flashAlpha: Float,
    /**
     * The accent the whole moment resolves to: `max` for an ordinary closure, `molten` for a
     * record. This is the single place [isRecord] changes anything.
     */
    val accent: Color,
)

/**
 * Drives [SetClosureVisuals] from the two booleans that describe a set row.
 *
 * [isRecord] only ever selects a colour — it never selects a different animation, a different
 * duration, or a different order. That is §9's "one automaton, record as a parameter" stated
 * as a signature.
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
    val markScale by animateFloatAsState(
        targetValue = if (isDone) 1f else RESTING_SCALE,
        animationSpec = tween(
            durationMillis = AppUi.motion.base,
            easing = AppUi.motion.spring,
        ),
        label = "setClosure-markScale",
    )
    // Colour: `out`. A lerp driven by an overshooting curve would clamp or garbage.
    //
    // The flash is TRANSIENT — it peaks at the instant of closure and decays — so it cannot be
    // a state-driven tween like the two above. Those interpolate between two resting values;
    // this one has no resting value to interpolate to, and writing it as `animateFloatAsState`
    // would produce a permanently-zero constant that animates nothing.
    val flash = remember { Animatable(0f) }
    // `AppUi.motion` is a composable accessor, so the spec is built here and captured — it
    // cannot be read from inside the effect body.
    val flashSpec = tween<Float>(
        durationMillis = AppUi.motion.slow,
        easing = AppUi.motion.out,
    )
    // The flash marks the TRANSITION into done, not the state of being done. `LaunchedEffect`
    // also runs on first composition, so keying it on `isDone` alone would flash every
    // already-completed set whenever an active session loads or a completed card is collapsed
    // and reopened — a burst of wow moments for work the user finished minutes ago.
    //
    // `wasDone` is seeded from the CURRENT value, so the first composition is always a no-op
    // whichever state the row arrives in.
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
        markScale = markScale,
        flashAlpha = flashAlpha,
        accent = accent,
    )
}

/** The mark rests slightly under full size so closure reads as a commit, not a repaint. */
private const val RESTING_SCALE = 0.92f

/**
 * The flash gate: a pulse belongs to the moment a set CLOSES, not to the state of being closed.
 *
 * Extracted so `SetClosureFlashTest` can pin it without a frame clock — the surrounding
 * composable needs one, this predicate does not.
 */
internal fun closedJustNow(previous: Boolean, current: Boolean): Boolean = current && !previous
