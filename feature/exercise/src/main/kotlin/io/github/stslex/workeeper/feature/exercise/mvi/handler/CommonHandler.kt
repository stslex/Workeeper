// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.toUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel.Companion.toUi
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.mvi.mapper.toAdhocPlanSummary
import io.github.stslex.workeeper.feature.exercise.mvi.mapper.toUi
import io.github.stslex.workeeper.feature.exercise.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: ExerciseInteractor,
    private val resourceWrapper: ResourceWrapper,
    store: ExerciseHandlerStore,
) : Handler<Action.Common>, ExerciseHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> processInit()
            is Action.Common.ImagePicked -> processImagePicked(action)
            Action.Common.ImagePickCancelled -> processImagePickCancelled()
        }
    }

    private fun processImagePicked(action: Action.Common.ImagePicked) {
        updateState {
            it.copy(
                pendingImage = PendingImage.NewFromUri(action.uri),
                sourceDialogVisible = false,
            )
        }
    }

    private fun processImagePickCancelled() {
        updateState { it.copy(sourceDialogVisible = false) }
    }

    private fun processInit() {
        observeTags()
        val uuid = state.value.uuid ?: return
        loadExercise(uuid)
    }

    private fun observeTags() {
        interactor.observeAvailableTags().launch { tags ->
            val mapped = tags.map { it.toUi() }.toImmutableList()
            updateStateImmediate { current -> current.copy(availableTags = mapped) }
        }
    }

    private fun loadExercise(uuid: String) {
        launch(
            onSuccess = { result ->
                updateStateImmediate { current -> current.applyLoaded(result) }
                if (result.exercise != null) observePersonalRecord(uuid)
            },
        ) {
            val exercise = async { interactor.getExercise(uuid) }
            val labels = async { interactor.getLabels(uuid) }
            val history = async { interactor.getRecentHistory(uuid) }
            val canPermanentlyDelete = async { interactor.canPermanentlyDelete(uuid) }
            val adhocPlan = async { interactor.getAdhocPlan(uuid) }
            LoadResult(
                exercise = exercise.await(),
                labels = labels.await(),
                history = history.await(),
                canPermanentlyDelete = canPermanentlyDelete.await(),
                adhocPlan = adhocPlan.await(),
            )
        }
    }

    /**
     * Restarts the PR collection whenever the user's selected type changes — switching
     * WEIGHTED ↔ WEIGHTLESS in edit mode would otherwise leave the original subscription
     * running with the stale `isWeightless` flag and re-emit a wrong PR after save.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePersonalRecord(uuid: String) {
        val typeFlow = state.map { it.type.toData() }.distinctUntilChanged()
        val flow = typeFlow.flatMapLatest { type ->
            interactor.observePersonalRecord(uuid, type).map { record -> record to type }
        }
        flow.launch { (record, type) ->
            val pr = record?.toUi(resourceWrapper, type.toUi())
            updateStateImmediate { current -> current.copy(personalRecord = pr) }
        }
    }

    private fun State.applyLoaded(result: LoadResult): State {
        val exercise = result.exercise ?: return copy(isLoading = false)
        val adhocPlan = result.adhocPlan
            ?.map { it.toUi() }
            ?.toImmutableList()
        val tags = result.labels
            .map { name ->
                val matched = availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                matched ?: TagUiModel(uuid = name, name = name)
            }
            .toImmutableList()
        val imagePath = exercise.imagePath
        // Capture the file's mtime so Coil can key by `?v=<mtime>` and avoid serving a
        // stale cache entry when the user replaces the image at the same path.
        val imageLastModified = imagePath?.let { File(it).lastModified() } ?: 0L
        return copy(
            name = exercise.name,
            type = exercise.type.toUi(),
            description = exercise.description.orEmpty(),
            tags = tags,
            recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList(),
            isLoading = false,
            canPermanentlyDelete = result.canPermanentlyDelete,
            adhocPlan = adhocPlan,
            adhocPlanSummaryLabel = adhocPlan.toAdhocPlanSummary(resourceWrapper),
            imagePath = imagePath,
            imageLastModified = imageLastModified,
            pendingImage = PendingImage.Unchanged,
            originalSnapshot = State.Snapshot(
                name = exercise.name,
                type = exercise.type.toUi(),
                description = exercise.description.orEmpty(),
                tagUuids = tags.map { it.uuid },
            ),
        )
    }

    private data class LoadResult(
        val exercise: ExerciseDataModel?,
        val labels: List<String>,
        val history: List<HistoryEntry>,
        val canPermanentlyDelete: Boolean,
        val adhocPlan: List<PlanSetDataModel>?,
    )
}
