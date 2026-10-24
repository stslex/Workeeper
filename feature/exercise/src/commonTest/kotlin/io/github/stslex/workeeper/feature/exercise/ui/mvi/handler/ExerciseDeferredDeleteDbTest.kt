// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.platform.CameraPermission
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
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
import io.github.stslex.workeeper.feature.exercise.resources.Res
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_button
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_impact
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_confirm_title
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * ED11's deferred delete against a recording interactor (D4): confirming deletes nothing, the
 * snackbar's `onDismissed` commits, and undo or process death inside the window leaves the row.
 * The real delete is `ExerciseRepositoryImplDbTest`'s (core:data:exercise).
 */
internal class ExerciseDeferredDeleteDbTest {

    private val interactor = RowsInteractor()

    /** Every consumed action, paired with whether the undo snackbar was already queued. */
    private val consumedActions = mutableListOf<Pair<Action, Boolean>>()

    /** On Native one test binary is one process, and the manager is a process-wide object. */
    @BeforeTest
    fun drainSnackbars() = runTest {
        while (SnackbarManager.pendingModelCount > 0) {
            SnackbarManager.snackbar.first()
        }
    }

    private fun TestScope.handlerFor(state: State): Triple<MutableStateFlow<State>, MutableList<Event>, ClickHandler> {
        val stateFlow = MutableStateFlow(state)
        val events = mutableListOf<Event>()
        val store = RunningStore(stateFlow, events, consumedActions, this)
        val handler = ClickHandler(
            interactor = interactor,
            stringResolver = ConfirmCopyStringResolver(),
            cameraPermission = UnreachedCameraPermission(),
            mainDispatcher = Dispatchers.Unconfined,
            store = store,
        )
        return Triple(stateFlow, events, handler)
    }

    private fun seedExercise(): Uuid {
        val uuid = Uuid.random()
        interactor.rows.add(uuid)
        return uuid
    }

