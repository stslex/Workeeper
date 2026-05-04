// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mvi.store

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
import io.github.stslex.workeeper.core.ui.plan_editor.di.PlanEditorHandlerStoreImpl
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler.ClickHandler
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler.CommonHandler
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler.InputHandler
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler.PlanEditorComponent
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State

@HiltViewModel(assistedFactory = PlanEditorStoreImpl.Factory::class)
internal class PlanEditorStoreImpl @AssistedInject constructor(
    @Assisted component: PlanEditorComponent,
    commonHandler: CommonHandler,
    clickHandler: ClickHandler,
    inputHandler: InputHandler,
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
            is Action.Navigation -> component as NavigationHandler
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

private fun io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.toMode(): State.Mode {
    val performed = performedExerciseUuid
    val exercise = exerciseUuid
    return when {
        performed != null && exercise != null -> State.Mode.PerformedExercise(
            performedExerciseUuid = performed,
            exerciseUuid = exercise,
            trainingUuid = trainingUuid,
        )
        exercise != null -> State.Mode.Exercise(exerciseUuid = exercise)
        else -> error(
            "Screen.PlanEditor must carry exerciseUuid (got performed=$performed, exercise=$exercise)",
        )
    }
}
