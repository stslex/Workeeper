// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the screen-less [AppFeature] composition entry resolves its Store at the
 * host `ComponentActivity`'s `ViewModelStore` — same lifetime as the Activity, not a
 * `NavBackStackEntry`, not a `@Singleton`. This is the scope invariant the App-root
 * mount site depends on (see KDoc on [AppFeature] and
 * `documentation/feature-specs/app-dialogs.md` → "AppDialogHost mounting").
 *
 * App-Scope Collapse Step 6 (Phase 3.4): de-Hilt'd. The probe ([AppRootProbeFeature] /
 * [AppRootProbeStoreImpl] in `AppFeatureProbe.kt`) resolves its Store through the SAME Metro path every
 * production `AppFeature` now uses — `rememberMetroStoreProcessor`, which retains the Store via
 * `viewModel<T>()` in the current `LocalViewModelStoreOwner`. That is the exact retention mechanic under
 * test: at App-root depth `LocalViewModelStoreOwner` must be the host Activity, so the Store lands in the
 * Activity's `ViewModelStore` and a subsequent `ViewModelProvider(activity)` read returns the SAME
 * instance (proof the cache key is the Activity, not a `NavBackStackEntry`).
 *
 * `@Smoke` by the taxonomy: `TestActivity` with directly-constructed deps — no `MetroTestRule`,
 * no `MainActivity`, no database (`documentation/testing.md` → "Categorization with `@Smoke` and
 * `@Regression`"). The class-level annotation is load-bearing, not decoration: a `@Test` carrying
 * neither `@Smoke` nor `@Regression` in a module that DOES resolve the annotation class is
 * selected by neither suite, and both runs stay green because a selector that matches nothing
 * reports nothing. `detektAndroidTestSuite` gates it
 * (`documentation/feature-specs/kmp-phase-0-instrumented-filter.md` → "The gate").
 */
@Smoke
@RunWith(AndroidJUnit4::class)
internal class AppFeatureScopeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun appFeatureProcessorResolvesAtActivityScope() {
        var capturedOwner: ViewModelStoreOwner? = null
        var capturedLifecycle: LifecycleOwner? = null
        var capturedStore: AppRootProbeStoreImpl? = null

        composeRule.setContent {
            // Composed as a sibling of any NavHost would be — i.e. directly at the
            // App root depth — so LocalViewModelStoreOwner.current must resolve to
            // the host ComponentActivity, not a NavBackStackEntry.
            AppRootProbeFeature.processor()

            // The instance the line above retained, read back through the SAME androidx path
            // `rememberMetroStoreProcessor` uses — `viewModel<T>()` with no explicit key against the
            // current LocalViewModelStoreOwner (the de-Hilt'd equivalent of the old `hiltViewModel()`).
            // It returns the already-cached entry, so no factory is invoked here.
            val directStore: AppRootProbeStoreImpl = viewModel()

            // LifecycleOwner and ViewModelStoreOwner snapshots from the same
            // composition that AppFeature is composed in: BaseStore.init was called
            // with whatever rememberLifecycleOwner() (== LocalLifecycleOwner.current)
            // returned, which must be the Activity at this depth.
            val owner = LocalViewModelStoreOwner.current
            val lifecycleOwner = LocalLifecycleOwner.current

            SideEffect {
                capturedOwner = owner
                capturedLifecycle = lifecycleOwner
                capturedStore = directStore
            }
        }

        composeRule.waitForIdle()

        val activity = composeRule.activity
        val owner = checkNotNull(capturedOwner) { "LocalViewModelStoreOwner not resolved" }
        val lifecycle = checkNotNull(capturedLifecycle) { "LifecycleOwner not resolved" }
        val directStore = checkNotNull(capturedStore) { "Probe Store not resolved in composition" }

        // (1) Activity owns the ViewModelStore the Store was scoped to.
        assertSame(
            "AppFeature must resolve to the host Activity's ViewModelStore",
            activity.viewModelStore,
            owner.viewModelStore,
        )

        // (2) The instance the processor retained IS the entry in the Activity's ViewModelStore:
        // fetching AppRootProbeStoreImpl through the Activity's own ViewModelProvider returns the
        // cached entry (===), not a second construction. If AppFeature had silently rescoped the Store
        // to any other owner, the Activity's store would hold no such entry and this read would either
        // fail to construct one (no default no-arg factory) or hand back a DIFFERENT instance — the
        // identity assertion is what catches the latter.
        val viaActivity = ViewModelProvider(activity)[AppRootProbeStoreImpl::class.java]
        assertSame(
            "Store fetched from the Activity must be the same instance AppFeature wired up",
            directStore,
            viaActivity,
        )

        // (3) BaseStore.init(LifecycleOwner) received the Activity lifecycle.
        // rememberStoreProcessor passes rememberLifecycleOwner() (i.e.
        // LocalLifecycleOwner.current) into BaseStore.init, and at App-root depth
        // that owner must be the Activity itself.
        assertSame(
            "BaseStore.init must receive the host Activity as LifecycleOwner",
            activity,
            lifecycle,
        )
    }
}
