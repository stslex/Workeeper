package io.github.stslex.workeeper.feature.single_training.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Component
import io.github.stslex.workeeper.core.ui.navigation.Navigator

internal interface SingleTrainingComponent : Component {

    val uuid: String?

    companion object {

        fun create(
            navigator: Navigator,
            uuid: String?,
        ): SingleTrainingComponent = NavigationHandler(
            navigator = navigator,
            uuid = uuid,
        )
    }
}
