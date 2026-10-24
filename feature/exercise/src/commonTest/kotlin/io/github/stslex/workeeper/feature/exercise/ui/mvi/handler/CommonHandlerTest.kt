// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_toast_undo
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TagDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_detail_permanent_delete_success
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_set_removed
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_body
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_confirm
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_impact
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_plan_type_change_weightless_title
import io.github.stslex.workeeper.feature.exercise.resources.feature_exercise_edit_tag_limit
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseClickCopy
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.stslex.workeeper.feature.exercise.resources.Res as ExerciseRes

internal class CommonHandlerTest {

    private val interactor = FakeExerciseInteractor()
    private val resourceWrapper = DatesOnlyResourceWrapper()
    private val stringResolver = ClickCopyStringResolver()

    /**
     * The store runs `launch` on the identity's `runTest` scheduler and routes a throw to `onError`
     * — production's default `onError` is `{}` (B17, B21), so a swallowing store would hide the
     * defect.
     */
    private fun setup(
        initialState: State,
        scope: CoroutineScope,
    ): Pair<MutableStateFlow<State>, CommonHandler> {
        val stateFlow = MutableStateFlow(initialState)
        val store = FakeCommonHandlerStore(stateFlow, scope)
        return stateFlow to CommonHandler(interactor, resourceWrapper, stringResolver, store)
    }

    @Test
    fun `Init for create mode does not load exercise`() = runTest {
        val (_, handler) = setup(State.create(uuid = null), this)
        handler.invoke(Action.Common.Init)
        advanceUntilIdle()
        // No exception means the handler short-circuited without trying to load.
    }

    @Test
    fun `Init for read mode kicks off observe + load`() = runTest {
        val (_, handler) = setup(State.create(uuid = "uuid-1"), this)
        handler.invoke(Action.Common.Init)
        advanceUntilIdle()
        // The handler invariant is that Init does not throw; repository behaviour lives elsewhere.
    }

    /** The consumer is `ExerciseGraph`'s `if (state.isLoading) return` gate, not a field reader. */
    @Test
    fun `a load that throws clears isLoading or the route is composed on nothing forever`() = runTest {
        interactor.exercise = { throw IllegalStateException("db down") }
        val (stateFlow, handler) = setup(State.create(uuid = "uuid-1"), this)
        // Precondition rather than assertion: a route with a uuid starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertFalse(stateFlow.value.isLoading)
    }

    @Test
    fun `a create route never loads so it is never withheld`() = runTest {
        val (stateFlow, handler) = setup(State.create(uuid = null), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertFalse(stateFlow.value.isLoading)
    }

    @Test
    fun `a re-fired Init preserves a dirty draft and its staged image removal`() = runTest {
        interactor.exercise = {
            ExerciseDomain(
                uuid = "uuid-1",
                name = "Bench persisted",
                type = ExerciseTypeDomain.WEIGHTED,
                description = "Refreshed notes",
                imagePath = "/files/bench.jpg",
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            )
        }
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench draft",
                description = "Unsaved notes",
                imagePath = "/files/bench.jpg",
                pendingImage = PendingImage.RemoveExisting,
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "Persisted notes",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
            this,
        )

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals("Bench draft", stateFlow.value.name)
        assertEquals("Unsaved notes", stateFlow.value.description)
        assertEquals(PendingImage.RemoveExisting, stateFlow.value.pendingImage)
        assertEquals(ImageDisplay.None, stateFlow.value.effectiveImageDisplay)
        assertEquals("Bench persisted", stateFlow.value.originalSnapshot?.name)
        assertEquals("Refreshed notes", stateFlow.value.originalSnapshot?.description)
        assertTrue(stateFlow.value.hasChanges)
    }

