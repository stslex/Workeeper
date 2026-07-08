// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import androidx.annotation.VisibleForTesting
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStoreImpl
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.EditorHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Metro assisted Store: @AssistedInject + @Assisted screen; graph exposes the @AssistedFactory
// (never the Store). No @HiltViewModel; unscoped (ViewModelStore retention via rememberMetroStoreProcessor).
@AssistedInject
internal class PlanEditorStoreImpl(
    @Assisted screen: Screen.PlanEditor,
    navigationHandler: NavigationHandler,
    commonHandler: CommonHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
    editorHandler: EditorHandler,
    storeDispatchers: StoreDispatchers,
    handlerStore: PlanEditorHandlerStoreImpl,
    analyticsHolder: AnalyticsHolder,
    loggerHolder: LoggerHolder,
) : BaseStore<State, Action, Event>(
    name = NAME,
    initialState = screen.toInitialState(),
    handlerCreator = { action ->
        when (action) {
            is Action.Common -> commonHandler
            is Action.Click -> clickHandler
            is Action.Input -> inputHandler
            is Action.Navigation -> navigationHandler
            is Action.EditorAction -> editorHandler
        }
    },
    storeEmitter = handlerStore,
    storeDispatchers = storeDispatchers,
    initialActions = listOf(Action.Common.Init),
    analyticsHolder = analyticsHolder,
    loggerHolder = loggerHolder,
) {

    @AssistedFactory
    interface Factory : StoreFactory<Screen.PlanEditor, PlanEditorStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "PlanEditor"
    }
}

internal fun Screen.PlanEditor.toMode(): State.Mode = when (this) {
    is Screen.PlanEditor.Existing -> {
        val performed = performedExerciseUuid
        val exercise = exerciseUuid
        val training = trainingUuid
        when {
            exercise == null -> error(
                "Screen.PlanEditor.Existing must carry exerciseUuid (got performed=$performed, exercise=$exercise)",
            )
            // Live workout (performed != null) or single-training (training != null) →
            // backing store is `training_exercise_table.plan_sets` keyed by
            // (trainingUuid, exerciseUuid). Live-workout's adhoc branch passes
            // performed != null with training == null, in which case the editor falls
            // back to `last_adhoc_sets`.
            performed != null || !training.isNullOrBlank() -> State.Mode.PerformedExercise(
                performedExerciseUuid = performed,
                exerciseUuid = exercise,
                trainingUuid = training,
            )
            // Exercise-detail Edit plan: no live session, no training association — saves
            // to the exercise's own `last_adhoc_sets` row plus `exercise_table.type`.
            else -> State.Mode.Exercise(exerciseUuid = exercise)
        }
    }

    is Screen.PlanEditor.Draft -> State.Mode.Draft
}

internal fun Screen.PlanEditor.toInitialState(): State {
    val mode = toMode()
    val seedType = when (this) {
        is Screen.PlanEditor.Draft -> initialType
        // Existing modes seed type as WEIGHTED until CommonHandler.Init loads the real
        // value from disk. The Composable doesn't render the toggle until
        // `state.isLoading == false`, so the user never sees the placeholder.
        is Screen.PlanEditor.Existing -> ExerciseTypeUiModel.WEIGHTED
    }
    val seedPlan = when (this) {
        is Screen.PlanEditor.Draft ->
            initialPlanJson
                ?.let { json ->
                    Json.decodeFromString(
                        ListSerializer(PlanSetUiModel.serializer()),
                        json,
                    )
                }
                ?.toImmutableList()
                ?: persistentListOf()

        is Screen.PlanEditor.Existing -> persistentListOf()
    }
    return State.init(mode = mode, seedType = seedType, seedPlan = seedPlan)
}
