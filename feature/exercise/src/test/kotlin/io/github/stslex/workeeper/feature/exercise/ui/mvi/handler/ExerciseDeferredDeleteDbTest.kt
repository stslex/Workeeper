// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

/**
 * ED11's deferred delete against a REAL in-memory Room database — the row is the witness,
 * driven through the real [ClickHandler] and the real interactor/repository so the claim is
 * about the mechanism and not about a test-built precondition (B23's rule).
 *
 * The window itself is the app-level snackbar's lifetime; its close signal is
 * `resolveSnackbarOutcome` (asserted in `SnackbarOutcomeTest`), and what it runs on close is
 * the [Event.ShowPermanentDeleteUndo.commit] captured here. So the three directions are:
 *
 *  - confirming DELETES NOTHING — the row survives until the window closes (never
 *    delete-first-and-reinsert);
 *  - the window closing — [Event.ShowPermanentDeleteUndo.commit] — removes the row;
 *  - «Отменить», or a process death inside the window (D-OPEN-10), never runs the commit,
 *    and the row survives. The two are one case at this seam on purpose: undo's action is
 *    a no-op and death is a cancellation, and both leave the commit un-run.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class ExerciseDeferredDeleteDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var interactor: ExerciseInteractorImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        // `ExerciseRepositoryImpl`'s constructor is internal to `core:data:exercise`, so the
        // repository seam is a one-method delegate onto the REAL DAO over the REAL database —
        // the impl's own transactional delete is proven in `ExerciseRepositoryImplDbTest`;
        // THIS file's subject is the window: who calls the delete, and when.
        val repository = mockk<io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository>(
            relaxed = true,
        ) {
            coEvery { permanentDelete(any()) } coAnswers {
                env.exerciseDao.permanentDelete(Uuid.parse(firstArg<String>()))
            }
        }
        interactor = ExerciseInteractorImpl(
            exerciseRepository = repository,
            tagRepository = mockk(relaxed = true),
            imageStorage = mockk<ImageStorage>(relaxed = true),
            personalRecordRepository = mockk(relaxed = true),
            archiveExerciseUseCase = mockk(relaxed = true),
            resolveTrackNowConflictUseCase = mockk(relaxed = true),
            startTrackNowSessionUseCase = mockk(relaxed = true),
            deleteSessionUseCase = mockk(relaxed = true),
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    private fun handlerFor(state: State): Triple<MutableStateFlow<State>, MutableList<Event>, ClickHandler> {
        val stateFlow = MutableStateFlow(state)
        val events = mutableListOf<Event>()
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
            every { sendEvent(any()) } answers { events.add(firstArg()) }
        }
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = mockk<ResourceWrapper>(relaxed = true),
            context = mockk<Context>(relaxed = true),
            mainDispatcher = Dispatchers.Unconfined,
            store = store,
        )
        return Triple(stateFlow, events, handler)
    }

    private suspend fun seedExercise(): Uuid {
        val uuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        return uuid
    }

    private fun confirmDelete(uuid: Uuid): Event.ShowPermanentDeleteUndo {
        val (_, events, handler) = handlerFor(
            State.create(uuid = uuid.toString()).copy(canPermanentlyDelete = true),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        return events.filterIsInstance<Event.ShowPermanentDeleteUndo>().single()
    }

    @Test
    fun `confirming deletes nothing while the snackbar lives`() = runTest {
        val uuid = seedExercise()

        confirmDelete(uuid)

        // The strict order (ED11): the confirm opened the window; the DB is untouched.
        assertNotNull(env.exerciseDao.getById(uuid))
    }

    @Test
    fun `the window closing commits the delete`() = runTest {
        val uuid = seedExercise()

        val pending = confirmDelete(uuid)
        assertNotNull(env.exerciseDao.getById(uuid))

        // What `resolveSnackbarOutcome` runs on Dismissed/timeout.
        pending.commit()

        assertNull(env.exerciseDao.getById(uuid))
    }

    @Test
    fun `undo — or a process death inside the window — leaves the row`() = runTest {
        val uuid = seedExercise()

        confirmDelete(uuid)
        // «Отменить» is a no-op action and death is a cancellation: in both, `commit`
        // never runs. Nothing to invoke IS the case (D-OPEN-10).

        assertNotNull(env.exerciseDao.getById(uuid))
    }

    /**
     * The dead-store hazard named in the mechanism report: the commit must not need the
     * Store's scope, because the screen popped. The lambda holds the interactor only, so it
     * still deletes after the Store's state is gone from every reader — modelled here by
     * committing with no handler or store in scope at all.
     */
    @Test
    fun `the commit outlives the screen that scheduled it`() = runTest {
        val uuid = seedExercise()
        var pending: suspend () -> Unit
        run {
            pending = confirmDelete(uuid).commit
        }

        pending()

        assertNull(env.exerciseDao.getById(uuid))
        assertTrue(env.exerciseDao.getAllActive().isEmpty())
    }
}
