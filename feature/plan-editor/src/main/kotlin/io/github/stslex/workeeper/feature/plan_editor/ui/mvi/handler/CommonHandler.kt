// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.ui.mapper.PlanEditorMapper.toUi
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State.Mode
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@ViewModelScoped
internal class CommonHandler @Inject constructor(
    private val interactor: PlanEditorInteractor,
    store: PlanEditorHandlerStore,
) : Handler<Action.Common>, PlanEditorHandlerStore by store {

    override fun invoke(action: Action.Common) {
        when (action) {
            Action.Common.Init -> loadPlan()
        }
    }

    private fun loadPlan() {
        val mode = state.value.mode
        val (exerciseUuid, trainingUuid) = when (mode) {
            is Mode.Exercise -> mode.exerciseUuid to null
            is Mode.PerformedExercise -> mode.exerciseUuid to mode.trainingUuid
            // Draft mode has no DB anchor — the seed already lives in State, so skip the
            // load and let the editor render immediately.
            Mode.Draft -> return
        }
        launchDefault(
            onError = { sendEvent(Event.ShowError(ErrorType.LoadFailed)) },
        ) {
            val result = interactor.loadPlan(
                exerciseUuid = exerciseUuid,
                trainingUuid = trainingUuid,
            )
            when (result) {
                is PlanEditorLoadResult.Success -> {
                    val draft = result.plan.map { it.toUi() }.toImmutableList()
                    val type = result.type.toUi()
                    updateState { current ->
                        current.copy(
                            isLoading = false,
                            exerciseName = result.exerciseName,
                            type = type,
                            initialType = type,
                            initialDraft = draft,
                            draft = draft,
                        )
                    }
                }

                PlanEditorLoadResult.NotFound -> {
                    sendEvent(Event.ShowError(ErrorType.LoadFailed))
                    updateState { it.copy(isLoading = false) }
                }
            }
        }
    }
}
