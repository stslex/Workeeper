// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store

import androidx.annotation.VisibleForTesting
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreFactory
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStoreImpl
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.EditorHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.PlanEditorComponent
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State

@HiltViewModel(assistedFactory = PlanEditorStoreImpl.Factory::class)
internal class PlanEditorStoreImpl @AssistedInject constructor(
    @Assisted component: PlanEditorComponent,
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
    initialState = State.init(component.data.toMode()),
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
    interface Factory : StoreFactory<PlanEditorComponent, PlanEditorStoreImpl>

    companion object {

        @VisibleForTesting
        private const val NAME = "PlanEditor"
    }
}

internal fun Screen.PlanEditor.toMode(): State.Mode {
    val performed = performedExerciseUuid
    val exercise = exerciseUuid
    val training = trainingUuid
    return when {
        exercise == null -> error(
            "Screen.PlanEditor must carry exerciseUuid (got performed=$performed, exercise=$exercise)",
        )
        // Live workout (performed != null) or single-training (training != null) → backing
        // store is `training_exercise_table.plan_sets` keyed by (trainingUuid, exerciseUuid).
        // Live-workout's adhoc branch passes performed != null with training == null, in which
        // case the editor falls back to `last_adhoc_sets`.
        performed != null || !training.isNullOrBlank() -> State.Mode.PerformedExercise(
            performedExerciseUuid = performed,
            exerciseUuid = exercise,
            trainingUuid = training,
        )
        // Exercise-detail Edit plan: no live session, no training association — saves to the
        // exercise's own `last_adhoc_sets` row.
        else -> State.Mode.Exercise(exerciseUuid = exercise)
    }
}
