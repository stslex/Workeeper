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
 * The generation shell (Phase 5, `kmp-phase-5-startup-processor.md` §8.7). The WHOLE app body —
 * including the [AppRootViewModel] resolution, which ctor-captures the generation's
 * `navigatorEventBus` — composes inside a region that is:
 *
 *  - **keyed on the generation id**: a new generation drops every positional state slot, so the
 *    Nav3 back stack restarts at Home and nothing remembered under generation N can leak into
 *    N+1 (Back cannot reach the old stack — the list object itself is gone);
 *  - **saveable-scoped per generation** ([rememberSaveableStateHolder]): generation N's saved
 *    state lives only under slot N — an ABORTED transition re-enters slot N and restores the old
 *    back stack intact, while a completed one removes the old slot so old entries can never
 *    resurrect. Cold start always composes slot 1, so ordinary Activity-recreation and
 *    process-death restoration are byte-identical to the pre-Phase-5 tree (pinned by
 *    `BackStackStateRestorationTest`);
 *  - **ViewModel-scoped to the generation**: the runtime-owned [AppUiPhase.Generation
 *    .viewModelStoreOwner] is provided as the root `LocalViewModelStoreOwner`, so
 *    [AppRootViewModel] and the app-dialog Store survive Activity recreation (the runtime
 *    outlives the Activity) yet die deterministically when the runtime clears the generation's
 *    store. NavDisplay's per-entry decorator re-provides the entry owner underneath, so
 *    per-screen Stores are untouched by this.
 *
 * The [DisposableEffect] is the runtime's Quiescing signal: it is the FIRST thing remembered in
 * the region, so Compose forgets it LAST — its `onDispose` fires only after every inner Store's
 * own dispose ran, which is exactly the "the UI let go of generation N" moment the runtime
 * awaits before touching the generation's ViewModelStore.
 *
 * [AppUiPhase.Transitioning] composes a bare neutral box (deliberately theme-independent: the
 * theme flows from the generation's own [AppRootViewModel], which does not exist in this window).
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
                // Admission must gate composition before content resolves generation dependencies.
                // Key by phase instance so an aborted transition can reopen the same id.
                val admission = remember(currentPhase) {
                    GenerationAdmission(generationsHolder, currentPhase.id)
                }
                if (admission.granted) {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides currentPhase.viewModelStoreOwner,
                    ) {
                        LaunchedEffect(currentPhase.id) {
                            // A COMPLETED transition (new id) drops the old generation's saved
                            // slot — no resurrection; an aborted one (same id) keeps it and
                            // restores.
                            previousGenerationId
                                ?.takeIf { it != currentPhase.id }
                                ?.let(saveableStateHolder::removeState)
                            previousGenerationId = currentPhase.id
                        }
                        AppGenerationContent()
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
 * The generation region's admission grant, held for exactly as long as the region is remembered.
 * [RememberObserver] is what makes it leak-free in BOTH directions Compose allows: a composition
 * that is applied releases through `onForgotten`, and one that is ABANDONED (composed but never
 * applied — the window a retirement can land in) releases through `onAbandoned`.
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
private fun AppGenerationContent() {
    // AppRootViewModel is a plain ViewModel constructed via viewModel {} with deps read from the
    // app graph — through [AppRootDeps], never the graph itself. `@DependencyGraph(AppScope)` and
    // `AppGraphOwner` are internal to `:app:app`, which depends on THIS module, so the graph is
    // below-the-line here by construction and cannot be named. `AppGraph` implements the contract;
    // the cast is safe because `BaseApplication : AppRootDepsHolder` is compile-visible there. Same
    // typed-point-acquisition shape as RecoveryDepsHolder / BackupWorkerDepsHolder. The resolution
    // happens INSIDE the generation region (see [App]'s KDoc), so the holder read returns the
    // CURRENT generation's deps and the ViewModel lands in the generation's store.
    val context = LocalContext.current
    val viewModel: AppRootViewModel = viewModel {
        val deps = (context.applicationContext as AppRootDepsHolder).appRootDeps()
        AppRootViewModel(
            commonDataStore = deps.commonDataStore,
            navigatorEventBus = deps.navigatorEventBus,
        )
    }
    val themeMode by viewModel.themeMode.collectAsState()

    AppTheme(themeMode = themeMode) {
        // The app-owned back stack. The COMMON rememberNavBackStack overload, always: the
        // explicit SavedStateConfiguration is what survives process death (see
        // screenSavedStateConfiguration's KDoc), and the reflection overload does not exist
        // off Android — using the config overload here is what keeps phase 7 a dependency
        // swap instead of a rewrite.
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
                .collect { delivered ->
                    // ActionPerformed → action; Dismissed or timeout → onDismissed. The
                    // routing is the kit's own named function so the deferred-delete window
                    // (ED11) is asserted at its selector, not read off this collector — the
                    // callbacks' failures are contained there, and a model this collector
                    // dies holding (the activity recreates under a visible toast) goes back
                    // on the queue WITH ITS ORIGINAL GENERATION EPOCH for the collector that
                    // replaces it.
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
                results = navigatorEventBus,
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
