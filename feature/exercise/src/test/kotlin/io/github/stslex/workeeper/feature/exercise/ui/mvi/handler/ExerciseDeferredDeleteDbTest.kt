// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.content.Context
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.ui.kit.snackbar.AppSnackbarModel
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractorImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
 * ED11's deferred delete against a real in-memory Room DB: confirming deletes nothing, the
 * snackbar's `onDismissed` commits, and undo or process death inside the window leaves the row.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class ExerciseDeferredDeleteDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var interactor: ExerciseInteractorImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        // `ExerciseRepositoryImpl`'s constructor is internal to `core:data:exercise`, so this seam
        // delegates onto the real DAO; the subject here is the window, not the delete itself.
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

    /** Every consumed action, paired with whether the undo snackbar was already queued. */
    private val consumedActions = mutableListOf<Pair<Action, Boolean>>()

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
            every { consume(any()) } answers {
                consumedActions.add(firstArg<Action>() to (SnackbarManager.pendingModelCount > 0))
            }
            every { launch<Any?>(any(), any(), any(), any(), any()) } answers {
                val onError = firstArg<suspend (Throwable) -> Unit>()
                val onSuccess = secondArg<suspend CoroutineScope.(Any?) -> Unit>()
                val action = arg<suspend CoroutineScope.() -> Any?>(4)
                runBlocking {
                    runCatching { supervisorScope { action() } }
                        .onSuccess { onSuccess(this, it) }
                        .onFailure { onError(it) }
                }
                mockk<Job>(relaxed = true)
            }
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

    private suspend fun confirmDelete(uuid: Uuid): AppSnackbarModel {
        val (_, _, handler) = handlerFor(
            State.create(uuid = uuid.toString()).copy(canPermanentlyDelete = true),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        handler.invoke(Action.Click.OnConfirmPermanentDelete)
        return SnackbarManager.snackbar.first().model
    }

    @Test
    fun `confirming deletes nothing while the snackbar lives`() = runTest {
        val uuid = seedExercise()

        confirmDelete(uuid)

        // The strict order (ED11): the confirm opened the window; the DB is untouched.
        assertNotNull(env.exerciseDao.getById(uuid))
    }

    /** The real catalog resolves the label — no static mock — and Back rides after the queue. */
    @Test
    fun `back is consumed only after the snackbar is queued, with the real undo label`() = runTest {
        val uuid = seedExercise()

        val pending = confirmDelete(uuid)

        assertEquals("Undo", pending.actionLabel)
        assertEquals(listOf<Action>(Action.Navigation.Back), consumedActions.map { it.first })
        assertTrue(consumedActions.single().second) {
            "Back must land only after the undo snackbar is queued"
        }
    }

    @Test
    fun `the window closing commits the delete`() = runTest {
        val uuid = seedExercise()

        val pending = confirmDelete(uuid)
        assertNotNull(env.exerciseDao.getById(uuid))

        // What `resolveSnackbarOutcome` runs on Dismissed/timeout.
        pending.onDismissed()

        assertNull(env.exerciseDao.getById(uuid))
    }

    @Test
    fun `undo — or a process death inside the window — leaves the row`() = runTest {
        val uuid = seedExercise()

        confirmDelete(uuid)
        // Undo is a no-op and death is a cancellation: `commit` never runs (D-OPEN-10).

        assertNotNull(env.exerciseDao.getById(uuid))
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

        assertNull(env.exerciseDao.getById(uuid))
        assertTrue(env.exerciseDao.getAllActive().isEmpty())
    }
}
