// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.Companion.planEditorDraftResultAttr
import io.github.stslex.workeeper.core.ui.navigation.Screen.PlanEditor.Companion.planEditorSavedAttr
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorScope
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action

@SingleIn(PlanEditorScope::class)
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.BackAfterSave -> navigator.popBack(
                planEditorSavedAttr.toPairValue(true),
            )

            is Action.Navigation.BackAfterDraftSave -> navigator.popBack(
                planEditorDraftResultAttr.toPairValue(action.resultJson),
            )
        }
    }
}
