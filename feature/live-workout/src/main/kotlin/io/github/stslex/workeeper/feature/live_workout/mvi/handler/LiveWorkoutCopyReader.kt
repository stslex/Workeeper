// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import android.content.Context
import androidx.annotation.StringRes
import dev.zacsweers.metro.Inject

/**
 * D1-A, temporary: reads a live-workout string verbatim, placeholders included, through the app
 * Context's non-formatting `getString(id)`. `ResourceWrapper` cannot, because it formats even with
 * no arguments, so a `%1$d` template throws there. Phase 7.16 PR-M's move commit deletes this class
 * for `StringResolver`'s no-argument path.
 */
@Inject
internal class LiveWorkoutCopyReader(
    private val context: Context,
) {

    fun read(@StringRes id: Int): String = context.getString(id)
}
