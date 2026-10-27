package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

@Stable
sealed interface DialogState {

    @Stable
    data object Hidden : DialogState

    @Stable
    data class DeleteDialog(
        val sessionName: String,
        val progressLabel: String,
    ) : DialogState

    /**
     * Empty-finish confirm dialog: Finish tapped with no performed sets. Discard is offered
     * only for ad-hoc trainings, never for library ones.
     */
    @Stable
    data class EmptyFinish(
        val canDiscard: Boolean,
        val confirmLabel: String,
        val dismissLabel: String,
    ) : DialogState

    @Stable
    sealed interface ConfirmDialog : DialogState {

        val title: String
        val body: String
        val confirmLabel: String
        val dismissLabel: String

        @Stable
        data class ResetSets(
            override val title: String,
            override val body: String,
            override val confirmLabel: String,
            override val dismissLabel: String,
            val exerciseUuid: String,
        ) : ConfirmDialog

        @Stable
        data class CancelSession(
            override val title: String,
            override val body: String,
            override val confirmLabel: String,
            override val dismissLabel: String,
        ) : ConfirmDialog
    }

    @Stable
    data class FinishSession(
        val durationMillis: Long,
        val durationLabel: String,
        val exercisesSummaryLabel: String,
        val setsLoggedLabel: String,
        val newPersonalRecords: ImmutableList<NewPrEntry>,
        /** Visible rows never filled in; shown when non-zero so the discard is stated (§6.1). */
        val unfilledSetCount: Int = 0,
        val requiresName: Boolean = false,
        val nameDraft: String = "",
        val nameLabel: String = "",
        val namePlaceholder: String = "",
        val nameError: String? = null,
        val confirmEnabled: Boolean = true,
    ) : DialogState {

        @Stable
        data class NewPrEntry(
            val exerciseUuid: String,
            val exerciseName: String,
            val displayLabel: String,
        )
    }
}
