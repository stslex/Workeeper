// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.stslex.workeeper.core.ui.test.TestActivity
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
 * The test does NOT touch the dialog catalog, recovery, or any production Store —
 * it uses [AppRootProbeFeature] / [AppRootProbeStoreImpl] declared in
 * `AppFeatureProbe.kt`. The probe's `@ViewModelScoped` `HandlerStore` + `Handler`
 * also have to bind on the Store's `ViewModelComponent`, so successful composition
 * of `AppRootProbeFeature.processor()` doubles as proof that the binding works.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
internal class AppFeatureScopeTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
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

            // Re-resolve the same @HiltViewModel from the same composition context.
            // hiltViewModel<T>() reads LocalViewModelStoreOwner.current, so this
            // returns the SAME instance as what AppFeature wired up — only if the
            // owner resolution agrees.
            val directStore: AppRootProbeStoreImpl = hiltViewModel()

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
        val storeFromComposition = checkNotNull(capturedStore) { "Store not captured" }

        // (1) Activity owns the ViewModelStore the Store was scoped to.
        assertSame(
            "AppFeature must resolve to the host Activity's ViewModelStore",
            activity.viewModelStore,
            owner.viewModelStore,
        )

        // (2) Fetching the same VM class via the Activity's ViewModelProvider returns
        // the same instance — proof that the cache key is the Activity, not a
        // NavBackStackEntry. If the AppFeature had silently rescoped to any other
        // owner, ViewModelProvider(activity) would construct a SECOND instance.
        val viaActivity = ViewModelProvider(activity).get(AppRootProbeStoreImpl::class.java)
        assertSame(
            "Store fetched from the Activity must be the same instance AppFeature wired up",
            storeFromComposition,
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
