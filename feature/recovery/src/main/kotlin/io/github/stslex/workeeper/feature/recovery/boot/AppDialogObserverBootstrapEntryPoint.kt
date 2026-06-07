// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.boot

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint used by `BaseApplication.onCreate` to eagerly construct the
 * `@Singleton` cross-feature dialog reactor (which lives `internal` to
 * `feature/recovery`). Construction triggers the reactor's
 * `init { observer.observeUserActions().launchIn(scope) }` block, registering
 * a subscriber on the SharedFlow BEFORE `MainActivity.onCreate` runs.
 *
 * Returns a [RecoveryBootstrap] (the reactor's marker interface) rather than
 * the concrete observer class so the bootstrap path can stay cross-module
 * without forcing the observer to be `public`. The return value is intended
 * to be discarded by the caller — the side-effect of singleton construction
 * is what arms the subscriber.
 *
 * Spec: `documentation/feature-specs/app-dialogs.md` → "Bootstrap (BLOCKER 1)".
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppDialogObserverBootstrapEntryPoint {
    fun recoveryBootstrap(): RecoveryBootstrap
}
