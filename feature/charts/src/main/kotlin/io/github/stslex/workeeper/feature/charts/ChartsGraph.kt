package io.github.stslex.workeeper.feature.charts

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.navScreen

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.chartsGraph(
    navigator: Navigator,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier
) {
    navScreen<Screen.Charts> {
        Text("CHARTS")
    }
}