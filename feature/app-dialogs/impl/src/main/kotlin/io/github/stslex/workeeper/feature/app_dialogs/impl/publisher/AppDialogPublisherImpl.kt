// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.publisher

import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `@Singleton` thin facade in front of [AppDialogRepository.publish].
 *
 * The split (Publisher in `api`, Repository in `impl`) keeps producer
 * features depending only on the api module — they never reach into the
 * impl module's DataStore, Resolver, or Store. The facade adds no logic of
 * its own; per-variant dedup and the atomic `dataStore.edit { }` write
 * live in the repository.
 */
@Singleton
internal class AppDialogPublisherImpl @Inject constructor(
    private val repository: AppDialogRepository,
) : AppDialogPublisher {

    override suspend fun publish(dialog: AppDialog) {
        repository.publish(dialog)
    }
}
