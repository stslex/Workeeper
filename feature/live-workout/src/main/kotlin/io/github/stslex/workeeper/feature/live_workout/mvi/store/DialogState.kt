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
     * Empty-finish confirm dialog (E1 lock). Triggered when the user taps Finish on a
     * session with no performed sets. Discard CTA is enabled only for ad-hoc trainings;
     * library training sessions get a Continue-editing-only variant — we do not delete
     * library trainings via session cancellation.
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
        /**
         * Visible rows the user never filled in. Rendered as an explicit line in
         * `FinishConfirmDialog` when non-zero, so the discard at finish is stated rather than
         * silent (§6.1). Zero hides the line entirely.
         */
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
