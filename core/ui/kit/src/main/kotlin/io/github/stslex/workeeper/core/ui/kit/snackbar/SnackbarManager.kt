// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object SnackbarManager {

    /**
     * The toast queue: unbounded, FIFO, and never dropping while the process lives.
     * [AppSnackbarModel.onDismissed] carries a deferred delete's COMMIT (ED11), not just
     * feedback — a dropped entry is not a stale toast skipped, it is a confirmed delete
     * that silently never runs after the screen that promised it popped. So neither a full
     * buffer nor a burst may evict: entries are tiny, every producer is a user gesture,
     * and the single collector (`App.kt`) drains one per toast lifetime. DO NOT cap this
     * queue or give it an overflow policy — any bound reintroduces the eviction, and
     * [SnackbarManagerTest] holds a burst case that goes red on one. Process death cancels
     * everything queued — D-OPEN-10's recorded shape, unchanged.
     */
    private val queue = Channel<AppSnackbarModel>(capacity = Channel.UNLIMITED)

    val snackbar: Flow<AppSnackbarModel> = queue.receiveAsFlow()

    fun showSnackbar(model: AppSnackbarModel) {
        queue.trySend(model)
    }

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        action: () -> Unit = {},
    ): Unit = showSnackbar(
        AppSnackbarModel(
            message = message,
            actionLabel = actionLabel,
            action = action,
        ),
    )
}
