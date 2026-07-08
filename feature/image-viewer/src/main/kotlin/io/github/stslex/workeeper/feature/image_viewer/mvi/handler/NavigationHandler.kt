// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerScope
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action

@SingleIn(ImageViewerScope::class)
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
        }
    }
}
