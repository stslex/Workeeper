// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.util.Log

internal fun <T> runWearRuntimeUiEvent(event: () -> T): Result<T> =
    runCatching(event).onFailure { failure ->
        if (failure !is Exception) throw failure
        Log.e("WatchRuntimeUi", "Runtime event failed; the read-only surface remains available", failure)
    }