    private suspend fun TestScope.confirmDelete(uuid: Uuid): AppSnackbarModel {
        val (_, _, handler) = handlerFor(
            State.create(uuid = uuid.toString()).copy(
                canPermanentlyDelete = true,
                clickCopy = ExerciseClickCopy.EMPTY.copy(
                    permanentDeleteSuccess = "Exercise deleted",
                    undoLabel = "Undo",
                ),
            ),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        advanceUntilIdle()
        return SnackbarManager.snackbar.first().model
    }

    @Test
    fun `confirming deletes nothing while the snackbar lives`() = runTest {
        val uuid = seedExercise()

        confirmDelete(uuid)

        // The strict order (ED11): the confirm opened the window; the row is untouched.
        assertTrue(uuid in interactor.rows)
    }

    /** The queued label is the one the State holds, and Back rides after the queue. */
    @Test
    fun `back is consumed only after the snackbar is queued with the real undo label`() = runTest {
        val uuid = seedExercise()

        val pending = confirmDelete(uuid)

        assertEquals("Undo", pending.actionLabel)
        assertEquals(listOf<Action>(Action.Navigation.Back), consumedActions.map { it.first })
        assertTrue(
            consumedActions.single().second,
            "Back must land only after the undo snackbar is queued",
        )
    }

    @Test
    fun `the window closing commits the delete`() = runTest {
        val uuid = seedExercise()

        val pending = confirmDelete(uuid)
        assertTrue(uuid in interactor.rows)

        // What `resolveSnackbarOutcome` runs on Dismissed/timeout.
        pending.onDismissed()

        assertTrue(uuid !in interactor.rows)
    }

    @Test
    fun `undo — or a process death inside the window — leaves the row`() = runTest {
        val uuid = seedExercise()

        confirmDelete(uuid)
        // Undo is a no-op and death is a cancellation: `commit` never runs (D-OPEN-10).

        assertTrue(uuid in interactor.rows)
    }

    /**
     * The commit must not need the Store's scope — the screen popped. The lambda holds only the
     * interactor, modelled here by committing with no handler or store in scope.
     */
    @Test
    fun `the commit outlives the screen that scheduled it`() = runTest {
        val uuid = seedExercise()
        var pending: suspend () -> Unit
        run {
            pending = confirmDelete(uuid).onDismissed
        }

        pending()

        assertTrue(uuid !in interactor.rows)
        assertTrue(interactor.rows.isEmpty())
    }

    /** D2: the deferred delete queues the copy `Init` resolved into the State, not a fresh read. */
    @Test
    fun `the queued delete carries the message and the undo label Init resolved`() = runTest {
        val uuid = seedExercise()
        val (_, _, handler) = handlerFor(
            State.create(uuid = uuid.toString()).copy(
                canPermanentlyDelete = true,
                clickCopy = ExerciseClickCopy.EMPTY.copy(
                    permanentDeleteSuccess = "exercise-deleted",
                    undoLabel = "undo",
                ),
            ),
        )

        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        advanceUntilIdle()
        val pending = SnackbarManager.snackbar.first().model

        assertEquals("exercise-deleted", pending.message)
        assertEquals("undo", pending.actionLabel)
    }

    /**
     * The store as the MockK store answered it: [state] is the identity's flow, [updateState]
     * applies to it, [sendEvent] records, [consume] records each action with whether a snackbar is
     * queued at that moment, and [launch] runs every launch on the identity's scheduler — the action
     * inside `supervisorScope`, then `onSuccess`, a throw to `onError`. Every other member fails fast.
     */
    private class RunningStore(
        private val flow: MutableStateFlow<State>,
        private val events: MutableList<Event>,
        private val consumed: MutableList<Pair<Action, Boolean>>,
        private val scope: CoroutineScope,
    ) : ExerciseHandlerStore {

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by the deferred delete")

        override val logger: Logger
            get() = error("logger must not be read by the deferred delete")

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action to (SnackbarManager.pendingModelCount > 0))
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
     * The rows the deferred delete can remove: [permanentlyDelete] takes one out and is recorded in
     * [deleted]. Every member the deferred delete does not reach fails fast.
     */
    private class RowsInteractor : ExerciseInteractor {

        val rows = mutableSetOf<Uuid>()
        val deleted = mutableListOf<String>()

        override suspend fun permanentlyDelete(uuid: String) {
            deleted.add(uuid)
            rows.remove(Uuid.parse(uuid))
        }

        override suspend fun getExercise(uuid: String): ExerciseDomain? = unreached("getExercise")

        override suspend fun getLabels(exerciseUuid: String): List<String> = unreached("getLabels")

        override suspend fun getRecentHistory(exerciseUuid: String, limit: Int): List<HistoryEntryDomain> =
            unreached("getRecentHistory")

        override suspend fun countSessions(exerciseUuid: String): Int = unreached("countSessions")

        override fun observeAvailableTags(): Flow<List<TagDomain>> = unreached("observeAvailableTags")

        override fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDomain?> =
            unreached("observePersonalRecord")

        override suspend fun saveExercise(snapshot: ExerciseChangeDomain): SaveResult = unreached("saveExercise")

        override suspend fun createTag(name: String): TagDomain = unreached("createTag")

        override suspend fun archive(uuid: String): ArchiveResult = unreached("archive")

        override suspend fun restore(uuid: String): Unit = unreached("restore")

        override suspend fun canPermanentlyDelete(uuid: String): Boolean = unreached("canPermanentlyDelete")

        override suspend fun getAdhocPlan(uuid: String): List<PlanSetDomain>? = unreached("getAdhocPlan")

        override suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDomain>?): Unit = unreached("setAdhocPlan")

        override suspend fun saveImage(ref: ImageRef, exerciseUuid: String): ImageSaveResult = unreached("saveImage")

        override suspend fun createTempCaptureRef(): ImageRef = unreached("createTempCaptureRef")

        override suspend fun deleteImageFile(path: String): Boolean = unreached("deleteImageFile")

        override suspend fun getImageLastModified(path: String): Long = unreached("getImageLastModified")

        override suspend fun resolveTrackNowConflict(): TrackNowConflict = unreached("resolveTrackNowConflict")

        override suspend fun startTrackNowSession(exerciseUuid: String, defaultName: String): String =
            unreached("startTrackNowSession")

        override suspend fun deleteSession(sessionUuid: String): Unit = unreached("deleteSession")

        private fun unreached(member: String): Nothing = error("the deferred delete must not reach $member")
    }

    /** The menu click launches the confirm's four reads; its write finds no menu sheet here. */
    private class ConfirmCopyStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
            when (resource) {
                Res.string.feature_exercise_detail_permanent_delete_confirm_title,
                Res.string.feature_exercise_detail_permanent_delete_confirm_body,
                Res.string.feature_exercise_detail_permanent_delete_confirm_impact,
                Res.string.feature_exercise_detail_permanent_delete_confirm_button,
                -> ""

                else -> error("the deferred delete must not read $resource")
            }

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
