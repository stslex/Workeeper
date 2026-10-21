// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_toast_undo
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_success
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_set_removed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_confirm
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_impact
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_tag_limit
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toUi
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseClickCopy
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import io.github.stslex.workeeper.feature.exercise.resources.Res as ExerciseRes

@SingleIn(ExerciseScope::class)
internal class CommonHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    private val resourceWrapper: ResourceWrapper,
    private val stringResolver: StringResolver,
    store: ExerciseHandlerStore,
) : Handler<Action.Common>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            is Action.Common.ImagePicked -> processImagePicked(action)
            Action.Common.ImagePickCancelled -> processImagePickCancelled()
            is Action.Common.ImageRequestReceived -> processImageRequest(action)
        }
    }

    /**
     * Resolve the viewer's request name and act on it. An unrecognised name is dropped: it means
     * a verb this build does not have.
     */
    private fun processImageRequest(action: Action.Common.ImageRequestReceived) {
        val request = Screen.ExerciseImageRequest.entries
            .firstOrNull { it.name == action.request }
            ?: return

        when (request) {
            Screen.ExerciseImageRequest.REPLACE -> consume(Action.Click.OnEditImageClick)
            Screen.ExerciseImageRequest.REMOVE -> consume(Action.Click.OnRemoveImageClick)
        }
    }

    private fun processImagePicked(action: Action.Common.ImagePicked) {
        updateState {
            it.copy(
                pendingImage = PendingImage.NewFromUri(action.ref),
                dialogState = DialogState.Hidden,
            )
        }
    }

    private fun processImagePickCancelled() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
    }

    private fun processInit() {
        observeTags()
        resolveClickCopy()
        val uuid = state.value.uuid ?: return
        loadExercise(uuid)
    }

    private fun observeTags() {
        interactor.observeAvailableTags().launch { tags ->
            val mapped = tags.map { it.toUi() }.toImmutableList()
            updateStateImmediate { current -> current.copy(availableTags = mapped) }
        }
    }

    /**
     * D2: the click paths' constant copy, resolved once per Store, so the weight-wipe confirm, the
     * two toasts and the deferred delete read it from State and their click paths never suspend.
     */
    private fun resolveClickCopy() {
        launch(
            onSuccess = { copy -> updateState { it.copy(clickCopy = copy) } },
        ) {
            ExerciseClickCopy(
                typeChangeTitle = stringResolver.getString(
                    ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_title,
                ),
                typeChangeBody = stringResolver.getString(
                    ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_body,
                ),
                typeChangeImpact = stringResolver.getString(
                    ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_impact,
                ),
                typeChangeConfirm = stringResolver.getString(
                    ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_confirm,
                ),
                setRemoved = stringResolver.getString(ExerciseRes.string.feature_exercise_edit_plan_set_removed),
                tagLimit = stringResolver.getString(ExerciseRes.string.feature_exercise_edit_tag_limit),
                permanentDeleteSuccess = stringResolver.getString(
                    ExerciseRes.string.feature_exercise_detail_permanent_delete_success,
                ),
                undoLabel = stringResolver.getString(Res.string.core_ui_kit_toast_undo),
            )
        }
    }

    private fun loadExercise(uuid: String) {
        launch(
            onSuccess = { result ->
                // F2: the history rows and the plan read nothing from the State, so they are built
                // before the write; the lambda keeps what reads `current`, and the copy.
                val recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList()
                val adhocPlan = result.adhocPlan?.map { it.toUi() }?.toImmutableList()
                updateStateImmediate { current ->
                    val exercise = result.exercise
                        ?: return@updateStateImmediate current.copy(isLoading = false)
                    val tags = result.labels
                        .map { name ->
                            val matched = current.availableTags
                                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                            matched ?: AppTagItem(uuid = name, name = name)
                        }
                        .toImmutableList()
                    current.copy(
                        name = exercise.name,
                        type = exercise.type.toUi(),
                        description = exercise.description.orEmpty(),
                        tags = tags,
                        recentHistory = recentHistory,
                        historyCount = result.historyCount,
                        isLoading = false,
                        canPermanentlyDelete = result.canPermanentlyDelete,
                        adhocPlan = adhocPlan,
                        imagePath = exercise.imagePath,
                        imageLastModified = result.imageLastModified,
                        pendingImage = PendingImage.Unchanged,
                        originalSnapshot = State.Snapshot(
                            name = exercise.name,
                            type = exercise.type.toUi(),
                            description = exercise.description.orEmpty(),
                            tagUuids = tags.map { it.uuid },
                            adhocPlan = adhocPlan,
                        ),
                    ).withDraftCarriedFrom(current)
                }
                if (result.exercise != null) observePersonalRecord(uuid)
            },
            // Clearing `isLoading` here is load-bearing: the route does not compose until the
            // load lands, and `launch` defaults `onError` to `{}`, so an empty arm latches it.
            onError = { updateStateImmediate { it.copy(isLoading = false) } },
        ) {
            val exercise = async { interactor.getExercise(uuid) }
            val labels = async { interactor.getLabels(uuid) }
            val history = async { interactor.getRecentHistory(uuid) }
            val historyCount = async { interactor.countSessions(uuid) }
            val canPermanentlyDelete = async { interactor.canPermanentlyDelete(uuid) }
            val adhocPlan = async { interactor.getAdhocPlan(uuid) }
            val loadedExercise = exercise.await()
            // Capture mtime so Coil can key by `?v=<mtime>` and not serve a stale cache entry.
            val imageLastModified = loadedExercise?.imagePath?.let { interactor.getImageLastModified(it) } ?: 0L
            LoadResult(
                exercise = loadedExercise,
                labels = labels.await(),
                history = history.await(),
                historyCount = historyCount.await(),
                canPermanentlyDelete = canPermanentlyDelete.await(),
                adhocPlan = adhocPlan.await(),
                imageLastModified = imageLastModified,
            )
        }
    }

    /**
     * GUARD: keep the type-keyed `flatMapLatest`. The restart is a rendering concern — the PR card
     * formats weighted and rep-only records differently, so a type switch must re-map the record.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePersonalRecord(uuid: String) {
        state
            .map { it.type.toDomain() }
            .distinctUntilChanged()
            .flatMapLatest { type ->
                interactor.observePersonalRecord(uuid)
                    .map { record -> record?.toUi(resourceWrapper, type.toUi()) }
            }
            .launch { pr ->
                updateStateImmediate { current ->
                    current.copy(personalRecord = pr)
                }
            }
    }

    /**
     * A retained Store receives `Init` again after the image viewer leaves composition; keep the
     * dirty draft while the freshly loaded snapshot becomes the baseline for Save or Cancel.
     */
    private fun State.withDraftCarriedFrom(previous: State): State {
        if (previous.mode !is State.Mode.Edit || !previous.hasChanges) return this
        return copy(
            name = previous.name,
            type = previous.type,
            description = previous.description,
            tags = previous.tags,
            adhocPlan = previous.adhocPlan,
            pendingImage = previous.pendingImage,
        )
    }

    private data class LoadResult(
        val exercise: ExerciseDomain?,
        val labels: List<String>,
        val history: List<HistoryEntryDomain>,
        val historyCount: Int,
        val canPermanentlyDelete: Boolean,
        val adhocPlan: List<PlanSetDomain>?,
        val imageLastModified: Long,
    )
}
