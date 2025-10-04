package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.charts.mvi.handler.NavigationHandler
import io.mockk.mockk
import org.junit.jupiter.api.Disabled

@Disabled
@Suppress("unused")
internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator)
}
