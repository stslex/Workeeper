// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.paging

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.stslex.workeeper.core.ui.kit.theme.AppMotion
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.coroutines.delay

/**
 * Whether a loading treatment should be on screen, as opposed to whether the data is loading:
 * appear delay [AppMotion.fast], then a minimum hold of [AppMotion.base] once shown.
 */
@Composable
private fun rememberLoadingVisible(loading: Boolean): Boolean {
    val motion = AppUi.motion
    var visible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(loading) {
        when (val step = loadingStep(loading, visible, shownAt, SystemClock.elapsedRealtime(), motion)) {
            is LoadingStep.ShowAfter -> {
                delay(step.delayMillis)
                shownAt = SystemClock.elapsedRealtime()
                visible = true
            }

            is LoadingStep.HideAfter -> {
                delay(step.delayMillis)
                visible = false
            }

            LoadingStep.Nothing -> Unit
        }
    }
    return visible
}

/**
 * The surface a list should draw: during the deferral window the last settled verdict, or `null`
 * before one exists. See documentation/design-system.md.
 *
 * GUARD: call this where it outlives the loading state and branch only on its result; re-deriving
 * `surface == loadingSurface` beside it drops the minimum hold.
 */
@Composable
fun <T : Any> rememberDeferredSurface(surface: T, loadingSurface: T): T? {
    val visible = rememberLoadingVisible(surface == loadingSurface)
    // The outgoing frame, kept because Compose does not keep it: a `null` here deletes what is
    // drawn rather than leaving it, blanking the region on transitions such as retry.
    var lastSettled by remember { mutableStateOf<T?>(null) }
    if (surface != loadingSurface) lastSettled = surface
    return deferredSurface(
        surface = surface,
        loadingSurface = loadingSurface,
        visible = visible,
        lastSettled = lastSettled,
    )
}

/**
 * Which body a list screen draws, from [rememberDeferredSurface]'s verdict. Rows and the loading
 * treatment are alternatives, not layers.
 */
enum class ListBody {
    /** The list's own items. */
    ROWS,

    /** The empty region: loading treatment, error, or empty state. */
    REGION,
}

/** See [ListBody]. Pure. */
fun <T : Any> listBody(surface: T?, contentSurface: T): ListBody =
    if (surface == contentSurface) ListBody.ROWS else ListBody.REGION

/** See [rememberDeferredSurface]. Pure; `lastSettled` is the last non-loading verdict drawn. */
internal fun <T : Any> deferredSurface(
    surface: T,
    loadingSurface: T,
    visible: Boolean,
    lastSettled: T?,
): T? = when {
    visible -> loadingSurface
    surface == loadingSurface -> lastSettled
    else -> surface
}

/**
 * What the deferral should do next — pure, so both durations are assertable.
 *
 * GUARD: do not inline these delays back into the composable; no test in this module can reach a
 * duration written straight into a `delay(...)`.
 */
internal sealed interface LoadingStep {

    /** Loading began and nothing is shown: wait, then show. */
    data class ShowAfter(val delayMillis: Long) : LoadingStep

    /** Loading ended while shown: wait out the remaining minimum, then hide. */
    data class HideAfter(val delayMillis: Long) : LoadingStep

    /** Already in the right state. */
    data object Nothing : LoadingStep
}

/** See [LoadingStep]. Pure. */
internal fun loadingStep(
    loading: Boolean,
    visible: Boolean,
    shownAtMillis: Long,
    nowMillis: Long,
    motion: AppMotion,
): LoadingStep = when {
    loading && !visible -> LoadingStep.ShowAfter(motion.fast.toLong())
    !loading && visible -> LoadingStep.HideAfter(
        loadingHoldRemaining(shownAtMillis, nowMillis, motion),
    )

    else -> LoadingStep.Nothing
}

/**
 * Milliseconds the loading treatment must still be held, clamped at zero.
 *
 * GUARD: feed this from `SystemClock.elapsedRealtime()`; a wall-clock adjustment would land
 * directly in the subtraction.
 */
internal fun loadingHoldRemaining(
    shownAtMillis: Long,
    nowMillis: Long,
    motion: AppMotion,
): Long = (motion.base.toLong() - (nowMillis - shownAtMillis)).coerceAtLeast(0L)
