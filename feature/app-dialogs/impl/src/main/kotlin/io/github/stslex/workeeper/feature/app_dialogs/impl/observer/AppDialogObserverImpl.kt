// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.observer

import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `@Singleton` implementation of [AppDialogObserver]. Holds the cross-feature
 * choice transport (`MutableSharedFlow`) and the acknowledgement bridge to
 * the repository.
 *
 * **Transport.** A `MutableSharedFlow<AppDialogUserChoice>` with
 * `replay = 0` (no late subscriber catches the event) and
 * `extraBufferCapacity = 64` (absorbs bursts without suspending the
 * emitter). `BufferOverflow.SUSPEND` ensures we never silently drop an
 * emission — the emitter waits for the subscriber to drain.
 *
 * **Bootstrap dependency.** No subscriber = no delivery. Construct via the
 * Hilt `@EntryPoint` accessor at `BaseApplication.onCreate` so the
 * subscriber-side handler's `init { ... launchIn(scope) }` runs before any
 * `MainActivity.onCreate` and registers a collector. See the [AppDialogObserver]
 * KDoc for the full bootstrap contract.
 */
@Singleton
internal class AppDialogObserverImpl @Inject constructor(
    private val repository: AppDialogRepository,
) : AppDialogObserver {

    private val choices: MutableSharedFlow<AppDialogUserChoice> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    private val choicesFlow: SharedFlow<AppDialogUserChoice> = choices.asSharedFlow()

    override fun observeUserActions(): SharedFlow<AppDialogUserChoice> = choicesFlow

    override suspend fun acknowledgeReaction(dialog: AppDialog) {
        repository.dismiss(dialog)
    }

    /**
     * Producer-side emit, used by `ChooseHandler` inside the Store. Internal
     * to the impl module — the api surface only exposes the consumer side.
     */
    internal suspend fun emit(choice: AppDialogUserChoice) {
        choices.emit(choice)
    }

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}
