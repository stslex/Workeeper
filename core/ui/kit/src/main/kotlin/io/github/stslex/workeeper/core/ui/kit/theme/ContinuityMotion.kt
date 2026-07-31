// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * **The characterless transit specs — §26's continuity-motion default, as amended.**
 *
 * §26 ("Continuity motion is a class, and it is not §9") splits every animation into two axes, and
 * this file is the default value of only the first:
 *
 * 1. **Transit** — that a property is interpolated at all rather than jumping. That is the class.
 *    Its job is that nothing teleports; it carries no expression, marks no event, and is noticed
 *    only by its absence.
 * 2. **Character** — the shape of that interpolation: which curve carries it, or a second
 *    property animated alongside it. **Not this class.** Character stays governed by §5's
 *    overshoot rule, which permits overshoot on geometry encoding nothing and forbids it on
 *    colour, alpha, bounded fractions and values.
 *
 * The reader's test needs no one's opinion: delete an animation and ask whether something jumps.
 * Yes → transit. No → something still moves smoothly without it → character, and character owes
 * its own ledger row before it ships.
 *
 * ## There are two specs, and the split is by WHAT IS INTERPOLATED — never by site
 *
 * This file used to publish one `continuitySpec`. It now publishes two, and the distinction a
 * caller must make is a question about the **property**, not about the component:
 *
 * - Are you interpolating a **position, a size, a placement**? → [continuityPositionalSpec].
 * - Are you interpolating an **alpha**? → [continuityAlphaSpec].
 *
 * A call site does not get to prefer one. `Modifier.animateItem` takes **both** in a single call —
 * `placementSpec` is positional and `fadeInSpec` / `fadeOutSpec` are alpha — which is the clearest
 * possible statement that the axis is the property and not the widget. There is deliberately **no
 * neutral third name** left in this file: removing the un-suffixed `continuitySpec` is what makes
 * the question unavoidable at every call site instead of answerable by reaching for the default.
 *
 * ## Why the split, measured rather than argued
 *
 * `out` is `cubic-bezier(0.16, 1, 0.3, 1)`, near-expo. Driving a fade with it puts **83 % of the
 * alpha travel in the first five frames** of a 260ms tween and leaves the remainder below 8-bit
 * visibility. Stepped on device at 60fps, the list top bar's crossfade matched its declared spec
 * frame for frame — and still read as ~85ms. So the class's characterless default was invisible to
 * the point of being indistinguishable from **no transit at all**, which is §10.4's own entry
 * arriving from the opposite direction: there a missing transit and a broken one leave identical
 * evidence, and a correct-but-imperceptible one joined them.
 *
 * Lengthening does not fix it, and that measurement is the load-bearing one. Perceived crossfade
 * duration tracks the **middle** of the curve, and `out`'s middle is already over. Taking the time
 * alpha spends in the perceptible band (0.15 → 0.85):
 *
 * ```
 * out    @260 (was)      64 ms   3.8 frames
 * out    @520 (double)  128 ms   7.7 frames
 * linear @160           112 ms   6.7 frames
 * linear @260 (is)      182 ms  10.9 frames
 * ```
 *
 * Doubling `out` to the `slow` rung buys 7.7 frames; `linear` at **under a third** of that duration
 * buys 6.7. The front of the curve is spending the budget, so no duration recovers it.
 *
 * ## Why each side keeps what it keeps
 *
 * **Positional keeps `out`.** For a thing that moved, a decelerating arrival is how it comes to
 * rest — the front-loading is the correctness there, not the defect. `placementSpec` is the member.
 *
 * **Alpha takes `linear`, chosen for being nothing rather than for being good.** A fade has no
 * arrival to decelerate into; it has only "how long were both layers legible". Any easing on alpha
 * is character by the class's own definition, so the characterless default for alpha is the curve
 * with no shape. §5 is then satisfied by construction — `linear` cannot leave `[0, 1]` — and the
 * `fadedOut` rule stays satisfied because no colour is interpolated at any of these sites.
 *
 * Under `linear`, **perceived duration equals declared duration**. That is the whole of what the
 * amendment buys, and it is why the remaining dial is a single number a device can judge rather
 * than a curve name it cannot. [AppMotion.base] is the value; 200 and 160 stay on the table.
 *
 * ## Why `spring` is wrong for every site below
 *
 * Not because the class bans overshoot — it does not; axis 2 settles that. Because
 * [AppMotion.spring] peaks at ~1.098 (sampled, see [AppMotion]) and both specs here drive
 * bounded quantities: alpha, and an item's placement inside a settled list. A fade driven past 1.0
 * either clamps — flattening the overshoot it was chosen for — or produces a frame outside both
 * endpoints, which is the Oklab flash's defect (§27) reached by another route.
 *
 * The FAB's `border-radius` morph is a transit carrying `spring` (§5-legal: unbounded geometry
 * encoding nothing) and the nav pill's stretch is character on a second element; both are recorded
 * decisions with their own ledger rows, and neither is a counterexample to this file.
 *
 * Callers take the composable overloads. The [AppMotion] overloads exist so both specs can be
 * asserted in a plain JVM test, without a composition — and both **are**, with `spring` run through
 * each as a negative control, because the cost of two specs is that one assertion no longer covers
 * the class.
 */
fun <T> continuityPositionalSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = motion.out,
)

/** The positional half of the class, at a call site. See [continuityPositionalSpec]. */
@Composable
fun <T> continuityPositionalSpec(): TweenSpec<T> = continuityPositionalSpec(AppUi.motion)

/**
 * The alpha half of the class. See the file KDoc — in particular that choosing between this and
 * [continuityPositionalSpec] is a question about the property being interpolated, never about which
 * component is asking.
 */
fun <T> continuityAlphaSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = motion.linear,
)

/** The alpha half of the class, at a call site. See [continuityAlphaSpec]. */
@Composable
fun <T> continuityAlphaSpec(): TweenSpec<T> = continuityAlphaSpec(AppUi.motion)
