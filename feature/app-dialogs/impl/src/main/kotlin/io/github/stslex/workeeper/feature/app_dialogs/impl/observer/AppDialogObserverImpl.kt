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
 * App-singleton implementation of [AppDialogObserver]. Holds the cross-feature
 * choice transport (`MutableSharedFlow`) and the acknowledgement bridge to
 * the repository.
 *
 * **Transport.** A `MutableSharedFlow<AppDialogUserChoice>` with
 * `replay = 0` (no late subscriber catches the event) and
 * `extraBufferCapacity = 64` (absorbs bursts without suspending the
 * emitter). `BufferOverflow.SUSPEND` ensures that once a subscriber IS
 * attached and falls behind, the emitter waits rather than dropping.
 *
 * **Bootstrap dependency.** No subscriber = no delivery, and with `replay = 0`
 * that loss is permanent and silent. This instance is NOT what
 * `BaseApplication` reads to arm the flow — the `AppGraph.appDialogObserverImpl`
 * accessor exists for the intra-module `AppDialogFeature` read.
 *
 * The subscriber is armed indirectly: `RestoreDialogChoiceObserver`
 * (`feature/recovery`) is the sole production collector, it is
 * `@ContributesBinding(AppScope)`-bound to the `RecoveryBootstrap` marker, and
 * `BaseApplication.bootstrapAppDialogObserver()` reads
 * `appGraph.recoveryBootstrap` for the side-effect of constructing it — its
 * `init { ... launchIn(scope) }` registers the collector before any
 * `MainActivity.onCreate`. Removing or deferring THAT accessor read is what
 * would break delivery, not a change to the accessor below.
 *
 * See the [AppDialogObserver] KDoc for the full bootstrap contract.
 *
 * DI: Metro-owned. `@ContributesBinding(AppScope)` binds it to
 * [AppDialogObserver] for the one cross-module consumer that injects the
 * interface — `RestoreDialogChoiceObserver` in `feature/recovery`. `AppGraph`
 * ALSO exposes the concrete type via a self accessor for the intra-module
 * `AppDialogFeature` read (the feature graph takes `AppDialogObserverImpl`, not
 * the interface, because `ChooseHandler` needs the impl-internal `emit`). One
 * `@SingleIn(AppScope)` instance backs both. Public because `@ContributesBinding`
 * on an `internal` class does not aggregate across Gradle modules.
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