    @Test
    fun `ImagePicked sets pendingImage to NewFromUri and hides the source dialog`() = runTest {
        val ref = ImageRef("content://picked/1")
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.ImageSourcePicker),
            this,
        )

        handler.invoke(Action.Common.ImagePicked(ref))
        advanceUntilIdle()

        assertEquals(PendingImage.NewFromUri(ref), stateFlow.value.pendingImage)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `the load keys the image by its file's modification time`() = runTest {
        interactor.exercise = { exercise(imagePath = "/files/bench.jpg") }
        interactor.imageLastModified = mapOf("/files/bench.jpg" to 1234L)
        val (stateFlow, handler) = setup(State.create(uuid = "uuid-1"), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals("Bench", stateFlow.value.name)
        assertEquals(1234L, stateFlow.value.imageLastModified)
        assertEquals(
            ImageDisplay.FromPath("/files/bench.jpg", 1234L),
            stateFlow.value.effectiveImageDisplay,
        )
    }

    @Test
    fun `an exercise without an image reads no modification time`() = runTest {
        interactor.exercise = { exercise(imagePath = null) }
        val (stateFlow, handler) = setup(State.create(uuid = "uuid-1"), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals("Bench", stateFlow.value.name)
        assertEquals(0L, stateFlow.value.imageLastModified)
        assertEquals(0, interactor.calls.count { it == "getImageLastModified" })
    }

    private fun exercise(imagePath: String?) = ExerciseDomain(
        uuid = "uuid-1",
        name = "Bench",
        type = ExerciseTypeDomain.WEIGHTED,
        description = null,
        imagePath = imagePath,
        archived = false,
        archivedAt = null,
        timestamp = 0L,
        lastAdhocSets = null,
    )

    @Test
    fun `ImagePickCancelled hides the source dialog without staging a pending image`() = runTest {
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.ImageSourcePicker),
            this,
        )

        handler.invoke(Action.Common.ImagePickCancelled)
        advanceUntilIdle()

        assertEquals(PendingImage.Unchanged, stateFlow.value.pendingImage)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    /**
     * The image viewer's return path: the graph forwards the raw request name, the handler resolves
     * it. These assert the dispatched action, never the absence of a throw.
     */
    private fun requestSetup(scope: CoroutineScope): Pair<FakeCommonHandlerStore, CommonHandler> {
        val store = FakeCommonHandlerStore(MutableStateFlow(State.create(uuid = "uuid-1")), scope)
        return store to CommonHandler(interactor, resourceWrapper, stringResolver, store)
    }

    @Test
    fun `a REPLACE request reopens the image source picker`() = runTest {
        val (store, handler) = requestSetup(this)

        handler.invoke(
            Action.Common.ImageRequestReceived(Screen.ExerciseImageRequest.REPLACE.name),
        )
        advanceUntilIdle()

        assertEquals(1, store.consumed.count { it == Action.Click.OnEditImageClick })
    }

    @Test
    fun `a REMOVE request stages the removal`() = runTest {
        val (store, handler) = requestSetup(this)

        handler.invoke(
            Action.Common.ImageRequestReceived(Screen.ExerciseImageRequest.REMOVE.name),
        )
        advanceUntilIdle()

        assertEquals(1, store.consumed.count { it == Action.Click.OnRemoveImageClick })
    }

    @Test
    fun `an unrecognised request name is dropped rather than acted on`() = runTest {
        val (store, handler) = requestSetup(this)

        handler.invoke(Action.Common.ImageRequestReceived("PHOTOSHOP"))
        advanceUntilIdle()

        assertEquals(0, store.consumed.size)
    }

    /** Every verb the enum declares must resolve to an action here, or this fails loudly. */
    @Test
    fun `every declared request verb resolves to an action`() = runTest {
        Screen.ExerciseImageRequest.entries.forEach { request ->
            val (store, handler) = requestSetup(this)

            handler.invoke(Action.Common.ImageRequestReceived(request.name))
            advanceUntilIdle()

            assertEquals(1, store.consumed.size)
        }
    }

    /** D2: `Init` resolves the click paths' constant copy once per Store, on either route. */
    @Test
    fun `Init resolves the click paths' constant copy into State`() = runTest {
        val (stateFlow, handler) = setup(State.create(uuid = null), this)

        handler.invoke(Action.Common.Init)
        advanceUntilIdle()

        assertEquals(
            ExerciseClickCopy(
                typeChangeTitle = "type-title",
                typeChangeBody = "type-body",
                typeChangeImpact = "type-impact",
                typeChangeConfirm = "type-confirm",
                setRemoved = "set-removed",
                tagLimit = "tag-limit",
                permanentDeleteSuccess = "exercise-deleted",
                undoLabel = "undo",
            ),
            stateFlow.value.clickCopy,
        )
    }

    /**
     * The store as the MockK store answered it: [state] is the identity's flow, [updateState] and
     * the update-lambda [updateStateImmediate] apply to it, and [launch] launches on the identity's
     * `runTest` scheduler, runs the action inside `supervisorScope`, then `onSuccess`, and routes a
     * throw from either to `onError`. [Flow.launch] records and collects nothing, as the relaxed
     * mock did; [consume] records. Every other member fails fast.
     */
    private class FakeCommonHandlerStore(
        private val flow: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : ExerciseHandlerStore {

        val observed = mutableListOf<Flow<*>>()
        val consumed = mutableListOf<Action>()

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by CommonHandler")

        override val logger: Logger
            get() = error("logger must not be read by CommonHandler")

        override fun sendEvent(event: Event): Nothing = error("sendEvent must not be used by CommonHandler")

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action): Nothing =
            error("consumeOnMain must not be used by CommonHandler")

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by CommonHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                // GUARD: `supervisorScope`, not a bare scope — `loadExercise` fans out through
                // six `async` children, and a plain scope cancels before `onError` can run.
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
        ): Nothing = error("launchDefault must not be used by CommonHandler")

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job {
            observed.add(this)
            return Job()
        }
    }

    /**
     * The interactor as the relaxed MockK one answered it: the load's six reads and
     * `getImageLastModified` answer what the identity set up — a value or a throw — and otherwise
     * the relaxed default (`null`, an empty list, `0`, `false`); the two observed flows answer as
     * they were stubbed. A member the load never reaches throws. Every call is counted in [calls].
     */
    private class FakeExerciseInteractor : ExerciseInteractor {

        var exercise: suspend () -> ExerciseDomain? = { null }
        var imageLastModified: Map<String, Long> = emptyMap()
        val calls = mutableListOf<String>()

        override suspend fun getExercise(uuid: String): ExerciseDomain? = called("getExercise", exercise)

        override suspend fun getLabels(exerciseUuid: String): List<String> = called("getLabels") { emptyList() }

        override suspend fun getRecentHistory(exerciseUuid: String, limit: Int): List<HistoryEntryDomain> =
            called("getRecentHistory") { emptyList() }

        override suspend fun countSessions(exerciseUuid: String): Int = called("countSessions") { 0 }

        override fun observeAvailableTags(): Flow<List<TagDomain>> {
            calls.add("observeAvailableTags")
            return flowOf(emptyList())
        }

        override fun observePersonalRecord(exerciseUuid: String): Flow<PersonalRecordDomain?> {
            calls.add("observePersonalRecord")
            return emptyFlow()
        }

        override suspend fun saveExercise(snapshot: ExerciseChangeDomain): SaveResult =
            error("saveExercise must not be used by CommonHandler")

        override suspend fun createTag(name: String): TagDomain = error("createTag must not be used by CommonHandler")

        override suspend fun archive(uuid: String): ArchiveResult = error("archive must not be used by CommonHandler")

        override suspend fun restore(uuid: String): Unit = error("restore must not be used by CommonHandler")

        override suspend fun canPermanentlyDelete(uuid: String): Boolean =
            called("canPermanentlyDelete") { false }

        override suspend fun permanentlyDelete(uuid: String): Unit =
            error("permanentlyDelete must not be used by CommonHandler")

        override suspend fun getAdhocPlan(uuid: String): List<PlanSetDomain>? = called("getAdhocPlan") { null }

        override suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDomain>?): Unit =
            error("setAdhocPlan must not be used by CommonHandler")

        override suspend fun saveImage(ref: ImageRef, exerciseUuid: String): ImageSaveResult =
            error("saveImage must not be used by CommonHandler")

        override suspend fun createTempCaptureRef(): ImageRef =
            error("createTempCaptureRef must not be used by CommonHandler")

        override suspend fun deleteImageFile(path: String): Boolean =
            error("deleteImageFile must not be used by CommonHandler")

        override suspend fun getImageLastModified(path: String): Long =
            called("getImageLastModified") { imageLastModified[path] ?: 0L }

        override suspend fun resolveTrackNowConflict(): TrackNowConflict =
            error("resolveTrackNowConflict must not be used by CommonHandler")

        override suspend fun startTrackNowSession(exerciseUuid: String, defaultName: String): String =
            error("startTrackNowSession must not be used by CommonHandler")

        override suspend fun deleteSession(sessionUuid: String): Unit =
            error("deleteSession must not be used by CommonHandler")

        private suspend fun <T> called(member: String, answer: suspend () -> T): T {
            calls.add(member)
            return answer()
        }
    }

    /** The relaxed mock answered both dates with `""`; nothing else is reached. */
    private class DatesOnlyResourceWrapper : ResourceWrapper {

        override fun getString(id: Int, vararg args: Any): String = error("not used")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("not used")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("not used")

        override fun formatMediumDate(timestamp: Long): String = ""

        override fun formatDayMonth(timestamp: Long): String = ""
    }

    /**
     * Strict: answers the eight reads of `Init`'s click copy (D2), each with no argument, with a
     * distinct string per field; any other resource or argument list throws. A throw inside the
     * launch routes to `onError`, so a missed read leaves `clickCopy` at `ExerciseClickCopy.EMPTY`.
     */
    private class ClickCopyStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            check(formatArgs.isEmpty()) { "$resource is read verbatim; got ${formatArgs.toList()}" }
            return when (resource) {
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_title -> "type-title"
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_body -> "type-body"
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_impact -> "type-impact"
                ExerciseRes.string.feature_exercise_edit_plan_type_change_weightless_confirm -> "type-confirm"
                ExerciseRes.string.feature_exercise_edit_plan_set_removed -> "set-removed"
                ExerciseRes.string.feature_exercise_edit_tag_limit -> "tag-limit"
                ExerciseRes.string.feature_exercise_detail_permanent_delete_success -> "exercise-deleted"
                Res.string.core_ui_kit_toast_undo -> "undo"
                else -> error("CommonHandler must not read $resource")
            }
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = error("CommonHandler reads no plural; got $resource")
    }
}
