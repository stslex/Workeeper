package io.github.stslex.workeeper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult.ActionPerformed
import androidx.compose.material3.SnackbarResult.Dismissed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.stslex.workeeper.bottom_app_bar.WorkeeperBottomAppBar
import io.github.stslex.workeeper.core.ui.kit.components.snackbar.AppSnackbar
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.navigation.LocalRootComponent
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.host.NavHostControllerWrapper.Companion.rememberNavHostControllerHolder
import io.github.stslex.workeeper.navigation.NavigatorImpl
import io.github.stslex.workeeper.navigation.RootComponentImpl

private val TOP_APP_BAR_HEIGHT = 64.dp
private val TOP_APP_BAR_ACTION_PADDING = 4.dp

@Composable
fun App() {
    val rootViewModel: AppRootViewModel = hiltViewModel()
    val themeMode by rootViewModel.themeMode.collectAsState()

    AppTheme(themeMode = themeMode) {
        val navWrapper = rememberNavHostControllerHolder(rootViewModel.navigationHolderProducer)
        val navigator = remember(navWrapper) { NavigatorImpl(rootViewModel.navigationHolder) }
        val rootComponent = remember(navigator) { RootComponentImpl(navigator) }
        CompositionLocalProvider(
            LocalRootComponent provides rootComponent,
        ) {
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                SnackbarManager.snackbar
                    .collect { model ->
                        val result = snackbarHostState.showSnackbar(
                            message = model.message,
                            actionLabel = model.actionLabel,
                            withDismissAction = model.withDismissAction,
                        )
                        when (result) {
                            ActionPerformed -> model.action()

                            Dismissed -> Unit // No-op
                        }
                    }
            }
            WorkeeperBottomAppBar(
                selectedItem = navWrapper.bottomBarDestination,
            ) {
                navigator.navTo(it.screen)
            }
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
                    visible = navWrapper.bottomBarDestination.value != null,
                    enter = fadeIn(
                        tween(AppUi.motion.normal),
                    ) + scaleIn(
                        tween(AppUi.motion.normal),
                    ) + slideIn(
                        initialOffset = { IntOffset(0, 0) },
                        animationSpec = tween(AppUi.motion.normal),
                    ),
                    exit = fadeOut(
                        tween(AppUi.motion.normal),
                    ) + scaleOut(
                        tween(AppUi.motion.normal),
                    ) + slideOut(
                        targetOffset = { fullSize -> IntOffset(0, fullSize.height) },
                        animationSpec = tween(AppUi.motion.normal),
                    ),
                ) {
                    val cornerRadius by transition.animateDp(
                        transitionSpec = {
                            tween(
                                durationMillis = AppUi.motion.normal,
                                easing = FastOutSlowInEasing,
                            )
                        },
                        label = "bottom-bar-corner-radius",
                    ) { state ->
                        when (state) {
                            EnterExitState.PreEnter -> AppDimension.Radius.largest
                            EnterExitState.Visible -> 0.dp
                            EnterExitState.PostExit -> AppDimension.Radius.largest
                        }
                    }

                    WorkeeperBottomAppBar(
                        modifier = Modifier.clip(
                            RoundedCornerShape(
                                topStart = cornerRadius,
                                topEnd = cornerRadius,
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp,
                            ),
                        ),
                        selectedItem = navWrapper.bottomBarDestination,
                    ) {
                        navigator.navTo(it.screen)
                    }
                }

                AppNavigationHost(
                    modifier = Modifier,
                    navigator = navigator,
                )

                if (navWrapper.bottomBarDestination.value != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .systemBarsPadding()
                            .height(TOP_APP_BAR_HEIGHT)
                            .padding(end = TOP_APP_BAR_ACTION_PADDING),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            modifier = Modifier.testTag("AppSettingsEntry"),
                            onClick = { navigator.navTo(Screen.Settings) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = AppUi.colors.textPrimary,
                            )
                        }
                    }
                }

                SnackbarHost(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hostState = snackbarHostState,
                ) { data ->
                    AppSnackbar(snackbarData = data)
                }
            }
        }
    }
}
