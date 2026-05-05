// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.Companion.planEditorSavedAttr
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action

@Suppress("MviHandlerConstructorRule")
internal class NavigationHandler(
    private val navigator: Navigator,
    data: Screen.PlanEditor,
) : PlanEditorComponent(data), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.BackAfterSave -> navigator.popBack(
                planEditorSavedAttr.toPairValue(true),
            )
        }
    }
}
