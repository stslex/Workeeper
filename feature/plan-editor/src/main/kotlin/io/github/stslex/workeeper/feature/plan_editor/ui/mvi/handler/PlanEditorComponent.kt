// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen

abstract class PlanEditorComponent(
    data: Screen.PlanEditor,
) : Component<Screen.PlanEditor>(data) {

    companion object {

        fun create(navigator: Navigator, data: Screen.PlanEditor): PlanEditorComponent =
            NavigationHandler(navigator, data)
    }
}
