// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.Screen.ExerciseImageRequest
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

internal class NavigationHandlerTest {

    @Test
    fun `Back triggers popBack`() {
        val navigator = RecordingNavigator()
        val handler = NavigationHandler(navigator)

        handler.invoke(Action.Navigation.Back)

        assertEquals(1, navigator.popBackCount)
        assertEquals(emptyList(), navigator.results)
    }

    @Test
    fun `BackWithRequest pops ExerciseImage with the exact request name`() {
        val navigator = RecordingNavigator()
        val handler = NavigationHandler(navigator)

        handler.invoke(Action.Navigation.BackWithRequest(ExerciseImageRequest.REPLACE))

        assertEquals(0, navigator.popBackCount)
        assertEquals(
            listOf(NavigationResult(Screen.ExerciseImage::class, ExerciseImageRequest.REPLACE.name)),
            navigator.results,
        )
    }
}

private data class NavigationResult(
    val destination: KClass<*>,
    val result: Any,
)

private class RecordingNavigator : Navigator {

    var popBackCount: Int = 0
        private set
    val results = mutableListOf<NavigationResult>()

    override fun popBack() {
        popBackCount += 1
    }

    override fun <S, R : Any> popBackWithResult(
        destination: KClass<S>,
        result: R,
    ) where S : ScreenWithResult<R> {
        results += NavigationResult(destination, result)
    }

    override fun navTo(screen: Screen): Nothing =
        error("navTo must not be used by NavigationHandler")

    override fun replaceTo(screen: Screen): Nothing =
        error("replaceTo must not be used by NavigationHandler")

    override fun restartApp(): Nothing =
        error("restartApp must not be used by NavigationHandler")

    override fun openRecovery(): Nothing =
        error("openRecovery must not be used by NavigationHandler")
}
