// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanDraftReducer
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorUIMapper.formatPlanSummary
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingHandlerStore
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingScope
import io.github.stslex.workeeper.feature.single_training.domain.SingleTrainingInteractor
import io.github.stslex.workeeper.feature.single_training.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.model.ExercisePlanDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.mvi.mapper.TagUiMapper.toDomain
import io.github.stslex.workeeper.feature.single_training.mvi.mapper.TagUiMapper.toUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.DialogState
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Event
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.PickerState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import io.github.stslex.workeeper.core.ui.plan_editor.R as CoreEditorR

@Suppress("TooManyFunctions", "LongMethod", "LargeClass")
@SingleIn(SingleTrainingScope::class)
internal class ClickHandler @Inject constructor(
    private val interactor: SingleTrainingInteractor,
    private val resourceWrapper: ResourceWrapper,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
    store: SingleTrainingHandlerStore,
) : Handler<Action.Click>, SingleTrainingHandlerStore by store {

    override fun invoke(action: Action.Click) {
        when (action) {
            Action.Click.OnBackClick -> processBackClick()
            Action.Click.OnDetailMenuClick -> processDetailMenuClick()
            Action.Click.OnDetailMenuDismiss -> processCloseDialog()
            Action.Click.OnEditClick -> processEditClick()
            Action.Click.OnArchiveClick -> processArchiveClick()
            Action.Click.OnPermanentDeleteClick -> processPermanentDeleteMenu()
            Action.Click.OnPermanentDeleteConfirm -> processPermanentDeleteConfirm()
            Action.Click.OnPermanentDeleteDismiss -> processCloseDialog()
            Action.Click.OnStartSessionClick -> processStartSession()
            Action.Click.OnConflictResume -> processConflictResume()
            Action.Click.OnConflictDeleteAndStart -> processConflictDeleteAndStart()
            Action.Click.OnConflictDismiss -> processCloseDialog()
            is Action.Click.OnExerciseRowClick -> processExerciseRowClick(action)
            is Action.Click.OnPastSessionClick -> processPastSessionClick(action)
            Action.Click.OnSaveClick -> processSaveClick()
            Action.Click.OnCancelClick -> processBackClick()
            Action.Click.OnConfirmDiscard -> processConfirmDiscard()
            Action.Click.OnDismissDiscard -> processCloseDialog()
            Action.Click.OnAddExerciseClick -> processAddExerciseClick()
            is Action.Click.OnExerciseRemove -> processExerciseRemove(action)
            is Action.Click.OnUndoExerciseRemove -> processUndoExerciseRemove(action)
            is Action.Click.OnUndoSetRemove -> processUndoSetRemove(action)
            is Action.Click.OnExerciseReorder -> processExerciseReorder(action)
            is Action.Click.OnExerciseCardToggle -> processExerciseCardToggle(action)
            is Action.Click.OnExercisePlanAction -> processExercisePlanAction(action)
            Action.Click.OnTagAddClick -> processTagAddClick()
            Action.Click.OnTagPickerDismiss -> processTagPickerDismiss()
            is Action.Click.OnTagToggle -> processTagToggle(action)
            is Action.Click.OnTagRemove -> processTagRemove(action)
            is Action.Click.OnTagCreate -> processTagCreate(action)
            Action.Click.OnPickerDismiss -> processPickerDismiss()
            is Action.Click.OnPickerToggle -> processPickerToggle(action)
            Action.Click.OnPickerConfirm -> processPickerConfirm()
        }
    }

    private fun processCloseDialog() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processDetailMenuClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.DetailMenu) }
    }

    private fun processBackClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        // Back gesture dismisses the topmost dialog before propagating to navigation.
        if (current.dialogState !is DialogState.Hidden) {
            updateState { it.copy(dialogState = DialogState.Hidden) }
            return
        }
        if (current.mode !is Mode.Edit) {
            consume(Action.Navigation.Back)
            return
        }
        // The write is in flight: the draft is committing, so Отмена may not offer to
        // roll back what the database is about to hold — the save's own outcome is what
        // ends the draft ([State.isSaving]'s KDoc).
        if (current.isSaving) return
        if (current.hasChanges) {
            updateState { it.copy(dialogState = DialogState.DiscardConfirm) }
        } else {
            applyDiscard()
        }
    }

    /**
     * ED14 is about ENTERING: "entering the editor you see the whole list; you expand the one you
     * mean". So the collapse lives here rather than on the ways OUT — one route in, however the
     * screen got back to Read, and a route added later inherits it. `applySnapshotOrPop` clears
     * the set too, for its own reason: a discard restores the loaded form, and expansion is part
     * of the form. D-OPEN-8's insert-opens fires while editing and is untouched by either.
     */
    private fun processEditClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                mode = Mode.Edit(isCreate = false),
                // A new draft: undo toasts of the previous one must not edit this one, its
                // stashed restores must not resurface, and a stuck flag from an orphaned
                // save must not gag its undos.
                draftEpoch = current.draftEpoch + 1,
                isSaving = false,
                pendingSetRestores = persistentListOf(),
                expandedExerciseUuids = persistentSetOf(),
                originalSnapshot = current.toSnapshot(),
            )
        }
    }

    private fun processArchiveClick() {
        val uuid = state.value.uuid ?: return
        val name = state.value.name
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        // The action lives in the `⋮` sheet; close it before the result lands so nothing
        // stacks on the open sheet.
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch(
            onSuccess = { result ->
                when (result) {
                    ArchiveResult.Success -> {
                        sendEvent(
                            Event.ShowArchiveSuccess(
                                message = resourceWrapper.getString(
                                    R.string.feature_training_detail_archive_success_format,
                                    name,
                                ),
                            ),
                        )
                        withContext(mainDispatcher) { consume(Action.Navigation.Back) }
                    }

                    is ArchiveResult.Blocked ->
                        sendEvent(
                            Event.ShowArchiveBlocked(
                                message = resourceWrapper.getString(
                                    R.string.feature_training_detail_archive_blocked,
                                ),
                            ),
                        )
                }
            },
        ) {
            interactor.archive(uuid)
        }
    }

    private fun processPermanentDeleteMenu() {
        val current = state.value
        if (!current.canPermanentlyDelete) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        // Pre-resolve display strings outside the updateState lambda — Rule 1 of
        // compose-state-discipline (no ResourceWrapper calls inside the lambda body).
        val title = resourceWrapper.getString(
            R.string.feature_training_detail_permanent_delete_title,
            current.name,
        )
        val body = resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_body)
        val impactSummary =
            resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_impact)
        val confirmLabel =
            resourceWrapper.getString(R.string.feature_training_detail_permanent_delete_confirm)
        updateState {
            it.copy(
                dialogState = DialogState.PermanentDeleteConfirm(
                    title = title,
                    body = body,
                    impactSummary = impactSummary,
                    confirmLabel = confirmLabel,
                ),
            )
        }
    }

    private fun processPermanentDeleteConfirm() {
        val uuid = state.value.uuid ?: return
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch(
            onSuccess = {
                withContext(mainDispatcher) { consume(Action.Navigation.Back) }
            },
        ) {
            interactor.permanentlyDelete(uuid)
        }
    }

    private fun processStartSession() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val trainingUuid = current.uuid ?: return
        launch {
            when (val resolution = interactor.resolveStartSessionConflict(trainingUuid)) {
                StartSessionConflict.ProceedFresh -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkout(
                        sessionUuid = "",
                        trainingUuid = trainingUuid,
                    ),
                )

                is StartSessionConflict.SilentResume -> consumeOnMain(
                    Action.Navigation.OpenLiveWorkout(
                        sessionUuid = resolution.sessionUuid,
                        trainingUuid = trainingUuid,
                    ),
                )

                is StartSessionConflict.NeedsUserChoice -> {
                    val activeName = current.name.takeIf { it.isNotBlank() }
                        ?: resourceWrapper.getString(
                            R.string.feature_training_detail_conflict_unnamed,
                        )
                    val progressLabel = resourceWrapper.getString(
                        R.string.feature_training_detail_conflict_progress_format,
                        0,
                        0,
                    )
                    val sessionUuid = resolution.active.sessionUuid
                    updateStateImmediate {
                        it.copy(
                            dialogState = DialogState.ActiveSessionConflict(
                                sessionUuid = sessionUuid,
                                activeSessionName = activeName,
                                progressLabel = progressLabel,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun processConflictResume() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val info = current.dialogState as? DialogState.ActiveSessionConflict ?: return
        updateState { it.copy(dialogState = DialogState.Hidden) }
        consume(
            Action.Navigation.OpenLiveWorkout(
                sessionUuid = info.sessionUuid,
                trainingUuid = current.uuid,
            ),
        )
    }

    private fun processConflictDeleteAndStart() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        val current = state.value
        val info = current.dialogState as? DialogState.ActiveSessionConflict ?: return
        updateState { it.copy(dialogState = DialogState.Hidden) }
        launch {
            interactor.deleteSession(info.sessionUuid)
            consumeOnMain(
                Action.Navigation.OpenLiveWorkout(
                    sessionUuid = "",
                    trainingUuid = state.value.uuid,
                ),
            )
        }
    }

    private fun processExerciseRowClick(action: Action.Click.OnExerciseRowClick) {
        if (state.value.mode is Mode.Edit) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenExerciseDetail(action.exerciseUuid))
    }

    private fun processPastSessionClick(action: Action.Click.OnPastSessionClick) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        consume(Action.Navigation.OpenSession(action.sessionUuid))
    }

    private fun processSaveClick() {
        val current = state.value
        if (current.name.isBlank()) {
            updateState { it.copy(nameError = true) }
            return
        }
        if (current.exercises.isEmpty()) {
            sendEvent(
                Event.ShowSaveError(
                    message = resourceWrapper.getString(R.string.feature_training_edit_error_no_exercises),
                ),
            )
            return
        }
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val resolvedUuid = current.uuid?.takeIf { it.isNotBlank() } ?: Uuid.random().toString()
        val snapshot = TrainingChangeDomain(
            uuid = resolvedUuid,
            name = current.name.trim(),
            description = current.description.takeIf { it.isNotBlank() },
            isAdhoc = false,
            archived = false,
            timestamp = System.currentTimeMillis(),
            labels = current.tags.map { it.name },
            exerciseUuids = current.exercises.sortedBy { it.position }.map { it.exerciseUuid },
        )
        val isCreate = current.isCreate
        val plans = current.exercises.map { item ->
            ExercisePlanDomain(
                exerciseUuid = item.exerciseUuid,
                planSets = item.planSets?.map { it.toDomain() },
            )
        }
        // The snapshot above is captured: from here until an outcome lands, an undo would
        // reach the screen and miss the write ([State.isSaving]'s KDoc).
        updateState { it.copy(isSaving = true) }
        launch(
            onError = {
                // The draft is still alive — its undos re-arm with it.
                updateStateImmediate { latest -> latest.copy(isSaving = false) }
            },
            onSuccess = {
                if (isCreate) {
                    withContext(mainDispatcher) { consume(Action.Navigation.Back) }
                } else {
                    updateStateImmediate { latest ->
                        latest.copy(
                            uuid = resolvedUuid,
                            mode = Mode.Read,
                            isSaving = false,
                            originalSnapshot = latest.toSnapshot(),
                        )
                    }
                }
            },
        ) {
            // ONE act: the training row and every plan commit in a single repository
            // transaction, which is also what lets a CREATE flow carry plans at all (ED1) —
            // the plan writes land on the `training_exercise_table` rows the same
            // transaction just wrote.
            interactor.saveTraining(snapshot, plans)
        }
    }

    private fun processConfirmDiscard() {
        sendEvent(Event.HapticClick(HapticFeedbackType.LongPress))
        updateState { it.copy(dialogState = DialogState.Hidden) }
        // Guarded on its own, not only at the entries that raise the sheet: the confirm
        // is a second action, and a save dispatched between the two would land on the
        // rollback below ([State.isSaving]'s KDoc).
        if (state.value.isSaving) return
        applyDiscard()
    }

    private fun applyDiscard() {
        val current = state.value
        val mode = current.mode as? Mode.Edit ?: run {
            consume(Action.Navigation.Back)
            return
        }
        if (mode.isCreate) {
            consume(Action.Navigation.Back)
        } else {
            // Roll the form back to the loaded snapshot and flip into Read mode.
            updateState { latest -> latest.applySnapshotOrPop() }
        }
    }

    private fun State.applySnapshotOrPop(): State {
        val snapshot = originalSnapshot ?: return copy(mode = Mode.Read)
        val matchedTags = availableTags
            .filter { tag -> tag.uuid in snapshot.tagUuids }
            .toImmutableList()
        return copy(
            mode = Mode.Read,
            expandedExerciseUuids = persistentSetOf(),
            pendingSetRestores = persistentListOf(),
            name = snapshot.name,
            nameError = false,
            description = snapshot.description,
            tags = matchedTags,
            tagSearchQuery = "",
            // The whole list, from the snapshot — never the current one filtered and
            // re-sorted. Filtering can only ever REMOVE, so a removed exercise would have
            // nothing to come back from; and re-sorting moves rows without rewriting
            // `position`, which this screen renders as `"${position + 1}."`. Discard is one
            // assignment for the same reason the training and its plans save as one act:
            // half an undo is a wrong screen.
            exercises = snapshot.exercises,
        )
    }

    private fun processAddExerciseClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        launch(
            onSuccess = { results ->
                updateStateImmediate { latest ->
                    latest.copy(
                        pickerState = PickerState.Open(
                            query = "",
                            results = results.map { picker ->
                                PickerExerciseItem(
                                    uuid = picker.exercise.uuid,
                                    name = picker.exercise.name,
                                    type = picker.exercise.type.toUi(),
                                    tags = picker.labels.toImmutableList(),
                                )
                            }.toImmutableList(),
                            selectedUuids = persistentListOf(),
                        ),
                    )
                }
            },
        ) {
            interactor.searchExercisesForPicker(
                query = "",
                excludeUuids = current.exercises.map { it.exerciseUuid }.toSet(),
            )
        }
    }

    // Immediate, and deliberately unconfirmed (D-OPEN-11): the draft is unsaved and Cancel
    // stands behind it. The undo snackbar is the affordance ED11 pairs with that absence —
    // a DRAFT restore, item-wise, with no timer and nothing deferred: nothing was persisted.
    private fun processExerciseRemove(action: Action.Click.OnExerciseRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val removed = current.exercises.firstOrNull { it.exerciseUuid == action.exerciseUuid }
        updateState { latest ->
            latest.copy(
                exercises = latest.exercises
                    .filterNot { it.exerciseUuid == action.exerciseUuid }
                    .mapIndexed { index, item -> item.copy(position = index) }
                    .toImmutableList(),
                expandedExerciseUuids = latest.expandedExerciseUuids
                    .filterNot { it == action.exerciseUuid }
                    .toImmutableSet(),
            )
        }
        if (removed != null) {
            sendEvent(
                Event.ShowExerciseRemovedUndo(
                    message = resourceWrapper.getString(
                        R.string.feature_training_edit_exercise_removed,
                    ),
                    item = removed,
                    wasExpanded = action.exerciseUuid in current.expandedExerciseUuids,
                    draftEpoch = current.draftEpoch,
                ),
            )
        }
    }

    private fun processExerciseReorder(action: Action.Click.OnExerciseReorder) {
        if (action.from == action.to) return
        sendEvent(Event.HapticClick(HapticFeedbackType.SegmentTick))
        updateState { current ->
            val mutable = current.exercises.toMutableList()
            val from = action.from.coerceIn(0, mutable.lastIndex)
            val to = action.to.coerceIn(0, mutable.lastIndex)
            val removed = mutable.removeAt(from)
            mutable.add(to, removed)
            current.copy(
                exercises = mutable
                    .mapIndexed { index, item -> item.copy(position = index) }
                    .toImmutableList(),
            )
        }
    }

    // Per card, never an accordion (ED14's amendment): closing card N must not move card
    // N+3 under the user's finger.
    private fun processExerciseCardToggle(action: Action.Click.OnExerciseCardToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val expanded = current.expandedExerciseUuids
            current.copy(
                expandedExerciseUuids = if (action.exerciseUuid in expanded) {
                    expanded.filterNot { it == action.exerciseUuid }.toImmutableSet()
                } else {
                    (expanded + action.exerciseUuid).toImmutableSet()
                },
            )
        }
    }

    /**
     * The expanded card's plan edit (ED1): reduce against THAT exercise's rows and write the
     * item back. In memory only — Save persists every plan alongside the training, which is
     * also what lets a not-yet-saved training edit its plans at all (the route this replaces
     * had to no-op there: it needed a `training_exercise_table` row to write to).
     */
    private fun processExercisePlanAction(action: Action.Click.OnExercisePlanAction) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val bodyAction = action.action
        val priorDraft = state.value.exercises
            .firstOrNull { it.exerciseUuid == action.exerciseUuid }
            ?.planSets
        updateState { current ->
            val target = current.exercises.firstOrNull { it.exerciseUuid == action.exerciseUuid }
                ?: return@updateState current
            val nextDraft = PlanDraftReducer.reduce(
                draft = target.planSets ?: persistentListOf(),
                action = action.action,
                isWeighted = target.exerciseType == ExerciseTypeUiModel.WEIGHTED,
            )
            // Empty normalizes back to null: `plan_sets IS NULL` is attached-with-no-plan, the
            // persisted shape, and an empty list would be a third value the row never stores.
            val nextPlan = nextDraft.takeIf { it.isNotEmpty() }
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.exerciseUuid == action.exerciseUuid) {
                        item.copy(
                            planSets = nextPlan,
                            planSummary = nextPlan?.formatPlanSummary().orEmpty(),
                        )
                    } else {
                        item
                    }
                }.toImmutableList(),
            )
        }
        // `− подход` gets its undo toast (§4's table): a DRAFT edit — the undo re-inserts the
        // removed row; no timer, nothing deferred. The deferred machinery belongs to the
        // permanent delete alone.
        if (bodyAction is PlanEditorBodyAction.OnSetRemove &&
            priorDraft != null &&
            bodyAction.index in priorDraft.indices
        ) {
            sendEvent(
                Event.ShowSetRemovedUndo(
                    message = resourceWrapper.getString(
                        CoreEditorR.string.core_ui_plan_editor_toast_set_removed,
                    ),
                    exerciseUuid = action.exerciseUuid,
                    set = priorDraft[bodyAction.index],
                    index = bodyAction.index,
                    draftEpoch = state.value.draftEpoch,
                ),
            )
        }
    }

    /**
     * The set-removed toast's «Отменить»: the addressed card's draft takes the row back.
     * Two stacked removals in one card can restore out of order — B-E8 records why exact
     * composition needs row identity the plan rows do not carry.
     */
    private fun processUndoSetRemove(action: Action.Click.OnUndoSetRemove) {
        updateState { current ->
            // The toast can outlive the draft (Save/Cancel ended it, Edit may have begun a
            // new one) or land while a save's captured snapshot is in flight — a stale
            // «Отменить» edits nothing. [State.draftEpoch] and [State.isSaving], both KDocs.
            if (current.isSaving ||
                current.mode !is Mode.Edit ||
                action.draftEpoch != current.draftEpoch
            ) {
                return@updateState current
            }
            val target = current.exercises.firstOrNull { it.exerciseUuid == action.exerciseUuid }
                // The card itself was removed after this set was (both toasts queue): the
                // restore stashes for the exercise undo to apply — dropped here, the row
                // would be lost with BOTH undos tapped ([State.pendingSetRestores]).
                ?: return@updateState current.copy(
                    pendingSetRestores = (
                        current.pendingSetRestores + State.PendingSetRestore(
                            exerciseUuid = action.exerciseUuid,
                            set = action.set,
                            index = action.index,
                        )
                        ).toImmutableList(),
                )
            val draft = target.planSets ?: persistentListOf()
            val at = action.index.coerceIn(0, draft.size)
            val nextPlan = draft.toMutableList()
                .apply { add(at, action.set) }
                .toImmutableList()
            current.copy(
                exercises = current.exercises.map { item ->
                    if (item.exerciseUuid == action.exerciseUuid) {
                        item.copy(
                            planSets = nextPlan,
                            planSummary = nextPlan.formatPlanSummary(),
                        )
                    } else {
                        item
                    }
                }.toImmutableList(),
            )
        }
    }

    /**
     * The exercise-removed toast's «Отменить»: the item returns where it stood — clamped to
     * the list's current size, since other edits may have landed inside the toast's window —
     * positions reindex, and a card that was open re-opens.
     */
    private fun processUndoExerciseRemove(action: Action.Click.OnUndoExerciseRemove) {
        updateState { current ->
            // Same stale-toast guard as the set undo above — [State.draftEpoch] and
            // [State.isSaving], both KDocs.
            if (current.isSaving ||
                current.mode !is Mode.Edit ||
                action.draftEpoch != current.draftEpoch
            ) {
                return@updateState current
            }
            if (current.exercises.any { it.exerciseUuid == action.item.exerciseUuid }) {
                // The card is already back by another route (the picker): stashes for it
                // belong to the dead removal chain and must not wait around to resurrect
                // into the NEW card's own remove-and-undo.
                return@updateState current.copy(
                    pendingSetRestores = current.pendingSetRestores
                        .filterNot { it.exerciseUuid == action.item.exerciseUuid }
                        .toImmutableList(),
                )
            }
            // Stashed set restores that fired while this card was absent go back into it
            // now, in tap order — [State.pendingSetRestores]. The plan was frozen while
            // the card was gone, so each captured index still points where it did.
            val stashes = current.pendingSetRestores
                .filter { it.exerciseUuid == action.item.exerciseUuid }
            val restoredItem = stashes
                .fold(action.item) { item, stash ->
                    val draft = item.planSets ?: persistentListOf()
                    val at = stash.index.coerceIn(0, draft.size)
                    item.copy(
                        planSets = draft.toMutableList()
                            .apply { add(at, stash.set) }
                            .toImmutableList(),
                    )
                }
                .let { item ->
                    if (stashes.isEmpty()) {
                        item
                    } else {
                        item.copy(planSummary = item.planSets?.formatPlanSummary().orEmpty())
                    }
                }
            val at = action.item.position.coerceIn(0, current.exercises.size)
            val nextExercises = current.exercises.toMutableList()
                .apply { add(at, restoredItem) }
                .mapIndexed { index, item -> item.copy(position = index) }
                .toImmutableList()
            current.copy(
                exercises = nextExercises,
                pendingSetRestores = current.pendingSetRestores
                    .filterNot { it.exerciseUuid == action.item.exerciseUuid }
                    .toImmutableList(),
                expandedExerciseUuids = if (action.wasExpanded) {
                    (current.expandedExerciseUuids + action.item.exerciseUuid).toImmutableSet()
                } else {
                    current.expandedExerciseUuids
                },
            )
        }
    }

    private fun processTagAddClick() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { it.copy(dialogState = DialogState.TagPicker) }
    }

    /** The query clears with the sheet, so reopening starts from the whole dictionary. */
    private fun processTagPickerDismiss() {
        updateState {
            it.copy(
                dialogState = DialogState.Hidden,
                tagSearchQuery = "",
            )
        }
    }

    private fun processTagToggle(action: Action.Click.OnTagToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val tag = current.availableTags.firstOrNull { it.uuid == action.tagUuid }
                ?: return@updateState current
            val tags = if (current.tags.any { it.uuid == action.tagUuid }) {
                current.tags.filterNot { it.uuid == action.tagUuid }.toImmutableList()
            } else {
                (current.tags + tag).toImmutableList()
            }
            current.copy(tags = tags)
        }
    }

    private fun processTagRemove(action: Action.Click.OnTagRemove) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            current.copy(
                tags = current.tags.filterNot { it.uuid == action.tagUuid }.toImmutableList(),
            )
        }
    }

    private fun processTagCreate(action: Action.Click.OnTagCreate) {
        if (action.name.isBlank()) return
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        launch(
            onSuccess = { tag ->
                updateStateImmediate { current ->
                    // The repository returns the EXISTING row for a name that already
                    // exists, so «Создать» over an already-selected name must not chip it
                    // twice — the persisted links dedup on Save, the draft must agree.
                    val alreadySelected = current.tags.any { it.uuid == tag.uuid }
                    current.copy(
                        tags = if (alreadySelected) {
                            current.tags
                        } else {
                            (
                                current.tags + AppTagItem(
                                    uuid = tag.uuid,
                                    name = tag.name,
                                )
                                ).toImmutableList()
                        },
                        tagSearchQuery = "",
                    )
                }
            },
        ) {
            interactor.createTag(action.name.trim())
        }
    }

    private fun processPickerDismiss() {
        updateState { current -> current.copy(pickerState = PickerState.Closed) }
    }

    private fun processPickerToggle(action: Action.Click.OnPickerToggle) {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        updateState { current ->
            val picker = current.pickerState as? PickerState.Open ?: return@updateState current
            val nextSelection = if (action.uuid in picker.selectedUuids) {
                picker.selectedUuids - action.uuid
            } else {
                picker.selectedUuids + action.uuid
            }
            current.copy(pickerState = picker.copy(selectedUuids = nextSelection.toImmutableList()))
        }
    }

    private fun processPickerConfirm() {
        sendEvent(Event.HapticClick(HapticFeedbackType.ContextClick))
        val current = state.value
        val picker = current.pickerState as? PickerState.Open ?: return
        if (picker.selectedUuids.isEmpty()) {
            updateState { it.copy(pickerState = PickerState.Closed) }
            return
        }
        launch(
            onSuccess = { resolved ->
                updateStateImmediate { latest ->
                    // Against LATEST, not the state the picker opened over: the resolution
                    // is async and the removed card's «Отменить» can restore it while the
                    // query is in flight — a blind append would seat the same uuid twice,
                    // and Save cannot write a duplicate (training_uuid, exercise_uuid) key.
                    val fresh = resolved.filterNot { picker ->
                        latest.exercises.any { it.exerciseUuid == picker.exercise.uuid }
                    }
                    val nextItems = fresh.mapIndexed { localIndex, picker ->
                        TrainingExerciseItem(
                            exerciseUuid = picker.exercise.uuid,
                            exerciseName = picker.exercise.name,
                            exerciseType = picker.exercise.type.toUi(),
                            tags = picker.labels.toImmutableList(),
                            position = latest.exercises.size + localIndex,
                            // null, not an empty list: attached-with-no-plan is what the row
                            // will persist as, and the dirty signature compares this value.
                            planSets = null,
                            planSummary = "",
                        )
                    }
                    latest.copy(
                        exercises = (latest.exercises + nextItems).toImmutableList(),
                        // A picker insert is a FRESH card: a stash left by the removed
                        // card's chain must not resurrect into it on a later undo
                        // ([State.pendingSetRestores]'s lifecycle).
                        pendingSetRestores = latest.pendingSetRestores
                            .filterNot { stash ->
                                nextItems.any { it.exerciseUuid == stash.exerciseUuid }
                            }
                            .toImmutableList(),
                        pickerState = PickerState.Closed,
                        // D-OPEN-8: an insert is an addressed gesture whose next step is the
                        // plan, so the inserted card opens — the FIRST only on a multi-insert.
                        // Cards already open stay open (per card, not an accordion).
                        expandedExerciseUuids = (
                            latest.expandedExerciseUuids +
                                listOfNotNull(nextItems.firstOrNull()?.exerciseUuid)
                            ).toImmutableSet(),
                    )
                }
            },
        ) {
            interactor.resolveExercises(picker.selectedUuids)
        }
    }
}
