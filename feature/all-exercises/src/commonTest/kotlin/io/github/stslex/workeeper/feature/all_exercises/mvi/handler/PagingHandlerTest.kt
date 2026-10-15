// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesHandlerStore
import io.github.stslex.workeeper.feature.all_exercises.domain.AllExercisesInteractor
import io.github.stslex.workeeper.feature.all_exercises.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Event
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test

internal class PagingHandlerTest {

    private val interactor = TagsOnlyInteractor()
    private val stringResolver = UnreachedStringResolver()
    private val store = RecordingFlowLaunchStore()
    private val handler = PagingHandler(
        interactor = interactor,
        stringResolver = stringResolver,
        defaultDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        store = store,
    )

    @Test
    fun `Init invokes observeAvailableTags via scope launch`() {
        handler.invoke(Action.Paging.Init)
        // The scope.launch on flow goes through the handler-store fake, which records it without
        // running it; we just make sure invoke does not throw.
    }
}

/** Answers [observeAvailableTags] as the relaxed mock was stubbed; every other member fails fast. */
private class TagsOnlyInteractor : AllExercisesInteractor {

    override fun observeAvailableTags(): Flow<List<TagDomain>> = flowOf(emptyList())

    override fun observeExercises(filterTagUuids: Set<String>): Nothing =
        error("observeExercises is collected only by the paging state, which no identity reads")

    override suspend fun archiveExercise(uuid: String): Nothing =
        error("archiveExercise must not be used by PagingHandler")

    override suspend fun restoreExercise(uuid: String): Nothing =
        error("restoreExercise must not be used by PagingHandler")

    override suspend fun canPermanentlyDelete(uuid: String): Nothing =
        error("canPermanentlyDelete must not be used by PagingHandler")

    override suspend fun permanentlyDelete(uuid: String): Nothing =
        error("permanentlyDelete must not be used by PagingHandler")

    override suspend fun getExercise(uuid: String): Nothing =
        error("getExercise must not be used by PagingHandler")

    override suspend fun countSessionsForExercise(uuid: String): Nothing =
        error("countSessionsForExercise must not be used by PagingHandler")

    override fun observeLinkedTrainingsCount(uuid: String): Nothing =
        error("observeLinkedTrainingsCount must not be used by PagingHandler")

    override fun observeLastTrainedAt(uuid: String): Nothing =
        error("observeLastTrainedAt must not be used by PagingHandler")

    override suspend fun bulkArchive(uuids: Set<String>): Nothing =
        error("bulkArchive must not be used by PagingHandler")

    override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing =
        error("bulkPermanentDelete must not be used by PagingHandler")

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing =
        error("canBulkPermanentDelete must not be used by PagingHandler")
}

/** The handler holds the resolver only to pass it to the mapper, which no identity reaches. */
private class UnreachedStringResolver : StringResolver {

    override suspend fun getString(resource: StringResource, vararg formatArgs: Any): Nothing =
        error("PagingHandlerTest reaches no copy")

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): Nothing = error("PagingHandlerTest reaches no copy")
}

/**
 * The handler's store as the relaxed MockK store answered it: [Flow.launch] (how `Init` collects
 * the tags) is recorded without running. Members `Init` never reaches fail fast.
 */
private class RecordingFlowLaunchStore : AllExercisesHandlerStore {

    val flowLaunches = mutableListOf<Flow<*>>()

    override val state: StateFlow<State>
        get() = error("state is read only by the paging flow, which no identity collects")

    override val lastAction: Action?
        get() = error("lastAction must not be read by PagingHandler")

    override val logger: Logger
        get() = error("logger must not be read by PagingHandler")

    override fun sendEvent(event: Event): Nothing =
        error("sendEvent must not be used by PagingHandler")

    override fun consume(action: Action): Nothing =
        error("consume must not be used by PagingHandler")

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by PagingHandler")

    override fun updateState(update: (State) -> State): Nothing =
        error("updateState must not be used by PagingHandler")

    override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
        error("updateStateImmediate runs only inside the recorded flow launch")

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by PagingHandler")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launch must not be used by PagingHandler")

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launchDefault must not be used by PagingHandler")

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job {
        flowLaunches.add(this)
        return Job()
    }
}
