// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.mapper.ExerciseUiMapper.toUi
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
import java.io.File

@SingleIn(ExerciseScope::class)
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
            is Action.Common.ImageRequestReceived -> processImageRequest(action)
        }
    }

    /**
     * Resolve the viewer's request name and act on it.
     *
     * The `when` is exhaustive over [Screen.ExerciseImageRequest] on purpose — that is the
     * reason the viewer hands back an enum's name rather than a bare string or a pair of
     * booleans: a third verb cannot be added on one side only without this failing to
     * compile.
     *
     * An unrecognised name resolves to `null` and is dropped. It means a viewer sent a verb
     * this build does not have, which is not a state the user can be shown anything useful
     * about.
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
                pendingImage = PendingImage.NewFromUri(action.uri),
                dialogState = DialogState.Hidden,
            )
        }
    }

    private fun processImagePickCancelled() {
        updateState { it.copy(dialogState = DialogState.Hidden) }
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
            // Clearing `isLoading` here is load-bearing, not tidiness. The route does not
            // compose until the load lands (§26; `ExerciseGraph`), so a throw that left the
            // flag latched would leave the user on a permanently empty frame with no way back
            // into the screen. `launch` defaults `onError` to `{}` (B17, B21), so this arm must
            // be written out — an empty one is the latched flag.
            onError = { updateStateImmediate { it.copy(isLoading = false) } },
        ) {
            val exercise = async { interactor.getExercise(uuid) }
            val labels = async { interactor.getLabels(uuid) }
            val history = async { interactor.getRecentHistory(uuid) }
            val historyCount = async { interactor.countSessions(uuid) }
            val canPermanentlyDelete = async { interactor.canPermanentlyDelete(uuid) }
            val adhocPlan = async { interactor.getAdhocPlan(uuid) }
            LoadResult(
                exercise = exercise.await(),
                labels = labels.await(),
                history = history.await(),
                historyCount = historyCount.await(),
                canPermanentlyDelete = canPermanentlyDelete.await(),
                adhocPlan = adhocPlan.await(),
            )
        }
    }

    /**
     * The query no longer takes a type — it reads `exercise_table.type` itself — so the
     * subscription cannot go stale on the *query* side. The restart is still needed on the
     * *rendering* side: the PR card formats weight-bearing and rep-only records differently,
     * so switching WEIGHTED ↔ WEIGHTLESS in edit mode must re-map the latest record through
     * the new type rather than leave the old label on screen.
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

    // TODO: DON'T RETURN A BIG RESULT -> MAP it to ui in mappinmg flow, not in collect !!!!
    private fun State.applyLoaded(
        result: LoadResult,
    ): State {
        val exercise = result.exercise ?: return copy(isLoading = false)
        val adhocPlan = result.adhocPlan
            ?.map { it.toUi() }
            ?.toImmutableList()
        val tags = result.labels
            .map { name ->
                val matched = availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                matched ?: AppTagItem(uuid = name, name = name)
            }
            .toImmutableList()
        val imagePath = exercise.imagePath
        // Capture the file's mtime so Coil can key by `?v=<mtime>` and avoid serving a
        // stale cache entry when the user replaces the image at the same path.
        val imageLastModified = imagePath?.let { File(it).lastModified() } ?: 0L
        val loaded = copy(
            name = exercise.name,
            type = exercise.type.toUi(),
            description = exercise.description.orEmpty(),
            tags = tags,
            recentHistory = result.history.map { it.toUi(resourceWrapper) }.toImmutableList(),
            historyCount = result.historyCount,
            isLoading = false,
            canPermanentlyDelete = result.canPermanentlyDelete,
            adhocPlan = adhocPlan,
            imagePath = imagePath,
            imageLastModified = imageLastModified,
            pendingImage = PendingImage.Unchanged,
            originalSnapshot = State.Snapshot(
                name = exercise.name,
                type = exercise.type.toUi(),
                description = exercise.description.orEmpty(),
                tagUuids = tags.map { it.uuid },
                adhocPlan = adhocPlan,
            ),
        )
        return loaded.withDraftCarriedFrom(this)
    }

    /**
     * A retained Store receives `Init` again after the image viewer leaves composition. Keep the
     * editable values when that Store already owns a dirty draft; the freshly loaded snapshot and
     * committed image path still become the baseline for Save or Cancel.
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
    )
}
