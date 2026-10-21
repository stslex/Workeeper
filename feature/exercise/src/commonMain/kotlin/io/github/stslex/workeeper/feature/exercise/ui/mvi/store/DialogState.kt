// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.kit.components.dialog.BlockedArchiveItem

/** Every dialog on the Exercise screen as one sealed type — Rule 4 of compose-state-discipline. */
@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    /** Edit-mode discard confirmation; `target` decides pop-the-screen vs flip-to-Read. */
    @Stable
    data class DiscardConfirm(val target: ExerciseStore.DiscardTarget) : DialogState

    /** "Switching to weightless clears typed weights" confirm; strings are pre-resolved. */
    @Stable
    data class TypeChangeConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /** Archiving an exercise still referenced by an active training; [item] carries the labels. */
    @Stable
    data class ArchiveBlocked(val item: BlockedArchiveItem) : DialogState

    @Stable
    data class PermanentDeleteConfirm(
        val title: String,
        val body: String,
        val impactSummary: String,
        val confirmLabel: String,
    ) : DialogState

    /** Camera-vs-Gallery picker shown after the user taps "Add image". */
    @Stable
    data object ImageSourcePicker : DialogState

    /** What counts as a record — opened from the history record row's PR tag. */
    @Stable
    data object PrExplainer : DialogState

    /** Camera permission denied → "Open Settings or Cancel" prompt. */
    @Stable
    data object PermissionDenied : DialogState

    /** Track Now conflict: an active session exists for a different exercise. */
    @Stable
    data class ActiveSessionConflict(
        val sessionUuid: String,
        val activeSessionName: String,
        val progressLabel: String,
    ) : DialogState
}

/** The topbar `⋮` overflow, Store-homed (Rule 4) and kept separate from [DialogState]. */
@Stable
sealed interface BottomSheetState {

    @Stable
    data object Hidden : BottomSheetState

    /** `⋮` → Изменить · В архив · Удалить навсегда (the last only when deletable). */
    @Stable
    data object DetailMenu : BottomSheetState

    /** The editor plan head's `(i)` (ED8): what a default plan is for, in a sheet. */
    @Stable
    data object PlanInfo : BottomSheetState

    /** ED7: the tag picker's sheet — search, chips, create row, «Готово»; selection is live. */
    @Stable
    data object TagPicker : BottomSheetState
}
