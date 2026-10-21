// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.platform.CameraPermission
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TagDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseClickCopy
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The deferred delete queues the undo label the State holds; kit's ComposeStringResolverRuTest
 * owns the Russian catalog.
 */
internal class PermanentDeleteUndoLabelRuTest {

    @Test
    fun `the queued undo label resolves from the real russian catalog`() = runTest {
        while (SnackbarManager.pendingModelCount > 0) {
            SnackbarManager.snackbar.first()
        }
        val stateFlow = MutableStateFlow(
            State.create(uuid = "uuid-ru").copy(
                canPermanentlyDelete = true,
                clickCopy = ExerciseClickCopy.EMPTY.copy(undoLabel = "Отменить"),
            ),
        )
        val store = RecordingStore(stateFlow, this)
        val handler = ClickHandler(
            interactor = RelaxedInteractor(),
            stringResolver = UnreachedStringResolver(),
            cameraPermission = UnreachedCameraPermission(),
            mainDispatcher = Dispatchers.Unconfined,
            store = store,
        )

        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        advanceUntilIdle()

        assertEquals("Отменить", SnackbarManager.snackbar.first().model.actionLabel)
    }

    /**
     * The store as the relaxed MockK store answered it: [state] is the identity's flow,
     * [updateState] applies to it, [sendEvent] and [consume] record, and [launch] runs the action
     * inside `supervisorScope` on the identity's scheduler, then `onSuccess`, a throw to `onError`.
     * Every other member fails fast.
     */
    private class RecordingStore(
        private val flow: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : ExerciseHandlerStore {

        val events = mutableListOf<Event>()
        val consumed = mutableListOf<Action>()

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by the deferred delete")

        override val logger: Logger
            get() = error("logger must not be read by the deferred delete")

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by the deferred delete")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
            error("updateStateImmediate must not be used by the deferred delete")

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by the deferred delete")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                val result = supervisorScope { action() }
                onSuccess(result)
            } catch (t: Throwable) {
                onError(t)
            }
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launchDefault must not be used by the deferred delete")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Nothing = error("Flow.launch must not be used by the deferred delete")
    }

    /**
     * The interactor as the relaxed MockK one answered it: `Unit`, an empty list or flow, `false`,
     * `0` or `null`; a member whose relaxed answer was a mock throws. Nothing here reaches one.
     */
    private class RelaxedInteractor : ExerciseInteractor {

        override suspend fun getExercise(uuid: String): ExerciseDomain? = null

        override suspend fun getLabels(exerciseUuid: String): List<String> = emptyList()

        override suspend fun getRecentHistory(exerciseUuid: String, limit: Int): List<HistoryEntryDomain> =
            emptyList()

        override suspend fun countSessions(exerciseUuid: String): Int = 0

        override fun observeAvailableTags(): Flow<List<TagDomain>> = emptyFlow()

        override fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDomain?> = emptyFlow()

        override suspend fun saveExercise(snapshot: ExerciseChangeDomain): SaveResult =
            error("saveExercise has no relaxed default")

        override suspend fun createTag(name: String): TagDomain = error("createTag has no relaxed default")

        override suspend fun archive(uuid: String): ArchiveResult = error("archive has no relaxed default")

        override suspend fun restore(uuid: String) = Unit

        override suspend fun canPermanentlyDelete(uuid: String): Boolean = false

        override suspend fun permanentlyDelete(uuid: String) = Unit

        override suspend fun getAdhocPlan(uuid: String): List<PlanSetDomain>? = null

        override suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDomain>?) = Unit

        override suspend fun saveImage(ref: ImageRef, exerciseUuid: String): ImageSaveResult =
            error("saveImage has no relaxed default")

        override suspend fun createTempCaptureRef(): ImageRef = error("createTempCaptureRef has no relaxed default")

        override suspend fun deleteImageFile(path: String): Boolean = false

        override suspend fun getImageLastModified(path: String): Long = 0L

        override suspend fun resolveTrackNowConflict(): TrackNowConflict =
            error("resolveTrackNowConflict has no relaxed default")

        override suspend fun startTrackNowSession(exerciseUuid: String, defaultName: String): String = ""

        override suspend fun deleteSession(sessionUuid: String) = Unit
    }

    /** The deferred delete reads its labels from the State, so no copy is read here. */
    private class UnreachedStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
            error("the deferred delete must not read $resource")

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = error("the deferred delete reads no plural; got $resource")
    }

    private class UnreachedCameraPermission : CameraPermission {

        override fun isGranted(): Boolean = error("the deferred delete must not read the camera permission")
    }
}
