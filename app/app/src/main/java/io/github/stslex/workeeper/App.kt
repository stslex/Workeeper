// SPDX-License-Identifier: GPL-3.0-only
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.kit.components.navbar.AppNavBar
import io.github.stslex.workeeper.core.ui.kit.components.navbar.AppNavBarItem
import io.github.stslex.workeeper.core.ui.kit.components.snackbar.AppSnackbar
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.kit.snackbar.resolveSnackbarOutcome
import io.github.stslex.workeeper.core.ui.kit.snackbar.toastTimeoutMillis
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.feature.app_dialogs.impl.ui.AppDialogHost
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.host.BottomBarNavigationListener.Companion.rememberBottomBarNavigationListener
import io.github.stslex.workeeper.navigation.NavigatorExt.NavigationEventBusSetup
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun App() {
    // AppRootViewModel is a plain ViewModel constructed via viewModel {} with deps read from the app
    // graph — commonDataStore off the public contract, navigatorEventBus off the internal AppGraph
    // (concrete, app/app-owned).
    val context = LocalContext.current
    val viewModel: AppRootViewModel = viewModel {
        val graph = (context.applicationContext as AppGraphOwner).appGraph
        AppRootViewModel(
            commonDataStore = graph.commonDataStore,
            navigatorEventBus = graph.navigatorEventBus,
        )
    }
    val themeMode by viewModel.themeMode.collectAsState()

    AppTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val holder = remember(navController) { NavigatorHolder(navController) }
        val navigatorEventBus = viewModel.navigatorEventBus

        val bottomBarNavigationListener = rememberBottomBarNavigationListener(holder)
        val hapticFeedback = LocalHapticFeedback.current

        NavigationEventBusSetup(
            navigatorHolder = holder,
            navigator = navigatorEventBus,
        )

        val snackbarHostState = remember { SnackbarHostState() }

        // B25 branch B: the host owns the toast's lifetime, because the drawing gives a number
        // Material3 has no rung for.
        //
        // `showSnackbar`'s `duration` defaults to `Indefinite` whenever an `actionLabel` is
        // present — a deliberate M3 default, paired with a dismiss affordance this app's `.toast`
        // does not draw. The result was three undo toasts that never went away. `SnackbarDuration`
        // offers only 4000ms and 10000ms; `session-v3f.html` says 5000, so the timeout is applied
        // here instead of rounded onto a rung.
        //
        // The snackbar is therefore shown as `Indefinite` and cancelled by `withTimeoutOrNull` —
        // cancelling the caller removes it from display, which M3's own KDoc guarantees. The
        // accessibility recommendation is applied by `toastTimeoutMillis` rather than left to M3:
        // an `Indefinite` snackbar short-circuits `calculateRecommendedTimeoutMillis` before the
        // system manager is reached, so it is the one duration that silently ignores a user's
        // display-timeout preference. A finite base restores it.
        val accessibilityManager = LocalAccessibilityManager.current
        LaunchedEffect(accessibilityManager) {
            SnackbarManager.snackbar
                .collect { model ->
                    val result = withTimeoutOrNull(
                        toastTimeoutMillis(
                            accessibilityManager = accessibilityManager,
                            hasAction = model.actionLabel != null,
                        ),
                    ) {
                        snackbarHostState.showSnackbar(
                            message = model.message,
                            actionLabel = model.actionLabel,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    // ActionPerformed → action; Dismissed or timeout → onDismissed. The
                    // routing is the kit's own named function so the deferred-delete window
                    // (ED11) is asserted at its selector, not read off this collector.
                    resolveSnackbarOutcome(result, model)
                }
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
                visible = bottomBarNavigationListener.bottomBarDestination.value != null,
                enter = fadeIn(
                    tween(AppUi.motion.base),
                ) + scaleIn(
                    tween(AppUi.motion.base),
                ) + slideIn(
                    initialOffset = { IntOffset(0, 0) },
                    animationSpec = tween(AppUi.motion.base),
                ),
                exit = fadeOut(
                    tween(AppUi.motion.base),
                ) + scaleOut(
                    tween(AppUi.motion.base),
                ) + slideOut(
                    targetOffset = { fullSize -> IntOffset(0, fullSize.height) },
                    animationSpec = tween(AppUi.motion.base),
                ),
            ) {
                // The v2 bar clipped its own top corners from `Radius.largest` (128dp) down to 0
                // across the enter transition. That treatment goes with the bar: `#s-nav` draws a
                // flat `--sec` track with a hairline along its top edge, and a 128dp top radius
                // both contradicts the drawn shape and cuts the hairline off at both ends. The
                // show/hide transition itself is untouched — only the shape animation the deleted
                // component owned.
                AppNavBar(
                    items = BottomBarItem.entries.map { item ->
                        AppNavBarItem(
                            icon = item.icon,
                            contentDescription = stringResource(item.titleRes),
                            testTag = item.testTag,
                        )
                    },
                    selectedIndex = bottomBarNavigationListener.selectedIndex.value,
                    onSelect = { index ->
                        val item = BottomBarItem.entries[index]
                        // §26 "Haptics": SegmentTick on a nav tab change. Fired here rather than
                        // inside `AppNavBar` because every haptic in this app is fired at a
                        // feature/graph level — `core/ui/kit/src/main` has none, measured.
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        navigatorEventBus.navTo(item.screen)
                    },
                    modifier = Modifier.testTag("WorkeeperBottomAppBar"),
                )
            }

            AppNavigationHost(
                modifier = Modifier,
                navigatorHolder = holder,
            )

            // NO host-owned affordance goes here. The host may not place a control in a band a
            // screen owns and can replace whole — §26, "A host-owned affordance may not float over
            // a bar a screen replaces", and B26 for what the last one cost.
            //
            // Interim, stated rather than papered over: all-trainings, all-exercises and archive
            // have **no settings entry of their own** until that pass rules the resting bar. Home
            // keeps its own (`HomeScreen`'s `actions`), and Home is one tap away on the nav bar, so
            // the three screens reach settings in two taps. Do not restore this overlay for them.

            SnackbarHost(
                modifier = Modifier.align(Alignment.BottomCenter),
                hostState = snackbarHostState,
            ) { data ->
                AppSnackbar(snackbarData = data)
            }

            // Sibling of AppNavigationHost (not a child of any destination) so the
            // dialog appears regardless of the current route and survives
            // navigation. Its state lives in DataStore — surviving process
            // restart is the load-bearing property.
            // See documentation/feature-specs/app-dialogs.md → "AppDialogHost
            // mounting".
            AppDialogHost()
        }
    }
}
