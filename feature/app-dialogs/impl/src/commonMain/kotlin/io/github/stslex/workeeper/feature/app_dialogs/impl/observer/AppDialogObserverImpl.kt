// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.observer

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App singleton implementing [AppDialogObserver]: the cross-feature user-choice transport plus
 * the acknowledgement bridge to the repository. See the app-dialogs spec.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AppDialogObserverImpl(
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

    /** Producer-side emit for `ChooseHandler`; the api exposes only the consumer side. */
    internal suspend fun emit(choice: AppDialogUserChoice) {
        choices.emit(choice)
    }

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}
