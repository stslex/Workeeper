// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.di.HomeHandlerStore
import io.github.stslex.workeeper.feature.home.domain.HomeInteractor
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.store.BottomSheetState
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Event
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.mvi.store.emptyPagingState
import io.github.stslex.workeeper.feature.home.resources.Res
import io.github.stslex.workeeper.feature.home.resources.feature_home_conflict_progress_format
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ClickHandlerTest {

    private val baseState = emptyPagingState().copy(isActiveLoaded = true)
    private val interactor = RecordingHomeInteractor()
    private val resources = UnreachedResourceWrapper()
    private val strings = ClickHandlerStringResolver()

    @Test
    fun `OnSettingsClick consumes OpenSettings navigation and emits HapticClick`() {
        val store = newStore(baseState)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnSettingsClick)

        assertEquals(1, store.consumed.count { it == Action.Navigation.OpenSettings })
        assertEquals(1, store.events.size)
        val captured = store.events.single()
        assertEquals(true, captured is Event.HapticClick)
    }

    @Test
    fun `OnActiveSessionClick is a no-op when no active session`() {
        val store = newStore(baseState)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnActiveSessionClick)

        assertEquals(emptyList<Action>(), store.consumed)
    }

    @Test
    fun `OnActiveSessionClick consumes OpenLiveWorkoutResume with the session uuid`() {
        val active = State.ActiveSessionInfo(
            sessionUuid = "session-7",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            startedAt = 0L,
            doneCount = 1,
            totalCount = 3,
            elapsedDurationLabel = "00:10",
        )
        val store = newStore(baseState.copy(activeSession = active))
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnActiveSessionClick)

        assertEquals(
            1,
            store.consumed.count {
                it == Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-7")
            },
        )
        assertEquals(1, store.events.size)
        val captured = store.events.single()
        assertEquals(HapticFeedbackType.ContextClick, (captured as Event.HapticClick).type)
    }

    @Test
    fun `OnRecentSessionClick consumes OpenPastSession with the session uuid`() {
        val store = newStore(baseState)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnRecentSessionClick(sessionUuid = "session-22"))

        assertEquals(
            1,
            store.consumed.count {
                it == Action.Navigation.OpenPastSession(sessionUuid = "session-22")
            },
        )
    }

    @Test
    fun `OnPickerSeeAllClick hides picker and consumes OpenAllTrainings`() {
        val visiblePicker = BottomSheetState.TrainingPicker(
            templates = kotlinx.collections.immutable.persistentListOf(),
            isLoading = false,
        )
        val store = newStore(baseState.copy(bottomSheet = visiblePicker))
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnPickerSeeAllClick)

        assertEquals(1, store.consumed.count { it == Action.Navigation.OpenAllTrainings })
    }

    @Test
    fun `OnModeLabelClick opens the mode sheet with a haptic`() {
        val flow = MutableStateFlow(baseState)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnModeLabelClick)

        assertEquals(BottomSheetState.StartModePicker, flow.value.bottomSheet)
        assertEquals(1, store.events.size)
        val captured = store.events.single()
        assertEquals(true, captured is Event.HapticClick)
    }

    @Test
    fun `OnModeLabelClick while the training picker is up replaces the sheet`() {
        val flow = MutableStateFlow(
            baseState.copy(
                bottomSheet = BottomSheetState.TrainingPicker(
                    templates = kotlinx.collections.immutable.persistentListOf(),
                    isLoading = false,
                ),
            ),
        )
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnModeLabelClick)

        assertEquals(BottomSheetState.StartModePicker, flow.value.bottomSheet)
    }

    @Test
    fun `OnModeSelected hides the sheet and persists the MAPPED mode through the interactor`() = runTest {
        val flow = MutableStateFlow(baseState.copy(bottomSheet = BottomSheetState.StartModePicker))
        // A store whose launch actually RUNS its body; the persist call and the UI→domain
        // mapping live inside that coroutine.
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnModeSelected(StartCardModeUi.LAGGING_GROUPS))
        store.runLaunches(this)

        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
        // startCardMode is NOT written here — the DataStore round trip owns it, and `State.init`
        // seeds no mode.
        assertEquals(null, flow.value.startCardMode)
        assertEquals(
            1,
            interactor.setModes.count { it == StartCardModeDomain.LAGGING_GROUPS },
        )
    }

    @Test
    fun `OnModeSheetDismiss hides the sheet without a haptic`() {
        val flow = MutableStateFlow(baseState.copy(bottomSheet = BottomSheetState.StartModePicker))
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnModeSheetDismiss)

        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
        assertEquals(emptyList<Event>(), store.events)
    }

    @Test
    fun `OnPickerTrainingSelected hides the picker before resolving conflict`() {
        val visiblePicker = BottomSheetState.TrainingPicker(
            templates = kotlinx.collections.immutable.persistentListOf(),
            isLoading = false,
        )
        val flow = MutableStateFlow(baseState.copy(bottomSheet = visiblePicker))
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnPickerTrainingSelected(trainingUuid = "tpl-1"))

        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
    }

    @Test
    fun `OnPickerTrainingSelected reports the persisted active session progress`() = runTest {
        val flow = MutableStateFlow(baseState)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnPickerTrainingSelected(trainingUuid = "tpl-1"))
        store.runLaunches(this)

        assertEquals("1 of 2 exercises done", flow.value.pendingConflict?.progressLabel)
    }

    /** The card sends one action carrying nothing, so this uuid came from the body in state. */
    @Test
    fun `OnStartActionClick under a Forgotten body starts THAT training no picker`() = runTest {
        val flow = MutableStateFlow(
            baseState.copy(
                startCardMode = StartCardModeUi.FORGOTTEN_TRAINING,
                startCardBody = StartCardBodyUi.Forgotten(
                    trainingUuid = "tpl-9",
                    trainingName = "Спина и бицепс",
                    metaLabel = "21 день · 6 упражнений",
                ),
            ),
        )
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnStartActionClick)
        store.runLaunches(this)

        assertEquals(
            1,
            store.consumedOnMain.count {
                it == Action.Navigation.OpenLiveWorkoutFresh(trainingUuid = "tpl-9")
            },
        )
        // No picker in between — that is what "starts THAT training directly" means.
        assertEquals(BottomSheetState.Hidden, flow.value.bottomSheet)
        assertEquals(1, store.events.size)
        val captured = store.events.single()
        assertEquals(true, captured is Event.HapticClick)
    }

    /**
     * FORGOTTEN_TRAINING degraded to `Empty` (HD2). The mode is deliberate: it pins that the
     * discriminator is the BODY, not the mode.
     */
    @Test
    fun `OnStartActionClick under an Empty body opens the picker instead`() {
        val flow = MutableStateFlow(
            baseState.copy(
                startCardMode = StartCardModeUi.FORGOTTEN_TRAINING,
                startCardBody = StartCardBodyUi.Empty(message = "Пока нет шаблонов"),
            ),
        )
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnStartActionClick)

        assertEquals(true, flow.value.bottomSheet is BottomSheetState.TrainingPicker)
        assertEquals(emptyList<Action>(), store.consumed)
    }

    /** The frame before the readout's first emission: still the picker, never a crash. */
    @Test
    fun `OnStartActionClick before the body arrives opens the picker`() {
        val flow = MutableStateFlow(baseState.copy(startCardBody = null))
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnStartActionClick)

        assertEquals(true, flow.value.bottomSheet is BottomSheetState.TrainingPicker)
    }

    @Test
    fun `OnConflictDismiss clears pendingConflict`() {
        val state = baseState.copy(
            pendingConflict = State.ConflictInfo(
                activeSessionUuid = "session-1",
                requestedTrainingUuid = "tpl-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        val flow = MutableStateFlow(state)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnConflictDismiss)

        assertEquals(null, flow.value.pendingConflict)
    }

    @Test
    fun `OnConflictResume consumes OpenLiveWorkoutResume with the active session uuid`() {
        val state = baseState.copy(
            pendingConflict = State.ConflictInfo(
                activeSessionUuid = "session-1",
                requestedTrainingUuid = "tpl-1",
                activeSessionName = "Push Day",
                progressLabel = "0 of 0",
            ),
        )
        val flow = MutableStateFlow(state)
        val store = newStoreWithFlow(flow)
        val handler = ClickHandler(
            interactor = interactor,
            resourceWrapper = resources,
            stringResolver = strings,
            store = store,
        )

        handler.invoke(Action.Click.OnConflictResume)

        assertEquals(
            1,
            store.consumed.count {
                it == Action.Navigation.OpenLiveWorkoutResume(sessionUuid = "session-1")
            },
        )
        assertEquals(null, flow.value.pendingConflict)
    }

    private fun newStore(state: State): FakeHomeHandlerStore =
        newStoreWithFlow(MutableStateFlow(state))

    private fun newStoreWithFlow(flow: MutableStateFlow<State>): FakeHomeHandlerStore =
        FakeHomeHandlerStore(flow)

    /**
     * The handler's store as the relaxed MockK store answered it: [updateState] and the
     * update-lambda [updateStateImmediate] apply to the identity's flow, [sendEvent], [consume] and
     * [consumeOnMain] record, and each Store-scope [launch] is recorded without running — an
     * identity that ran it under MockK runs it with [runLaunches] on its own `runTest` scheduler.
     * [Flow.launch] records without collecting. Members [ClickHandler] never reaches fail fast.
     */
    private class FakeHomeHandlerStore(
        private val flow: MutableStateFlow<State>,
    ) : HomeHandlerStore {

        val events = mutableListOf<Event>()
        val consumed = mutableListOf<Action>()
        val consumedOnMain = mutableListOf<Action>()
        val flowLaunches = mutableListOf<Flow<*>>()
        private val launches = mutableListOf<suspend CoroutineScope.() -> Unit>()

        /** Runs every recorded Store-scope action, in order and to completion, on [scope]. */
        suspend fun runLaunches(scope: CoroutineScope) {
            launches.forEach { action -> scope.action() }
        }

        override val state: StateFlow<State> = flow

        override val lastAction: Nothing
            get() = error("lastAction must not be read by ClickHandler")

        override val logger: Logger
            get() = error("logger must not be read by ClickHandler")

        override fun sendEvent(event: Event) {
            events.add(event)
        }

        override fun consume(action: Action) {
            consumed.add(action)
        }

        override suspend fun consumeOnMain(action: Action) {
            consumedOnMain.add(action)
        }

        override fun updateState(update: (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            flow.value = update(flow.value)
        }

        override suspend fun updateStateImmediate(state: State): Nothing =
            error("updateStateImmediate(state) must not be used by ClickHandler")

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job {
            launches.add { action() }
            return Job()
        }

        override fun <T> launchDefault(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            action: suspend CoroutineScope.() -> T,
        ): Nothing = error("launchDefault must not be used by ClickHandler")

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

    /**
     * Answers what the identities reach: [setStartCardMode] records, [resolveStartConflict] answers
     * `tpl-1` with a conflict and `tpl-9` with a fresh start, [getTrainingName] knows the active
     * training, and [observeRecentTrainings] records its limit. Every other member fails fast.
     */
    private class RecordingHomeInteractor : HomeInteractor {

        val setModes = mutableListOf<StartCardModeDomain>()
        val recentTrainingLimits = mutableListOf<Int>()

        override suspend fun setStartCardMode(mode: StartCardModeDomain) {
            setModes.add(mode)
        }

        override suspend fun resolveStartConflict(requestedTrainingUuid: String): StartSessionConflict =
            when (requestedTrainingUuid) {
                "tpl-1" -> StartSessionConflict.NeedsUserChoice(
                    active = ActiveSessionDomain(
                        sessionUuid = "active-1",
                        trainingUuid = "active-training",
                        startedAt = 1L,
                    ),
                    doneCount = 1,
                    totalCount = 2,
                )

                "tpl-9" -> StartSessionConflict.ProceedFresh
                else -> error("unexpected requested training $requestedTrainingUuid")
            }

        override suspend fun getTrainingName(trainingUuid: String): String {
            check(trainingUuid == "active-training") { "unexpected training $trainingUuid" }
            return "Push Day"
        }

        override fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItemDomain>> {
            recentTrainingLimits.add(limit)
            return emptyFlow()
        }

        override fun observeActiveSession(): Nothing =
            error("observeActiveSession must not be used by ClickHandler")

        override fun observeStartCardReadout(mode: StartCardModeDomain, nowMillis: Long): Nothing =
            error("observeStartCardReadout must not be used by ClickHandler")

        override fun observeStartCardMode(): Nothing =
            error("observeStartCardMode must not be used by ClickHandler")

        override fun pagedRecent(): Nothing = error("pagedRecent must not be used by ClickHandler")

        override suspend fun deleteSession(sessionUuid: String): Nothing =
            error("deleteSession is only reached by a launch no identity runs")
    }

    /** No identity reaches the wrapper: the picker mapping runs in a flow nothing collects. */
    private class UnreachedResourceWrapper : ResourceWrapper {

        override fun getString(id: Int, vararg args: Any): Nothing =
            error("ClickHandler reads no copy through ResourceWrapper; id $id")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): Nothing =
            error("ClickHandler reads no plural through ResourceWrapper; id $id")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): Nothing =
            error("no ClickHandler identity reaches the picker mapping")

        override fun formatMediumDate(timestamp: Long): Nothing = error("Not used on this screen")

        override fun formatDayMonth(timestamp: Long): Nothing = error("Not used on this screen")
    }

    /**
     * The strict resolver: today's stub for the conflict progress, and only with the two arguments
     * production passes. Any other resource or argument shape throws.
     */
    private class ClickHandlerStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
            when (resource) {
                Res.string.feature_home_conflict_progress_format -> {
                    check(formatArgs.toList() == listOf(1, 2)) {
                        "conflict_progress_format takes exactly (done, total); got ${formatArgs.toList()}"
                    }
                    "1 of 2 exercises done"
                }

                else -> error("no ClickHandler identity reads $resource")
            }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): Nothing = error("ClickHandler reads no plural; got $resource")
    }
}
