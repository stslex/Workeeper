// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.publisher

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository

/**
 * Thin facade in front of [AppDialogRepository.publish].
 *
 * The split (Publisher in `api`, Repository in `impl`) keeps producer
 * features depending only on the api module — they never reach into the
 * impl module's DataStore, Resolver, or Store. The facade adds no logic of
 * its own; per-variant dedup and the atomic `dataStore.edit { }` write
 * live in the repository.
 *
 * DI (App-Scope Collapse Step 3): Metro-owned, `@ContributesBinding(AppScope)`
 * binds it to [AppDialogPublisher] for the cross-module producer readers
 * (settings). Public because `@ContributesBinding` on an `internal` class does
 * not aggregate across Gradle modules.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AppDialogPublisherImpl(
    private val repository: AppDialogRepository,
) : AppDialogPublisher {

    override suspend fun publish(dialog: AppDialog) {
        repository.publish(dialog)
    }
}
