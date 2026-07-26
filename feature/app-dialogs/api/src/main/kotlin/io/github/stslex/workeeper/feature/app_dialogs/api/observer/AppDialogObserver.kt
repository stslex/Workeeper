// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.observer

import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import kotlinx.coroutines.flow.Flow

/**
 * Cross-feature consumer surface for app-dialog choices. Implementations live
 * in `feature/app-dialogs/impl` and are wired at `@SingleIn(AppScope)` — one
 * instance per process on the Metro app graph. Consumer features that need to
 * react to the user's dialog choice (e.g. the post-restart
 * undo/diagnostics/report side-effects) subscribe to [observeUserActions] and
 * call [acknowledgeReaction] once their side-effect has run.
 *
 * **Choice transport.** The choice is delivered as a transient signal — it
 * is NOT persisted in DataStore. Crash-mid-reaction = the dialog flag stays
 * set = on next launch the dialog re-shows and the user re-taps. This is
 * idempotent by construction; there is no replay-on-restart of the
 * reaction.
 *
 * **Acknowledgement contract.** [acknowledgeReaction] clears the dialog's
 * `pending_*` flag (via the repository's `dismiss`). The consumer MUST call
 * it AFTER its side-effect runs (uniform dismiss-after across every variant
 * including the destructive `UndoRestoreConfirmation`+`ConfirmUndo` path).
 * Calling it before the side-effect would clear the dialog while leaving
 * the side-effect to fail silently after — the same failure class App
 * Dialogs exists to prevent.
 *
 * **Subscription lifetime.** The transport is a `replay = 0` `SharedFlow`. A
 * choice emitted while no subscriber is active is silently lost — the producing
 * `AppDialogObserverImpl.emit` does NOT suspend to wait for one (proven by
 * `AppDialogObserverImplTest`), and nothing is replayed to a late subscriber.
 * Consumers therefore MUST be constructed eagerly at
 * `BaseApplication.onCreate`, before any UI dispatch can happen.
 *
 * The ONE production subscriber is `RestoreDialogChoiceObserver`
 * (`feature/recovery`), and it is armed indirectly: it is
 * `@ContributesBinding(AppScope)`-bound to the `RecoveryBootstrap` marker, and
 * `BaseApplication.bootstrapAppDialogObserver()` reads
 * `appGraph.recoveryBootstrap` purely for the side-effect of constructing it —
 * its `init { ... launchIn(scope) }` is what registers the collector. Deleting
 * that accessor, or making the read lazy, silently drops every dialog choice
 * with no compile error and no test failure at this boundary. See
 * `documentation/feature-specs/app-dialogs.md` → "Cross-feature observation".
 */
interface AppDialogObserver {

    /**
     * Stream of user-action choices emitted by `ChooseHandler` when the Host
     * dispatches `Action.Choose(dialog, action)`. Hot (no replay).
     */
    fun observeUserActions(): Flow<AppDialogUserChoice>

    /**
     * Clear the dialog's `pending_*` flag. Called by the consumer AFTER its
     * side-effect for the user's choice has run. The dialog re-shows on
     * next launch if a process crash prevents acknowledgement.
     */
    suspend fun acknowledgeReaction(dialog: AppDialog)
}
