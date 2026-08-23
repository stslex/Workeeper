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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    private val probeDeps = ProbeGenerationDeps()

    @Test
    fun appFeatureProcessorResolvesAtActivityScope() {
        var capturedOwner: ViewModelStoreOwner? = null
        var capturedLifecycle: LifecycleOwner? = null
        var capturedStore: AppRootProbeStoreImpl? = null

        composeRule.setContent {
            ProbeAppDepsHost(probeDeps) {
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

    /**
     * Phase 5 R3, blocker 4 — the SEAM, on-device, through the production path.
     *
     * `StoreGenerationJoinTest` (host) passes a generation job to `BaseStore.init` by hand, so it
     * proves `AppCoroutineScopeImpl`'s parenting but not that anything supplies the job. This
     * composes the real `rememberMetroStoreProcessor` under a real `appDeps` holder and then ends
     * the generation lifetime the holder handed out: a Store job started through the ordinary
     * `launchDefault` surface must be a DESCENDANT, so `cancelAndJoin` cannot return until that
     * job's `finally` has run. That is the property the runtime relies on when it closes a
     * generation's database immediately after joining its lifetime.
     */
    @Test
    fun storeJobsAreDescendantsOfTheGenerationSuppliedByTheDepsSeam() {
        var capturedStore: AppRootProbeStoreImpl? = null

        composeRule.setContent {
            ProbeAppDepsHost(probeDeps) {
                AppRootProbeFeature.processor()
                val directStore: AppRootProbeStoreImpl = viewModel()
                SideEffect { capturedStore = directStore }
            }
        }

        composeRule.waitForIdle()
        val store = checkNotNull(capturedStore) { "Probe Store not resolved in composition" }

        val started = CountDownLatch(1)
        val finallyRan = AtomicBoolean(false)
        store.launchDefault(
            onError = {},
            onSuccess = {},
            action = {
                try {
                    started.countDown()
                    awaitCancellation()
                } finally {
                    // Stands in for the real thing: a Store job whose cleanup touches the
                    // generation's database. It MUST complete before the runtime closes it.
                    finallyRan.set(true)
                }
            },
        )
        check(started.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "probe job never started" }

        runBlocking { probeDeps.appScopeLifetime.cancelAndJoin() }

        assertTrue(
            "cancelAndJoin on the generation lifetime returned while a Store job's finally had " +
                "not run — the Store's jobs are not parented to the generation the deps seam " +
                "supplied, so the runtime would close the database under them",
            finallyRan.get(),
        )
    }

    private companion object {
        const val WAIT_SECONDS = 5L
    }
}
