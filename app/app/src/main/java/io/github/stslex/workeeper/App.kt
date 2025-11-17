package io.github.stslex.workeeper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.github.stslex.workeeper.bottom_app_bar.WorkeeperBottomAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.navigation.LocalNavigator
import io.github.stslex.workeeper.core.ui.navigation.LocalRootComponent
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.host.NavHostControllerHolder.Companion.rememberNavHostControllerHolder
import io.github.stslex.workeeper.navigation.NavigatorImpl
import io.github.stslex.workeeper.navigation.RootComponentImpl

@Composable
fun App() {
    AppTheme {
        val navigatorHolder = rememberNavHostControllerHolder()
        val navigator = remember(navigatorHolder) { NavigatorImpl(navigatorHolder) }
        val rootComponent = remember(navigator) { RootComponentImpl(navigator) }
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalRootComponent provides rootComponent,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("AppRoot"),
            ) {
                AnimatedVisibility(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f),
                    visible = navigatorHolder.bottomBarDestination.value != null,
                    enter = fadeIn(
                        tween(AppUi.uiFeatures.defaultAnimationDuration),
                    ) + scaleIn(
                        tween(AppUi.uiFeatures.defaultAnimationDuration),
                    ) + slideIn(
                        initialOffset = { IntOffset(0, 0) },
                        animationSpec = tween(AppUi.uiFeatures.defaultAnimationDuration),
                    ),
                    exit = fadeOut(
                        tween(AppUi.uiFeatures.defaultAnimationDuration),
                    ) + scaleOut(
                        tween(AppUi.uiFeatures.defaultAnimationDuration),
                    ) + slideOut(
                        targetOffset = { fullSize -> IntOffset(0, fullSize.height) },
                        animationSpec = tween(AppUi.uiFeatures.defaultAnimationDuration),
                    ),
                ) {
                    WorkeeperBottomAppBar(
                        selectedItem = navigatorHolder.bottomBarDestination,
                    ) {
                        navigator.navTo(it.screen)
                    }
                }

                AppNavigationHost(
                    modifier = Modifier,
                    navigator = navigator,
                )
            }
        }
    }
}
