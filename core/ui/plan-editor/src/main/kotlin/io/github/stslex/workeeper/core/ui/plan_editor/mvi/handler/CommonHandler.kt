// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.SetTypeDomain
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State.Mode
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
        }
        launch(
            onError = { sendEvent(Event.ShowError(ErrorType.LoadFailed)) },
        ) {
            val result = interactor.loadPlan(
                exerciseUuid = exerciseUuid,
                trainingUuid = trainingUuid,
            )
            when (result) {
                is PlanEditorLoadResult.Success -> {
                    val draft = result.plan.map { it.toUi() }.toImmutableList()
                    updateState { current ->
                        current.copy(
                            isLoading = false,
                            exerciseName = result.exerciseName,
                            isWeighted = result.isWeighted,
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

internal fun PlanSetDomain.toUi(): PlanSetUiModel = PlanSetUiModel(
    weight = weight,
    reps = reps,
    type = type.toUi(),
)

internal fun SetTypeDomain.toUi(): SetTypeUiModel = when (this) {
    SetTypeDomain.WARMUP -> SetTypeUiModel.WARMUP
    SetTypeDomain.WORK -> SetTypeUiModel.WORK
    SetTypeDomain.FAILURE -> SetTypeUiModel.FAILURE
    SetTypeDomain.DROP -> SetTypeUiModel.DROP
}

internal fun PlanSetUiModel.toDomain(): PlanSetDomain = PlanSetDomain(
    weight = weight,
    reps = reps,
    type = type.toDomain(),
)

internal fun SetTypeUiModel.toDomain(): SetTypeDomain = when (this) {
    SetTypeUiModel.WARMUP -> SetTypeDomain.WARMUP
    SetTypeUiModel.WORK -> SetTypeDomain.WORK
    SetTypeUiModel.FAILURE -> SetTypeDomain.FAILURE
    SetTypeUiModel.DROP -> SetTypeDomain.DROP
}
