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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppRootDepsHolder
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiGenerationsHolder
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.kit.components.navbar.AppNavBar
import io.github.stslex.workeeper.core.ui.kit.components.navbar.AppNavBarItem
import io.github.stslex.workeeper.core.ui.kit.components.snackbar.AppSnackbar
import io.github.stslex.workeeper.core.ui.kit.snackbar.SnackbarManager
import io.github.stslex.workeeper.core.ui.kit.snackbar.resolveSnackbarOutcomeOrRequeue
import io.github.stslex.workeeper.core.ui.kit.snackbar.toastTimeoutMillis
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.screenSavedStateConfiguration
import io.github.stslex.workeeper.feature.app_dialogs.impl.ui.AppDialogHost
import io.github.stslex.workeeper.host.AppNavigationHost
import io.github.stslex.workeeper.host.BottomBarNavigationListener.Companion.rememberBottomBarNavigationListener
import io.github.stslex.workeeper.navigation.NavigatorExt.NavigationEventBusSetup
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The generation shell: the whole app body composes in a region keyed and saveable-scoped on the
 * generation id, under the generation's ViewModelStoreOwner. See the Phase-5 startup spec §8.7.
 */
@Composable
fun App() {
    val context = LocalContext.current
    val generationsHolder = remember(context) {
        context.applicationContext as AppUiGenerationsHolder
    }
    val phase by generationsHolder.appUiPhases.collectAsState()
    val saveableStateHolder = rememberSaveableStateHolder()
    // Survives recreation until the successor can drop the old generation's slot.
    var previousGenerationId by rememberSaveable { mutableStateOf<Int?>(null) }

    when (val currentPhase = phase) {
        is AppUiPhase.Generation -> saveableStateHolder.SaveableStateProvider(currentPhase.id) {
            key(currentPhase.id) {
                // Admission gates composition; keyed by phase so an aborted transition reopens it.
                val admission = remember(currentPhase) {
                    GenerationAdmission(generationsHolder, currentPhase.id)
                }
                if (admission.granted) {
                    val deps = remember(currentPhase.id) {
                        (context.applicationContext as AppRootDepsHolder).appRootDeps()
                    }
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides currentPhase.viewModelStoreOwner,
                    ) {
                        LaunchedEffect(currentPhase.id) {
                            // A completed transition (new id) drops the old saved slot; an
                            // aborted one (same id) keeps it and restores.
                            previousGenerationId
                                ?.takeIf { it != currentPhase.id }
                                ?.let(saveableStateHolder::removeState)
                            previousGenerationId = currentPhase.id
                        }
                        AppGenerationContent(deps)
                    }
                }
            }
        }

        AppUiPhase.Transitioning -> Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("AppTransitioning"),
        )
    }
}

/**
 * The generation region's admission grant, held as long as the region is remembered.
 * [RememberObserver] releases it on both `onForgotten` and `onAbandoned`.
 */
private class GenerationAdmission(
    private val holder: AppUiGenerationsHolder,
    generationId: Int,
) : RememberObserver {

    private val token: AppUiAdmissionToken? = holder.admitUiGeneration(generationId)

    val granted: Boolean = token != null

    override fun onRemembered() = Unit

    override fun onForgotten() {
        token?.let(holder::releaseUiGeneration)
    }

    override fun onAbandoned() {
        token?.let(holder::releaseUiGeneration)
    }
}

@Composable
@Suppress("LongMethod")
private fun AppGenerationContent(deps: AppRootDeps) {
    // AppRootViewModel reads deps through [AppRootDeps]; the app graph is below-the-line here and
    // cannot be named. The admitted generation resolves and passes this exact instance once.
    val viewModel: AppRootViewModel = viewModel {
        AppRootViewModel(
            commonDataStore = deps.commonDataStore,
            navigatorEventBus = deps.navigatorEventBus,
        )
    }
    val themeMode by viewModel.themeMode.collectAsState()

    AppTheme(themeMode = themeMode) {
        // App-owned back stack. The common overload with an explicit SavedStateConfiguration is
        // what survives process death and keeps phase 7 a dependency swap.
        val backStack = rememberNavBackStack(
            screenSavedStateConfiguration,
            Screen.BottomBar.Home,
        )
        val holder = remember(backStack) { NavigatorHolder(backStack) }
        val navigatorEventBus = viewModel.navigatorEventBus

        val bottomBarNavigationListener = rememberBottomBarNavigationListener(holder)
        val hapticFeedback = LocalHapticFeedback.current

        NavigationEventBusSetup(
            navigatorHolder = holder,
            navigator = navigatorEventBus,
            results = navigatorEventBus,
        )

        val snackbarHostState = remember { SnackbarHostState() }

        // The host owns the toast lifetime: `showSnackbar` defaults to `Indefinite` when an
        // actionLabel is present, so it is shown Indefinite and cancelled by `withTimeoutOrNull`.
        val accessibilityManager = LocalAccessibilityManager.current
        LaunchedEffect(accessibilityManager) {
            SnackbarManager.snackbar
                .collect { delivered ->
                    // ActionPerformed → action; dismiss or timeout → onDismissed, routed through
                    // the kit's selector so a model this collector dies holding is requeued.
                    val model = delivered.model
                    resolveSnackbarOutcomeOrRequeue(delivered) {
                        withTimeoutOrNull(
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
                    }
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
                        // §26 "Haptics": SegmentTick on a nav tab change, fired here because
                        // every haptic is fired at feature/graph level, never inside the kit.
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        navigatorEventBus.navTo(item.screen)
                    },
                    modifier = Modifier.testTag("WorkeeperBottomAppBar"),
                )
            }

            AppNavigationHost(
                modifier = Modifier,
                navigatorHolder = holder,
                results = navigatorEventBus,
                imageViewerGraphFactory = deps.imageViewerGraphFactory,
                planEditorGraphFactory = deps.planEditorGraphFactory,
            )

            // GUARD: no host-owned affordance here — the host may not place a control in a band
            // a screen owns and replaces whole. See v3-redesign-spec.md §26.

            SnackbarHost(
                modifier = Modifier.align(Alignment.BottomCenter),
                hostState = snackbarHostState,
            ) { data ->
                AppSnackbar(snackbarData = data)
            }

            // Sibling of AppNavigationHost so the dialog shows on any route; its state lives in
            // DataStore. See documentation/feature-specs/app-dialogs.md → "AppDialogHost mounting".
            AppDialogHost()
        }
    }
}
