// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.SAVED_STATE_PLAN_EDITOR_SAVED
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.PlanEditor,
) : PlanEditorComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.BackAfterSave -> {
                // Caller's backstack entry observes SAVED_STATE_PLAN_EDITOR_SAVED to
                // pick up the new plan on resume. We flip it to true *before* popping
                // so the LaunchedEffect on the caller side sees the value transition.
                navigator.navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(SAVED_STATE_PLAN_EDITOR_SAVED, true)
                navigator.popBack()
            }
        }
    }
}
