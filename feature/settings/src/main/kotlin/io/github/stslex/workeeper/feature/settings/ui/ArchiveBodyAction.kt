// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Segment

internal sealed interface ArchiveBodyAction {

    data object BackClick : ArchiveBodyAction

    data class SegmentChange(val segment: Segment) : ArchiveBodyAction

    data class RestoreClick(val item: ArchivedItem) : ArchiveBodyAction

    data class PermanentDeleteClick(val item: ArchivedItem) : ArchiveBodyAction

    data object DeleteConfirm : ArchiveBodyAction

    data object DeleteDismiss : ArchiveBodyAction
}

internal fun ArchiveBodyAction.toAction(): Action = when (this) {
    ArchiveBodyAction.BackClick -> Action.Navigation.Back
    is ArchiveBodyAction.SegmentChange -> Action.Click.OnSegmentChange(segment)
    is ArchiveBodyAction.RestoreClick -> Action.Click.OnRestoreClick(item)
    is ArchiveBodyAction.PermanentDeleteClick -> Action.Click.OnPermanentDeleteClick(item)
    ArchiveBodyAction.DeleteConfirm -> Action.Click.OnDeleteConfirm
    ArchiveBodyAction.DeleteDismiss -> Action.Click.OnDeleteDismiss
}
