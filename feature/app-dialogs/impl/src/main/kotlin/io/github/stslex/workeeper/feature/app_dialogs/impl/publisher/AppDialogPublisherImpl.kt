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
 * App-scoped facade over [AppDialogRepository.publish] so producer features depend on the api
 * module only; it adds no logic of its own.
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
